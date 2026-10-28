package org.tsitle.rtsp_server.threads;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.SleepHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

public final class AdaptiveScheduler {

	private final @NonNull LogMsgInterface logMsgInterface;

	private double targetIntervalNs;
	private boolean isWaitForNextFrameEnabled;

	@SuppressWarnings("FieldCanBeLocal")
	private long lastFrameTimeNs = 0;
	private long nextFrameTimeNs = 0;
	private double errorPerFrameNs = 0.0;
	private double cumulativeErrorNs = 0.0;

	private long curFrameNr = 0;

	public AdaptiveScheduler(
				@NonNull LogMsgInterface logMsgInterface,
				double fps
			) {
		this.logMsgInterface = logMsgInterface;

		setFps(fps);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setFps(double fps) {
		this.targetIntervalNs = (fps >= 0.1 ? 1_000_000_000.0 / fps : -1.0);
		this.isWaitForNextFrameEnabled = (fps >= 0.1);
	}

	public long getSendIntervalNs() {
		return (long)targetIntervalNs;
	}

	public boolean getIsWaitForNextFrameEnabled() {
		return isWaitForNextFrameEnabled;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void waitForNextFrame() {
		if (! isWaitForNextFrameEnabled) {
			return;
		}
		if (curFrameNr == 0) {
			lastFrameTimeNs = System.nanoTime();
			nextFrameTimeNs = (lastFrameTimeNs + (long)targetIntervalNs);
			// if errorPerFrameNs is positive, we are behind, otherwise we are ahead
			errorPerFrameNs = ((double)nextFrameTimeNs - ((double)lastFrameTimeNs + targetIntervalNs));
		} else {
			long currentTimeNs = System.nanoTime();
			//double timeSinceLastWfnfNs = (currentTimeNs - lastFrameTimeNs);
			long tmpCumErrNs = (cumulativeErrorNs > 100_000.0 ? 100_000 : (cumulativeErrorNs < -100_000.0 ? -100_000 : 0));
			long remainingTimeToSleepNs = nextFrameTimeNs - currentTimeNs - tmpCumErrNs;
			cumulativeErrorNs -= (double)tmpCumErrNs;

			if (remainingTimeToSleepNs > 0) {
				sleepUntilNanos(currentTimeNs + remainingTimeToSleepNs);
			}

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
	 */
	public void sleepUntilNanos(long targetTimeNanos) {
		sleepUntilNanos(targetTimeNanos, true);
	}

	/**
	 * Sleeps until the specified target time (in nanoseconds since epoch) is reached.
	 * Uses a hybrid approach: Thread.sleep() for coarse waiting, then busy-waiting
	 * for high precision without overshooting.
	 * @param targetTimeNanos The target time in nanoseconds (System.nanoTime() format)
	 * @param warnIfInPast If true, logs a warning if the target time is in the past
	 */
	public void sleepUntilNanos(long targetTimeNanos, boolean warnIfInPast) {
		if (warnIfInPast) {
			final String FNC_NAME = getClass().getSimpleName() + ".sleepUntilNanos()";

			long currentTime = System.nanoTime();
			if (targetTimeNanos <= currentTime) {
				double tmpDeltaUs = (((double)targetTimeNanos - currentTime) / 1_000.0);
				if (Math.abs(tmpDeltaUs) >= 100.0) {
					logWarn(FNC_NAME,
							String
									.format("Target time is in the past (%.3f us, r=%d)", tmpDeltaUs, curFrameNr)
									.replace(",", ".")
						);
				}
				return;
			}
		}

		SleepHelper.precSleepUntilNanos(targetTimeNanos);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(
				RtxpLogLevel.WARN,
				Thread.currentThread().getName(),
				fncName + ": " + msg
			);
	}

}
