package org.tsitle.rtsp_server.threads.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.types.TimestampEpoch;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.threads.ThreadBase;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ThreadRtxpLogger extends ThreadBase {

	private record LogEntry(
			@NonNull TimestampEpoch timestamp,
			@NonNull RtxpLogLevel level,
			@NonNull String threadId,
			@NonNull String msg
		) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final String OUTPUT_FN_DATETIME = "%DATETIME%";
	private static final String OUTPUT_FN_EXT = ".log";

	private boolean enableOutputFile = false;
	private boolean enableOutputConsole = false;
	private String outputFilename = "rtxp-" + OUTPUT_FN_DATETIME + OUTPUT_FN_EXT;

	private final Queue<@NonNull LogEntry> msgQueue = new ConcurrentLinkedQueue<>();
	private final ReentrantLock lock = new ReentrantLock();
	/** Condition to signal that a new message has been added to the queue or the thread has been requested to stop */
	private final Condition stateChanged = lock.newCondition();

	private @Nullable FileOutputStream outpFileFos;
	private @Nullable PrintStream outpFilePs;

	public ThreadRtxpLogger() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setEnableOutputFile(boolean enable, @NonNull String filename) {
		if (enableOutputFile && filename.isBlank()) {
			throw new IllegalArgumentException("Output filename cannot be blank when enabling output file");
		}
		this.enableOutputFile = enable;
		this.outputFilename = filename;
		if (! this.outputFilename.endsWith(OUTPUT_FN_EXT)) {
			this.outputFilename += OUTPUT_FN_EXT;
		}
	}

	public void setEnableOutputConsole(boolean enable) {
		this.enableOutputConsole = enable;
	}

	public boolean havePendingMessages() {
		lock.lock();
		try {
			return (! msgQueue.isEmpty());
		} finally {
			lock.unlock();
		}
	}

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

		Thread.currentThread().setName("RTXPLOGGER");

		if (enableOutputFile) {
			try {
				String tmpOutpFn = buildOutputFilename();
				outpFileFos = new FileOutputStream(tmpOutpFn, true);
				outpFilePs = new PrintStream(outpFileFos);
			} catch (FileNotFoundException e) {
				System.err.println(FNC_NAME + ": Failed to open log file for writing: " + e.getMessage());
				return;
			}
		}

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
			//
			if (outpFilePs != null) {
				outpFilePs.close();
			}
			if (outpFileFos != null) {
				try {
					outpFileFos.close();
				} catch (IOException ignore) {
					// ignore
				}
			}
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

	private @NonNull String buildOutputFilename() {
		if (! outputFilename.contains(OUTPUT_FN_DATETIME)) {
			return outputFilename;
		}
		String tmpTs = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"));
		return outputFilename.replace(OUTPUT_FN_DATETIME, tmpTs);
	}

	private @NonNull LogEntry createLogEntry(@NonNull RtxpLogLevel level, @NonNull String threadId, @NonNull String msg) {
		return new LogEntry(
				TimestampEpoch.ofNow(),
				level,
				threadId,
				msg
			);
	}

	private void outputMsg(@NonNull LogEntry entry) {
		if (! (enableOutputConsole || enableOutputFile)) {
			return;
		}
		String prefix = switch (entry.level) {
				case DEBUG -> "DEBUG";
				case INFO  -> "INFO_";
				case WARN  -> "WARN_";
				case ERROR -> "ERROR";
			};
		final String outpStr = String.format(
				"%s: |%-24s|%14d| <%s> %s%n",
				prefix,
				entry.timestamp.toIso8601StyleString(),  // 2026-03-27T19:54:50.975Z
				entry.timestamp.getEpochNsUnsigned64bit().orElseThrow(),  // 45032236077403
				entry.threadId,
				entry.msg
			);
		if (enableOutputConsole) {
			PrintStream ps = (entry.level == RtxpLogLevel.DEBUG || entry.level == RtxpLogLevel.INFO ? System.out : System.err);
			ps.print(outpStr);
		}
		if (enableOutputFile && outpFilePs != null) {
			outpFilePs.print(outpStr);
			outpFilePs.flush();
		}
	}

}
