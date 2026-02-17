package org.tsitle.rtsp.threads.logging;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.ThreadBase;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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

	public RtxpLogger() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void log(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
		msgQueue.add(createLogEntry(logLevel, threadId, msg));
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
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		if (! msgQueue.isEmpty()) {
			outputMsg(msgQueue.poll());
		} else {
			Thread.sleep(10);
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
				"%s: |%s|%d| <%s> %s%n",
				prefix,
				entry.timestampInstant,
				entry.timestampNanos,
				entry.threadId,
				entry.msg
			);
	}

}
