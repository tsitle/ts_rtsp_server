package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqInputStream extends InputStream {

	private static final int ZMQ_IO_THREAD_COUNT = 1;  // @TODO

	private final @Nullable LogMsgInterface logMsgInterface;

	private ZMQ.@Nullable Socket zmqSocket;
	private ZMQ.@Nullable Poller zmqPoller;

	private final BufferExt bufferData = new BufferExt();
	private int bufferPos = 0;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	private static ZContext zmqContextObj = null;  // one context for all MQs
	private static int zmqContextRefCount = 0;
	private boolean zmqContextOpened;

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	private final @NonNull String mqAddrHost;
	private final @NonNull String mqAddrPath;
	private final @NonNull String mqAddrAuth;

	public MqInputStream(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String mqAddressHost,
				@NonNull String mqAddressPath,
				@NonNull String mqAddressAuth
			) {
		this.logMsgInterface = logMsgInterface;
		this.mqAddrHost = mqAddressHost;
		this.mqAddrPath = mqAddressPath;
		this.mqAddrAuth = mqAddressAuth;

		//
		openZmqContextStat();
		this.zmqContextOpened = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		/*
		 * - send HTTP GET request to the URL in this.inputUriStr, using Basic Auth with the credentials in this.inputUriAuth
		 * - parse the response and store the Message Queues address in this.mqAddress
		 * - connect to the MQ server at this.mqAddress
		 */
		String endpoint = "tcp://localhost:" + (mqAddrPath.contains("r_video") ? "7778" : "7779");  // @TODO
		internalConnectToMq(endpoint);

		stateOpened.set(true);
	}

	@Override
	public int read() throws IOException {
		if (bufferPos >= bufferData.getUsed()) {
			receiveNextMessage();
			if (bufferData.isEmpty()) {
				throw new IOException("End of stream reached");
			}
		}

		return (bufferData.get(bufferPos++) & 0xFF);
	}

	@Override
	public int read(byte[] b, final int offs, final int len) throws IOException {
		Objects.checkFromIndexSize(offs, len, b.length);

		int totalCopied = 0;
		while (totalCopied < len) {
			if (bufferPos >= bufferData.getUsed()) {
				receiveNextMessage();
				if (bufferData.isEmpty()) {
					return -1;
				}
			}

			int remainingInInp = bufferData.getUsed() - bufferPos;
			int toCopy = Math.min(len - totalCopied, remainingInInp);

			bufferData.copyInto(bufferPos, b, offs + totalCopied, toCopy);

			bufferPos += toCopy;
			totalCopied += toCopy;
		}
		return totalCopied;
	}

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (stateClosed.compareAndSet(false, true) && zmqSocket != null) {
				if (zmqPoller != null) {
					zmqPoller.close();
					zmqPoller = null;
				}
				zmqSocket.close();
				zmqSocket = null;
			}
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		}
		closeZmqContextInstance();
		stateOpened.set(false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static synchronized void openZmqContextStat() {
		if (zmqContextObj == null) {
			zmqContextObj = new ZContext(ZMQ_IO_THREAD_COUNT);
		}
		++zmqContextRefCount;
	}

	private synchronized void closeZmqContextInstance() {
		if (zmqContextOpened) {
			closeZmqContextStat();
			zmqContextOpened = false;
		}
	}

	private static synchronized void closeZmqContextStat() {
		if (zmqContextRefCount != 0 && --zmqContextRefCount == 0) {
			/*zmqContextObj.close();
			zmqContextObj = null;*/  //@TODO
		}
	}

	private void internalConnectToMq(String endpoint) {
		zmqSocket = zmqContextObj.createSocket(SocketType.SUB);
		zmqSocket.setReceiveTimeOut(10);
		zmqSocket.setReconnectIVL(1000);
		zmqSocket.setReconnectIVLMax(10000);
		zmqSocket.setRcvHWM(30);  // very roughly 30 audio/video packets
		zmqSocket.subscribe("".getBytes());

		// connect to publisher
		zmqSocket.connect(endpoint);

		//
		zmqPoller = zmqContextObj.createPoller(1);
		zmqPoller.register(zmqSocket, ZMQ.Poller.POLLIN);
	}

	private void ensureOpen(String fncName) throws IOException {
		if (! stateOpened.get() || stateClosed.get()) {
			throw new IOException(fncName + ": Stream not opened or already closed");
		}
	}

	private synchronized void receiveNextMessage() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveNextMessage()";

		bufferPos = 0;
		bufferData.clear();

		//
		ensureOpen(FNC_NAME);
		if (zmqSocket == null || zmqPoller == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ socket not initialized");
		}

		//
		Instant tmpNow1 = Instant.now();
		while (true) {
			if (stateClosed.get() || zmqPoller == null) {
				return;
			}
			if (zmqPoller.poll(1) != 0) {
				if (zmqPoller != null && zmqPoller.pollin(0)) {
					break;
				}
			}
			Thread.onSpinWait();
		}
		if (Thread.currentThread().isInterrupted() || stateClosed.get() || zmqPoller == null) {
			return;
		}
		Instant tmpNow2 = Instant.now();

		// read all message parts
		List<byte[]> frames = new ArrayList<>();
		try {
			if (stateClosed.get() || zmqSocket == null) {
				return;
			}
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			byte[] frame = zmqSocket.recv(0);
			if (frame == null) {
				throw new IOException(FNC_NAME + ": Socket closed or interrupted");
			}
			frames.add(frame);
			while (! stateClosed.get() && zmqSocket != null && zmqSocket.hasReceiveMore()) {
				frames.add(zmqSocket.recv(0));
				if (frames.getLast() == null) {
					throw new IOException(FNC_NAME + ": Socket closed or interrupted");
				}
			}
			if (stateClosed.get()) {
				return;
			}
		} catch (Exception e) {
			throw new IOException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}
		Instant tmpNow3 = Instant.now();
		//
		try {
			final MqMessageDecoder.PacketCodec stcCodec = MqMessageDecoder.decodePacket_onlyCodec(frames);
			if (stcCodec == MqMessageDecoder.PacketCodec.H264 || stcCodec == MqMessageDecoder.PacketCodec.H265) {
				MqMessageDecoder.PacketVideo packet = MqMessageDecoder.decodePacketVideo(frames);
				//logDebug(FNC_NAME, "Received from MQ " + packet);
				logDebug(FNC_NAME, "Received PacketVideo from MQ");
				//MqMessageDecoder.validatePacketPayloadCRC(hashCrc8Helper, packet.mdPayloadCRC8(), packet.dataRub());  @TODO
				bufferData.copyOf(packet.dataRub());
			} else {
				MqMessageDecoder.PacketAudio packet = MqMessageDecoder.decodePacketAudio(frames);
				//logDebug(FNC_NAME, "Received from MQ " + packet);
				//logDebug(FNC_NAME, "Received PacketAudio from MQ");
				//MqMessageDecoder.validatePacketPayloadCRC(hashCrc8Helper, packet.mdPayloadCRC8(), packet.dataRub());  @TODO
				bufferData.copyOf(packet.dataRub());
			}
		} catch (MqException e) {
			throw new IOException(FNC_NAME + ": MqException caught: " + e.getMessage());
		}
		Instant tmpNow4 = Instant.now();

		/*Duration tmpDur12 = Duration.between(tmpNow1, tmpNow2);
		Duration tmpDur23 = Duration.between(tmpNow2, tmpNow3);
		Duration tmpDur34 = Duration.between(tmpNow3, tmpNow4);
		logDebug(FNC_NAME, "MQ read time: A=" + (tmpDur12.toNanos() / 1_000L) + " us, " +
				"B=" + (tmpDur23.toNanos() / 1_000L) + " us, " +
				"C=" + (tmpDur34.toNanos() / 1_000L) + " us");*/
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.DEBUG, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.ERROR, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
