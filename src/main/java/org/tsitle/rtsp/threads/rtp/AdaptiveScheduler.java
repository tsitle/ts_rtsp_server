package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;

import java.util.concurrent.locks.LockSupport;

public final class AdaptiveScheduler {

	@SuppressWarnings("FieldCanBeLocal")
	private final LogMsgInterface logMsgInterface;

	private final double targetIntervalNs;
	@SuppressWarnings("FieldCanBeLocal")
	private long lastFrameTimeNs = 0;
	private long nextFrameTimeNs = 0;
	private double errorPerFrameNs = 0.0;
	private double cumulativeErrorNs = 0.0;

	private long curFrameNr = 0;
	@SuppressWarnings("unused")
	private long sleepCounter = 0;

	public AdaptiveScheduler(
				@NonNull LogMsgInterface logMsgInterface,
				double fps
			) {
		this.logMsgInterface = logMsgInterface;

		this.targetIntervalNs = 1_000_000_000.0 / fps;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void waitForNextFrame() {
		if (curFrameNr == 0) {
			lastFrameTimeNs = System.nanoTime();
			nextFrameTimeNs = (lastFrameTimeNs + (long)targetIntervalNs);
			// if errorPerFrameNs is positive, we are behind, otherwise we are ahead
			errorPerFrameNs = ((double)nextFrameTimeNs - ((double)lastFrameTimeNs + targetIntervalNs));
		} else {
			long currentTimeNs = System.nanoTime();
			//double timeSinceLastWfnfNs = (currentTimeNs - lastFrameTimeNs);
			long tmpCumErrNs = (cumulativeErrorNs > 100_000.0 ? 100_000 : (cumulativeErrorNs < 100_000.0 ? -100_000 : 0));
			long remainingTimeToSleepNs = nextFrameTimeNs - currentTimeNs - tmpCumErrNs;
			cumulativeErrorNs -= (double)tmpCumErrNs;

			sleepUntilNanos(currentTimeNs + remainingTimeToSleepNs);

			// if currentErrorNs is positive, we are behind, otherwise we are ahead
			long currentErrorNs = (System.nanoTime() - nextFrameTimeNs);
			cumulativeErrorNs += errorPerFrameNs + currentErrorNs;

			nextFrameTimeNs += (long)targetIntervalNs;
		}

		lastFrameTimeNs = System.nanoTime();
		++curFrameNr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Sleeps until the specified target time (in nanoseconds since epoch) is reached.
	 * Uses a hybrid approach: Thread.sleep() for coarse waiting, then busy-waiting
	 * for high precision without overshooting.
	 * @param targetTimeNanos The target time in nanoseconds (System.nanoTime() format)
	 * @throws IllegalArgumentException if targetTimeNanos is in the past
	 */
	public void sleepUntilNanos(long targetTimeNanos) {
		long currentTime = System.nanoTime();

		if (targetTimeNanos <= currentTime) {
			/*logDebug("sleepUntilNanos()", "Target time is in the past or current time (" +
						(((double)currentTime - targetTimeNanos) / 1_000.0) +
						"us, r=" + curFrameNr +
						", s=" + sleepCounter + ")");*/  // @TODO
			return;
		}

		long remainingNanos = targetTimeNanos - currentTime;

		// Phase 1: Coarse waiting using Thread.sleep() for milliseconds
		// Leave a buffer to avoid overshooting
		long sleepBufferNanos = 2_000_000; // 2ms buffer

		if (remainingNanos > sleepBufferNanos) {
			long sleepMillis = (remainingNanos - sleepBufferNanos) / 1_000_000;
			if (sleepMillis > 0) {
				try {
					Thread.sleep(sleepMillis);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}

		// Phase 2: Medium precision using LockSupport.parkNanos()
		// For microsecond-level precision
		currentTime = System.nanoTime();
		remainingNanos = targetTimeNanos - currentTime;
		final long parkBufferNanos = 100_000; // 100µs buffer

		if (remainingNanos > parkBufferNanos) {
			LockSupport.parkNanos(remainingNanos - parkBufferNanos);
		}

		// Phase 3: High precision busy-waiting for the final nanoseconds
		while (System.nanoTime() < targetTimeNanos) {
			// Busy wait - yields CPU but maintains high precision
			Thread.onSpinWait(); // JDK 9+ hint for busy waiting
		}

		++sleepCounter;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/*private void logDebug(@NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(
				RtxpLogLevel.DEBUG,
				Thread.currentThread().getName(),
				fncName + ": " + msg
			);
	}*/

}
