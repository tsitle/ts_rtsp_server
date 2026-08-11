package org.tsitle.rtsp_server;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;

import java.util.concurrent.*;

abstract class RtspThreadMngBase {

	protected final @NonNull LogMsgInterface logMsgInterface;
	protected final @NonNull CancelToken cancelToken;
	protected final @NonNull RtspSrvConfigMain rtspSrvConfig;

	protected @Nullable ExecutorService pool = null;

	public RtspThreadMngBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig
			) {
		this.logMsgInterface = logMsgInterface;
		this.cancelToken = cancelToken;
		this.rtspSrvConfig = rtspSrvConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract void doHousekeeping();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void internalShutdownAllThreads(@NonNull String poolName) {
		final String FNC_NAME = getClass().getSimpleName() + ".internalShutdownAllThreads()";

		if (pool == null) {
			return;
		}
		pool.shutdown();
		//
		try {
			if (! pool.awaitTermination(10, TimeUnit.SECONDS)) {
				System.err.println(FNC_NAME + ": timeout, forcing shutdown " + poolName);
				pool.shutdownNow();  // force shutdown
			}
		} catch (InterruptedException e) {
			System.err.println(FNC_NAME + ": interrupted, forcing shutdown " + poolName);
			pool.shutdownNow();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
