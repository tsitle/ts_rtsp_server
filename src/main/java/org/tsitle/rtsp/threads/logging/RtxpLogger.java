package org.tsitle.rtsp.threads.logging;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.ThreadBase;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class RtxpLogger extends ThreadBase {

	private record LogEntry(
			Instant timestampInstant,
			long timestampNanos,
			@NonNull RtxpLogLevel level,
			@NonNull String threadId,
			@NonNull String msg
		) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final Queue<@NonNull LogEntry> msgQueue = new ConcurrentLinkedQueue<>();
	private final ReentrantLock lock = new ReentrantLock();
	/** Condition to signal that a new message has been added to the queue or the thread has been requested to stop */
	private final Condition stateChanged = lock.newCondition();

	public RtxpLogger() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void log(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
		lock.lock();
		try {
			msgQueue.add(createLogEntry(logLevel, threadId, msg));
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);

		//
		try {
			while (! doStop.get()) {
				mainLoop();
			}
		} catch (InterruptedException e) {
			outputMsg(createLogEntry(
					RtxpLogLevel.ERROR,
					Thread.currentThread().getName(),
					FNC_NAME + ": InterruptedException"
				));
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			outputMsg(createLogEntry(
					RtxpLogLevel.ERROR,
					Thread.currentThread().getName(),
					FNC_NAME + ": Exception: " + e.getMessage()
				));
		} finally {
			isRunning.set(false);
			outputMsg(createLogEntry(
					RtxpLogLevel.DEBUG,
					Thread.currentThread().getName(),
					FNC_NAME + ": Thread ended"
				));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		lock.lock();
		try {
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		lock.lock();
		try {
			if (doStop.get()) {
				return;
			}
			stateChanged.await();
			if (doStop.get()) {
				return;
			}
			while (! msgQueue.isEmpty()) {
				outputMsg(msgQueue.poll());
			}
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull LogEntry createLogEntry(@NonNull RtxpLogLevel level, @NonNull String threadId, @NonNull String msg) {
		return new LogEntry(
				Instant.now(),
				System.nanoTime(),
				level,
				threadId,
				msg
			);
	}

	private void outputMsg(@NonNull LogEntry entry) {
		PrintStream ps = (entry.level == RtxpLogLevel.DEBUG || entry.level == RtxpLogLevel.INFO ? System.out : System.err);
		String prefix = switch (entry.level) {
				case DEBUG -> "DEBUG";
				case INFO  -> "INFO_";
				case WARN  -> "WARN_";
				case ERROR -> "ERROR";
			};
		ps.format(
				"%s: |%-30s|%14d| <%s> %s%n",
				prefix,
				entry.timestampInstant,  // 2026-03-27T19:54:50.975917801Z
				entry.timestampNanos,    // 45032236077403
				entry.threadId,
				entry.msg
			);
	}

}
