package org.tsitle.rtsp_server.threads;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ThreadBase extends Thread {

	protected final AtomicBoolean doStop = new AtomicBoolean(false);
	protected final AtomicBoolean isRunning = new AtomicBoolean(false);

	protected final @Nullable LogMsgInterface logMsgInterface;

	/**
	 * Constructor.
	 */
	public ThreadBase() {
		this.logMsgInterface = null;
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 */
	public ThreadBase(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void stopThread() {
		if (doStop.get()) {
			return;
		}
		doStop.set(true);
		stopThreadHook();
		while (isRunning.get()) {
			try {
				//noinspection BusyWait
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				break;
			}
		}
	}

	@SuppressWarnings("unused")
	public synchronized boolean hasBeenRequestedToStop() {
		return doStop.get();
	}

	@SuppressWarnings("unused")
	public synchronized boolean isRunning() {
		return isRunning.get();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void stopThreadHook();

	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("unused")
	protected void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}
	protected void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
