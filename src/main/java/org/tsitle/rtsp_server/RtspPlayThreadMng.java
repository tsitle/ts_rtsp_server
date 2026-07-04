package org.tsitle.rtsp_server;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;
import org.tsitle.rtsp_server.threads.rtsp_tcp.RtspPlayThreadMngInterface;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

public final class RtspPlayThreadMng implements RtspPlayThreadMngInterface {

	private record VarsForNewThread(
			@NonNull RtspProtoSessionInfo rtspSessionInfo,
			@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
			@NonNull RtspChildThreadsCbRtxpTcpInterface childThreadsCbRtpTcpInterface
		) { }

	private static final int RTSP_THREADS_PLAY = 20;  // one thread per client session

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull CancelToken cancelToken;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc;

	private final @NonNull Queue<VarsForNewThread> queueForNewThread = new ConcurrentLinkedQueue<>();

	private final @NonNull Map<@NonNull RtspProtoIdSession, @NonNull ThreadRtspPlay> rtspPlayThreadMap = new ConcurrentHashMap<>();

	private static final ExecutorService poolRtspPlay = new ThreadPoolExecutor(
			RTSP_THREADS_PLAY,
			RTSP_THREADS_PLAY,
			60L, TimeUnit.SECONDS,
			new SynchronousQueue<>(true)
		);

	public RtspPlayThreadMng(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc
			) {
		this.logMsgInterface = logMsgInterface;
		this.cancelToken = cancelToken;
		this.rtspConfig = rtspConfig;
		this.globalSessionInfoSvc = globalSessionInfoSvc;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @Nullable ThreadRtspPlay startOrGetThreadRtspPlay(
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspChildThreadsCbRtxpTcpInterface childThreadsCbRtpTcpInterface
			) {
		if (rtspPlayThreadMap.containsKey(rtspSessionInfo.getIdSession())) {
			return rtspPlayThreadMap.get(rtspSessionInfo.getIdSession());
		}

		// queue the creation of a new thread
		queueForNewThread.add(new VarsForNewThread(rtspSessionInfo, availableStreamsInterface, childThreadsCbRtpTcpInterface));

		// wait for the new thread to become available
		final int MAX_TIMEOUT_CNT = 5000;
		int timeoutCnt = 0;
		while (! rtspPlayThreadMap.containsKey(rtspSessionInfo.getIdSession()) && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
			}
		}
		return rtspPlayThreadMap.get(rtspSessionInfo.getIdSession());
	}

	@Override
	public void updateThreadsSessionInfoBySessionId(
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoSessionInfo rtspSessionInfo
			) {
		if (idSession.isEmpty() || ! rtspPlayThreadMap.containsKey(idSession)) {
			return;
		}
		ThreadRtspPlay threadRtspPlay = rtspPlayThreadMap.get(idSession);
		if (threadRtspPlay != null) {
			threadRtspPlay.updateSessionInfo(rtspSessionInfo);
		}
	}

	@Override
	public void shutdownThreadBySessionId(@NonNull RtspProtoIdSession idSession) {
		if (idSession.isEmpty() || ! rtspPlayThreadMap.containsKey(idSession)) {
			return;
		}
		ThreadRtspPlay threadRtspPlay = rtspPlayThreadMap.get(idSession);
		if (threadRtspPlay != null) {
			threadRtspPlay.stopThread();
			rtspPlayThreadMap.remove(idSession);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void startNewPlayThreadFromQueue() {
		final String FNC_NAME = getClass().getSimpleName() + ".startNewThreadFromQueue()";

		if (queueForNewThread.isEmpty()) {
			return;
		}

		VarsForNewThread varsForNewThread = queueForNewThread.poll();

		ThreadRtspPlay threadRtspPlay = new ThreadRtspPlay(
				logMsgInterface,
				cancelToken,
				rtspConfig,
				varsForNewThread.rtspSessionInfo(),
				varsForNewThread.childThreadsCbRtpTcpInterface(),
				varsForNewThread.availableStreamsInterface(),
				globalSessionInfoSvc
			);

		try {
			poolRtspPlay.submit(threadRtspPlay);

			//
			final int MAX_TIMEOUT_CNT = 100;
			int timeoutCnt = 0;
			while (! threadRtspPlay.isRunning() && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
				}
			}

			if (threadRtspPlay.isRunning()) {
				rtspPlayThreadMap.put(varsForNewThread.rtspSessionInfo().getIdSession(), threadRtspPlay);
			}
		} catch (RejectedExecutionException e) {
			logError(FNC_NAME, "RejectedExecutionException caught: " + e.getMessage());
		}
	}

	public void doHousekeepingForPlayThreads() {
		final String FNC_NAME = getClass().getSimpleName() + ".doHousekeepingForPlayThreads()";

		for (Map.Entry<RtspProtoIdSession, ThreadRtspPlay> entry : rtspPlayThreadMap.entrySet()) {
			ThreadRtspPlay threadRtspPlay = entry.getValue();
			if (! threadRtspPlay.isRunning()) {
				logDebug(FNC_NAME, "removing play thread for SID=" + entry.getKey().getIdStr().orElse("-unset-"));
				shutdownThreadBySessionId(entry.getKey());
				continue;
			}
			if (! threadRtspPlay.getIsTransportUdp()) {
				continue;
			}
			long tmpTimeDiff = threadRtspPlay.getLastIncomingRtspRequestTimeDeltaSeconds();
			if (tmpTimeDiff > RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT +
					RtspProtoHighConstants.SESSION_TIMEOUT_TOLERANCE_SEC + 2) {
				logDebug(FNC_NAME, "RTSP session sid=" + entry.getKey().getIdStr().orElse("-unset-") +
						" timeout after " + tmpTimeDiff + " seconds");
				shutdownThreadBySessionId(entry.getKey());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void shutdownAllThreads() {
		poolRtspPlay.shutdown();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
