package org.tsitle.lib_xrtxp.common.helpers;

import java.util.concurrent.locks.LockSupport;

public final class SleepHelper {

	private SleepHelper() { }

	/**
	 * Not very precise sleep.
	 * @param millis Milliseconds to sleep for
	 * @return True if the sleep was successful, false if interrupted
	 */
	public static boolean basicSleep(long millis) {
		return basicSleep(millis, 0);
	}

	/**
	 * Not very precise sleep.
	 * @param millis Milliseconds to sleep for
	 * @param nanos Nanoseconds to sleep for
	 * @return True if the sleep was successful, false if interrupted
	 */
	public static boolean basicSleep(long millis, int nanos) {
		if (millis < 0L || nanos < 0 || (millis == 0L && nanos == 0)) {
			return true;
		}
		try {
			Thread.sleep(millis, nanos);
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}

	/**
	 * Very precise sleep without overshooting.
	 * @param millis Milliseconds to sleep for
	 * @param nanos Nanoseconds to sleep for
	 * @return True if the sleep was successful, false if interrupted
	 */
	@SuppressWarnings("unused")
	public static boolean precSleep(long millis, int nanos) {
		if (millis < 0L || nanos < 0 || (millis == 0L && nanos == 0)) {
			return true;
		}
		return precSleepUntilNanos(System.nanoTime() + (millis * 1_000_000L) + nanos);
	}

	/**
	 * Very precisely sleeps until the specified target time (in nanoseconds since epoch) is reached.<br />
	 * Uses a hybrid approach: Thread.sleep() for coarse waiting, then busy-waiting
	 * for high precision without overshooting.
	 * @param targetTimeNanos The target time in nanoseconds (System.nanoTime() format)
	 */
	public static boolean precSleepUntilNanos(long targetTimeNanos) {
		long currentTime = System.nanoTime();

		if (targetTimeNanos <= currentTime) {
			return true;
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
					Thread.currentThread().interrupt();  // restore flag
					return false;
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

		return true;
	}

}
