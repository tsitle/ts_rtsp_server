package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for Message Queue subscribers.
 */
public abstract class MqReceiverSubBase implements AutoCloseable {

	private static class Stats {
		@Nullable Long lastTimestampMs = null;
		long lastRecvTimeNs = 0L;
		@Nullable Long lastMsgNr = null;
		long avgTsDeltaSum = 0L;
		int avgTsDeltaCnt = 0;
		long avgRecvDeltaSum = 0L;
		int avgRecvDeltaCnt = 0;
		long avgTsVsRecvDeltaSum = 0L;
		int avgTsVsRecvDeltaCnt = 0;
		@Nullable Instant lastFpsMeasureTime;
		int framesOutputtedCount = 0;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int TIMEOUT_WAIT_FOR_SOCKET_READABLE_MS = 2500;

	private final @Nullable LogMsgInterface logMsgInterface;
	private final boolean doValidatePayload;
	private final boolean doPrintDebugStats;

	protected final @NonNull ZContext zmqContext;
	protected ZMQ.@Nullable Socket zmqSocket;
	protected ZMQ.@Nullable Poller zmqPollerObj;
	protected int zmqPollerIxRead = 0;
	private boolean zmqContextOpened;

	protected final AtomicBoolean stateClosed = new AtomicBoolean(false);
	protected final AtomicBoolean stateOpened = new AtomicBoolean(false);

	protected @Nullable MqMsgHandlerBase msgHandler;

	private final Stats stats = new Stats();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param doValidatePayload Validate payload data?
	 * @param doPrintDebugStats Print debug statistics?
	 */
	protected MqReceiverSubBase(
				@Nullable LogMsgInterface logMsgInterface,
				boolean doValidatePayload,
				boolean doPrintDebugStats
			) {
		this.logMsgInterface = logMsgInterface;
		this.doValidatePayload = doValidatePayload;
		this.doPrintDebugStats = doPrintDebugStats;

		//
		this.zmqContext = MqContextHelper.openMqContext();
		this.zmqContextOpened = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Connect to the Message Queue.
	 * @throws MqException If an error has occurred
	 */
	public abstract void connectToMq() throws MqException;

	/**
	 * Receive a message containing audio/video data from the Message Queue.
	 * @param payloadData The payload data buffer
	 * @return Received message or empty if no message was received
	 * @throws MqException If an error has occurred
	 */
	public Optional<MqPacketAv> receiveMessageAv(@NonNull BufferExt payloadData) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveMessageAv()";

		//
		if (msgHandler == null) {
			throw new IllegalStateException(FNC_NAME + ": Message handler not set");
		}
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
		if (! waitForSocketReadyToRead(FNC_NAME)) {
			return Optional.empty();
		}

		//
		Optional<MqPacketAv> optPacket = msgHandler.readMsgAvFromMq(payloadData);
		if (optPacket.isEmpty()) {
			return Optional.empty();
		}
		final MqPacketAv packet = optPacket.get();
		//if (! packet.codec().isVideo()) { logDebug(FNC_NAME, "Received MQ " + packet); }
		//logDebug(FNC_NAME, "Received MQ Packet " + (packet.codec().isVideo() ? "VID" : "AUD"));

		// validate the payload data (if the checksum is 0x00, we assume that the sender did not compute it)
		if (doValidatePayload && packet.mdPayloadCRC8() != 0x00) {
			msgHandler.validateCRC8(packet.mdPayloadCRC8(), payloadData);
		}

		//
		if (doPrintDebugStats) {
			printDebugStats(FNC_NAME, packet.mdTimestamp());
		}
		stats.lastTimestampMs = packet.mdTimestamp();
		stats.lastRecvTimeNs = System.nanoTime();

		//
		if (stats.lastMsgNr != null) {
			final long tmpDelta = packet.msgNr() - stats.lastMsgNr;
			if (tmpDelta != 1L) {
				logWarn(FNC_NAME, "MQ packet.msgNr delta " + Long.toUnsignedString(tmpDelta));
			}
		}
		stats.lastMsgNr = packet.msgNr();

		return optPacket;
	}

	/**
	 * Close the Message Queue.
	 */
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

	/**
	 * Has the Message Queue been closed?
	 * @return True if the Message Queue has been closed, false otherwise
	 */
	public boolean isClosed() {
		return stateClosed.get();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void closeZmqContextInstance() {
		if (zmqContextOpened) {
			MqContextHelper.closeMqContext();
			zmqContextOpened = false;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean waitForSocketReadyToRead(@NonNull String fncName) throws MqException {
		int timeoutCnt = 0;
		while (! Thread.currentThread().isInterrupted() && ! stateClosed.get() && zmqPollerObj != null) {
			int tmpResI = zmqPollerObj.poll(5);
			if (tmpResI > 0) {
				// check that the zmqPollerObj hasn't been deleted since poll() was called
				if (zmqPollerObj != null && zmqPollerObj.pollin(zmqPollerIxRead)) {
					return true;
				}
			}
			if (++timeoutCnt > TIMEOUT_WAIT_FOR_SOCKET_READABLE_MS / 5) {
				throw new MqException(fncName + ": Timeout waiting for socket (rd)");
			}
		}
		return false;
	}

	private void printDebugStats(@NonNull String fncName, long curMdTimestampMs) {
		if (stats.lastTimestampMs != null) {
			final long tmpTimestampDeltaMs = curMdTimestampMs - stats.lastTimestampMs;
			final long tmpRecvDeltaMs = (System.nanoTime() - stats.lastRecvTimeNs) / 1_000_000L;

			stats.avgTsDeltaSum += tmpTimestampDeltaMs;
			++stats.avgTsDeltaCnt;
			stats.avgRecvDeltaSum += tmpRecvDeltaMs;
			++stats.avgRecvDeltaCnt;

			stats.avgTsVsRecvDeltaSum += (tmpRecvDeltaMs - tmpTimestampDeltaMs);
			++stats.avgTsVsRecvDeltaCnt;
		}

		//
		if (stats.avgTsVsRecvDeltaCnt == 250) {
			final long tmpAvgTsDelta = stats.avgTsDeltaSum / stats.avgTsDeltaCnt;
			final long tmpAvgRecvDelta = stats.avgRecvDeltaSum / stats.avgRecvDeltaCnt;
			final long tmpAvgTsVsRecvDelta = stats.avgTsVsRecvDeltaSum / stats.avgTsVsRecvDeltaCnt;
			logDebug(fncName, "MQ avg tsDelta=" + tmpAvgTsDelta + " | rcvDelta=" + tmpAvgRecvDelta +
					" | tsVsRcvDelta=" + tmpAvgTsVsRecvDelta + " ms");
			stats.avgTsDeltaSum = tmpAvgTsDelta;
			stats.avgTsDeltaCnt = 1;
			stats.avgRecvDeltaSum = tmpAvgRecvDelta;
			stats.avgRecvDeltaCnt = 1;
			stats.avgTsVsRecvDeltaSum = tmpAvgTsVsRecvDelta;
			stats.avgTsVsRecvDeltaCnt = 1;
		}

		++stats.framesOutputtedCount;
		if (stats.lastFpsMeasureTime != null) {
			Duration tmpDurLfmt = Duration.between(stats.lastFpsMeasureTime, Instant.now());
			long tmpMs = tmpDurLfmt.toMillis();
			if (tmpMs >= 5_000L) {
				logDebug(fncName, String.format(
						"MQ fps: %.1f", (((double)stats.framesOutputtedCount / (double)tmpMs) * 1_000.0)));
				stats.framesOutputtedCount = 0;
				stats.lastFpsMeasureTime = Instant.now();
			}
		} else {
			stats.lastFpsMeasureTime = Instant.now();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.DEBUG, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	protected void logWarn(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.WARN, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	protected void logError(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.ERROR, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
