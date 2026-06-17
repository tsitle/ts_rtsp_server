package org.tsitle.rtsp_server.threads;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RunnableBase implements Runnable {

	private final @NonNull CancelToken cancelToken;
	protected final AtomicBoolean isRunning = new AtomicBoolean(false);

	protected final @Nullable LogMsgInterface logMsgInterface;

	/**
	 * Constructor.
	 * @param cancelToken Cancel token
	 */
	@SuppressWarnings("unused")
	public RunnableBase(@NonNull CancelToken cancelToken) {
		this.logMsgInterface = null;
		this.cancelToken = cancelToken;
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 */
	public RunnableBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken
			) {
		this.logMsgInterface = logMsgInterface;
		this.cancelToken = cancelToken;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public synchronized boolean hasBeenRequestedToStop() {
		return (cancelToken.cancelled || Thread.currentThread().isInterrupted());
	}

	@SuppressWarnings("unused")
	public synchronized boolean isRunning() {
		return isRunning.get();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
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
