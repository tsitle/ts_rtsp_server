package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MqReceiverSubBase implements AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final boolean doValidatePayload;
	private final boolean doPrintDebugStats;

	protected final @NonNull ZContext zmqContext;
	protected ZMQ.@Nullable Socket zmqSocket;
	protected ZMQ.@Nullable Poller zmqPollerObj;
	protected int zmqPollerIx = 0;
	private boolean zmqContextOpened;

	protected final AtomicBoolean stateClosed = new AtomicBoolean(false);
	protected final AtomicBoolean stateOpened = new AtomicBoolean(false);

	protected @Nullable MqMsgHandlerBase msgHandler;

	private @Nullable Long lastTimestampMs = null;
	private long lastRecvTimeNs = 0L;
	private @Nullable Integer lastCounter = null;
	private long avgTsDeltaSum = 0L;
	private int avgTsDeltaCnt = 0;
	private long avgRecvDeltaSum = 0L;
	private int avgRecvDeltaCnt = 0;
	private long avgTsVsRecvDeltaSum = 0L;
	private int avgTsVsRecvDeltaCnt = 0;
	private @Nullable Instant lastFpsMeasureTime;
	private int framesOutputtedCount = 0;

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

	public abstract void connectToMq() throws MqException;

	public Optional<MqPacketAv> receiveMessage(@NonNull BufferExt payloadData) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveMessage()";

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

		//
		Optional<MqPacketAv> optPacket = msgHandler.readMsgFromMq(payloadData);
		if (optPacket.isEmpty()) {
			return Optional.empty();
		}
		Instant tmpNow5 = Instant.now();

		// validate the payload data
		//logDebug(FNC_NAME, "Received MQ " + optPacket.get());
		//logDebug(FNC_NAME, "Received MQ Packet " + (optPacket.get().codec().isVideo() ? "VID" : "AUD"));
		if (doValidatePayload) {
			msgHandler.validatePacketPayloadCRC(optPacket.get().mdPayloadCRC8(), payloadData);
		}

		Instant tmpNow6 = Instant.now();

		if (doPrintDebugStats) {
			final long tmpTimestampDeltaMs;
			final long tmpRecvDeltaMs;
			if (lastTimestampMs != null) {
				tmpTimestampDeltaMs = optPacket.get().mdTimestamp() - lastTimestampMs;
				tmpRecvDeltaMs = (System.nanoTime() - lastRecvTimeNs) / 1_000_000L;

				avgTsDeltaSum += tmpTimestampDeltaMs;
				++avgTsDeltaCnt;
				avgRecvDeltaSum += tmpRecvDeltaMs;
				++avgRecvDeltaCnt;

				avgTsVsRecvDeltaSum += (tmpRecvDeltaMs - tmpTimestampDeltaMs);
				++avgTsVsRecvDeltaCnt;
			} else {
				tmpTimestampDeltaMs = 0L;
				tmpRecvDeltaMs = 0L;
			}

			//
			Duration tmpDur12 = Duration.between(tmpNow1, tmpNow2);  // @TODO
			Duration tmpDur56 = Duration.between(tmpNow5, tmpNow6);
			Duration tmpDur16 = Duration.between(tmpNow1, tmpNow6);
			if (tmpDur16.toMillis() > 170) {
				logDebug(FNC_NAME, "MQ read " + (optPacket.get().codec().isVideo() ? "VID" : "AUD") + " time: " +
						"A=" + (tmpDur12.toNanos() / 1_000L) + " us, " +
						"B=" + (doValidatePayload ? "" + (tmpDur56.toNanos() / 1_000L) : "--") + " us // " +
						"tot=" + (tmpDur16.toNanos() / 1_000_000L) + " ms, " +
						"tsDelta=" + tmpTimestampDeltaMs + " ms, rcvDelta=" + tmpRecvDeltaMs + " ms");  // @TODO
			}

			if (avgTsVsRecvDeltaCnt == 250) {
				final long tmpAvgTsDelta = avgTsDeltaSum / avgTsDeltaCnt;
				final long tmpAvgRecvDelta = avgRecvDeltaSum / avgRecvDeltaCnt;
				final long tmpAvgTsVsRecvDelta = avgTsVsRecvDeltaSum / avgTsVsRecvDeltaCnt;
				logDebug(FNC_NAME, "MQ avg tsDelta=" + tmpAvgTsDelta + " ms, avg rcvDelta=" + tmpAvgRecvDelta + " ms, avg tsVsRcvDelta=" + tmpAvgTsVsRecvDelta + " ms");
				avgTsDeltaSum = tmpAvgTsDelta;
				avgTsDeltaCnt = 1;
				avgRecvDeltaSum = tmpAvgRecvDelta;
				avgRecvDeltaCnt = 1;
				avgTsVsRecvDeltaSum = tmpAvgTsVsRecvDelta;
				avgTsVsRecvDeltaCnt = 1;
			}

			++framesOutputtedCount;
			if (lastFpsMeasureTime != null) {
				Duration tmpDurLfmt = Duration.between(lastFpsMeasureTime, Instant.now());
				long tmpMs = tmpDurLfmt.toMillis();
				if (tmpMs >= 1_000L) {
					logDebug(FNC_NAME, String.format(
							"MQ fps: %f", (((double)framesOutputtedCount / (double)tmpMs) * 1_000.0)));
					framesOutputtedCount = 0;
					lastFpsMeasureTime = Instant.now();
				}
			} else {
				lastFpsMeasureTime = Instant.now();
			}
		}

		//
		lastTimestampMs = optPacket.get().mdTimestamp();
		lastRecvTimeNs = System.nanoTime();

		if (lastCounter != null) {
			final int tmpCounterDelta = optPacket.get().mdCounter() - lastCounter;
			if (tmpCounterDelta != 1) {
				logWarn(FNC_NAME, "MQ counter delta " + tmpCounterDelta);
			}
		}
		lastCounter = optPacket.get().mdCounter();

		return optPacket;
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

	protected void closeZmqContextInstance() {
		if (zmqContextOpened) {
			MqContextHelper.closeMqContext();
			zmqContextOpened = false;
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
