package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.HashCrc8Helper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqInternalSub implements AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final int streamSourceId;

	private final @NonNull ZContext zmqContext;
	private ZMQ.@Nullable Socket zmqSocket;
	private ZMQ.@Nullable Poller zmqPollerObj;
	private int zmqPollerIx = 0;
	private boolean zmqContextOpened;

	private final AtomicBoolean stateClosed = new AtomicBoolean(false);
	private final AtomicBoolean stateOpened = new AtomicBoolean(false);

	private final @NonNull ByteBuffer cacheBufferData;
	private final @NonNull BufferExt cachePayloadData = new BufferExt();

	private final HashCrc8Helper hashCrc8Helper = new HashCrc8Helper();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param streamSourceId Stream source identifier
	 */
	public MqInternalSub(
				@Nullable LogMsgInterface logMsgInterface,
				int streamSourceId
			) {
		this.logMsgInterface = logMsgInterface;
		this.streamSourceId = streamSourceId;

		//
		this.zmqContext = MqContextHelper.openMqContext();
		this.zmqContextOpened = true;

		//
		this.cacheBufferData = ByteBuffer.allocate(1024 * 1024);
		this.cacheBufferData.order(ByteOrder.BIG_ENDIAN);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void connectToMq() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".connectToMq()";

		if (stateClosed.get()) {
			throw new MqException(FNC_NAME + ": Stream had already been closed");
		}
		String chanName = MqChannelBus.buildChannelNameForStreamSourceId(streamSourceId);
		int chanId = MqChannelBus.getChannelId(chanName);
		zmqSocket = MqChannelBus.createSubscriber(chanId, zmqContext);
		//
		zmqPollerObj = zmqContext.createPoller(1);
		zmqPollerIx = zmqPollerObj.register(zmqSocket, ZMQ.Poller.POLLIN);

		stateOpened.set(true);
		logDebug(FNC_NAME, "Connected to MQ channel: " + chanName);
	}

	public Optional<MqPacketAv> receiveMessage() throws MqException {
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

		// wait until we're ready to receive data
		Instant tmpNow1 = Instant.now();
		int timeoutCnt = 0;
		while (zmqPollerObj != null) {
			if (stateClosed.get()) {
				return Optional.empty();
			}
			if (zmqPollerObj != null && zmqPollerObj.poll(1) != 0) {
				if (zmqPollerObj != null && zmqPollerObj.pollin(zmqPollerIx)) {
					break;
				}
			}
			if (++timeoutCnt > 1000) {
				throw new MqException(FNC_NAME + ": Timeout waiting for data");
			}
		}
		if (Thread.currentThread().isInterrupted() || stateClosed.get() || zmqPollerObj == null) {
			return Optional.empty();
		}
		Instant tmpNow2 = Instant.now();

		// receive the message, but not the payload data
		try {
			if (stateClosed.get() || zmqSocket == null) {
				return Optional.empty();
			}
			cacheBufferData.clear();
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recvByteBuffer(cacheBufferData, 0);
			if (tmpRecvRes == -1) {
				throw new MqException(FNC_NAME + ": Socket closed or interrupted");
			}
			if (tmpRecvRes == 0) {
				return Optional.empty();
			}
			cacheBufferData.flip();
			if (stateClosed.get()) {
				return Optional.empty();
			}
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		// decode the message, but not the payload data
		Instant tmpNow3 = Instant.now();
		final MqMessageDecoder.DecodeTuple tmpDecTuple;
		try {
			tmpDecTuple = MqMessageDecoder.decodePacketAvFromInternalMq(cacheBufferData, cachePayloadData);
		} catch (MqException e) {
			throw new MqException(FNC_NAME + ": MqException caught: " + e.getMessage());
		}
		Instant tmpNow4 = Instant.now();

		// receive the payload data
		try {
			cachePayloadData.clear();
			if (! zmqSocket.hasReceiveMore()) {
				throw new MqException(FNC_NAME + ": Missing payload data from socket");
			}
			cachePayloadData.increaseSize(tmpDecTuple.payloadSize);  // prepare buffer size
			// blocks until one message is successfully retrieved, or stops when timeout set by setReceiveTimeOut(int) expires
			int tmpRecvRes = zmqSocket.recv(cachePayloadData.getBufPtr(), 0, tmpDecTuple.payloadSize, 0);
			if (tmpRecvRes != tmpDecTuple.payloadSize) {
				throw new MqException(FNC_NAME + ": Received too few data from socket");
			}
			cachePayloadData.setUsed(tmpRecvRes);
		} catch (Exception e) {
			throw new MqException(FNC_NAME + ": Exception caught: " + e.getMessage());
		}

		// validate the payload data
		try {
			//logDebug(FNC_NAME, "Received int MQ " + tmpDecTuple.packetAv);
			//logDebug(FNC_NAME, "Received int MQ Packet " + (tmpDecTuple.packetAv.codec().isVideo() ? "VID" : "AUD"));
			MqMessageDecoder.validatePacketPayloadCRC(hashCrc8Helper, tmpDecTuple.packetAv.mdPayloadCRC8(), cachePayloadData);
		} catch (MqException e) {
			throw new MqException(FNC_NAME + ": MqException caught: " + e.getMessage());
		}

		Instant tmpNow5 = Instant.now();

		Duration tmpDur12 = Duration.between(tmpNow1, tmpNow2);
		Duration tmpDur23 = Duration.between(tmpNow2, tmpNow3);
		Duration tmpDur34 = Duration.between(tmpNow3, tmpNow4);
		Duration tmpDur45 = Duration.between(tmpNow4, tmpNow5);
		if (tmpDur12.toMillis() + tmpDur23.toMillis() + tmpDur34.toMillis() + tmpDur45.toMillis() > 100) {
			logDebug(FNC_NAME, "int MQ read " + (tmpDecTuple.packetAv.codec().isVideo() ? "VID" : "AUD") + " time: " +
					"A=" + (tmpDur12.toNanos() / 1_000L) + " us, " +
					"B=" + (tmpDur23.toNanos() / 1_000L) + " us, " +
					"C=" + (tmpDur34.toNanos() / 1_000L) + " us, " +
					"D=" + (tmpDur45.toNanos() / 1_000L) + " us");
		}

		return Optional.of(tmpDecTuple.packetAv);
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
