package org.tsitle.rtsp.mq;

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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqExternalSub implements AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull String mqAddrHostAndPort;
	private final @NonNull String mqAddrPath;
	private final @NonNull String mqAddrAuth;

	private final @NonNull ZContext zmqContext;
	private ZMQ.@Nullable Socket zmqSocket;
	private ZMQ.@Nullable Poller zmqPollerObj;
	private int zmqPollerIx = 0;
	private boolean zmqContextOpened;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param mqAddressHostAndPort Message queue host address (IP/hostname and port)
	 * @param mqAddressPath Message queue path
	 * @param mqAddressAuth Message queue authentication (User:Password)
	 */
	public MqExternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String mqAddressHostAndPort,
				@NonNull String mqAddressPath,
				@NonNull String mqAddressAuth
			) {
		this.logMsgInterface = logMsgInterface;
		this.mqAddrHostAndPort = mqAddressHostAndPort;
		this.mqAddrPath = mqAddressPath;
		this.mqAddrAuth = mqAddressAuth;

		//
		this.zmqContext = MqContextHelper.openMqContext();
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

	public Optional<MqPacketAv> receiveMessage(@NonNull BufferExt payloadData) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveMessage()";

		//
		if (! stateOpened.get()) {
			throw new IllegalStateException(FNC_NAME + ": Stream has not been opened");
		}
		if (stateClosed.get()) {
			return Optional.empty();
		}
		if (zmqSocket == null || zmqPollerObj == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ socket not initialized");
		}

		//
		Instant tmpNow1 = Instant.now();
		while (zmqPollerObj != null) {
			if (stateClosed.get()) {
				return Optional.empty();
			}
			if (zmqPollerObj != null && zmqPollerObj.poll(1) != 0) {
				if (zmqPollerObj != null && zmqPollerObj.pollin(zmqPollerIx)) {
					break;
				}
			}
		}
		if (Thread.currentThread().isInterrupted() || stateClosed.get() || zmqPollerObj == null) {
			return Optional.empty();
		}
		Instant tmpNow2 = Instant.now();

		// read all message parts
		List<byte[]> frames = new ArrayList<>();
		try {
			if (stateClosed.get() || zmqSocket == null) {
				return Optional.empty();
			}
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			byte[] frame = zmqSocket.recv(0);
			if (frame == null) {
				throw new MqException(FNC_NAME + ": Socket closed or interrupted");
			}
			frames.add(frame);
			while (! stateClosed.get() && zmqSocket != null && zmqSocket.hasReceiveMore()) {
				frames.add(zmqSocket.recv(0));
				if (frames.getLast() == null) {
					throw new MqException(FNC_NAME + ": Socket closed or interrupted");
				}
			}
			if (stateClosed.get()) {
				return Optional.empty();
			}
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}
		Instant tmpNow3 = Instant.now();
		//
		final MqPacketAv resPack;
		try {
			resPack = MqMessageDecoder.decodePacketAvFromExternalMq(frames, payloadData);
			//logDebug(FNC_NAME, "Received ext MQ " + resPack);
			//logDebug(FNC_NAME, "Received ext MQ Packet " + (resPack.codec().isVideo() ? "VID" : "AUD"));
			//MqMessageDecoder.validatePacketPayloadCRC(hashCrc8Helper, resPack.mdPayloadCRC8(), payloadData);  @TODO
		} catch (MqException e) {
			throw new MqException(FNC_NAME + ": MqException caught: " + e.getMessage());
		}
		Instant tmpNow4 = Instant.now();

		Duration tmpDur12 = Duration.between(tmpNow1, tmpNow2);
		Duration tmpDur23 = Duration.between(tmpNow2, tmpNow3);
		Duration tmpDur34 = Duration.between(tmpNow3, tmpNow4);
		if (tmpDur12.toMillis() + tmpDur23.toMillis() + tmpDur34.toMillis() > 100) {
			logDebug(FNC_NAME, "ext MQ read " + (resPack.codec().isVideo() ? "VID" : "AUD") + " time: " +
					"A=" + (tmpDur12.toNanos() / 1_000L) + " us, " +
					"B=" + (tmpDur23.toNanos() / 1_000L) + " us, " +
					"C=" + (tmpDur34.toNanos() / 1_000L) + " us");
		}

		return Optional.of(resPack);
	}

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			if (stateClosed.compareAndSet(false, true) && zmqSocket != null) {
				if (zmqPollerObj != null) {
					zmqPollerObj.unregister(zmqSocket);
					zmqPollerObj.close();
					zmqPollerObj = null;
				}
				zmqSocket.close();
				zmqSocket = null;
			}
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		}
		closeZmqContextInstance();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void closeZmqContextInstance() {
		if (zmqContextOpened) {
			MqContextHelper.closeMqContext();
			zmqContextOpened = false;
		}
	}

	private void internalConnectToMq(String endpoint) {
		zmqSocket = zmqContext.createSocket(SocketType.SUB);
		zmqSocket.setReceiveTimeOut(10);
		zmqSocket.setReconnectIVL(1000);
		zmqSocket.setReconnectIVLMax(10000);
		zmqSocket.setRcvHWM(30);  // very roughly 30 audio/video packets
		zmqSocket.setLinger(0);
		zmqSocket.subscribe("".getBytes());  // subscribe to all topics

		// connect to publisher
		zmqSocket.connect(endpoint);

		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIx = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);
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
