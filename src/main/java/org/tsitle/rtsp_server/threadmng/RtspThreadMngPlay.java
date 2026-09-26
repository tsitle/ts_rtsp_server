package org.tsitle.rtsp_server.threadmng;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;
import org.tsitle.rtsp_server.threads.rtsp_tcp.RtspPlayThreadMngInterface;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

public final class RtspThreadMngPlay extends RtspThreadMngBase implements RtspPlayThreadMngInterface {

	private record VarsForNewThread(
			@NonNull RtspProtoSessionInfo rtspSessionInfo,
			@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
			@NonNull RtspChildThreadsCbRtxpTcpInterface childThreadsCbRtpTcpInterface
		) { }

	private static final String POOL_NAME = "POOLRTSPPLAY";
	private static final int ADDITIONAL_SESSION_TIMEOUT_TOLERANCE_SECS = 2;

	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;

	private final @NonNull Queue<VarsForNewThread> queueForNewThread = new ConcurrentLinkedQueue<>();

	private final @NonNull Map<@NonNull RtspProtoIdSession, @NonNull ThreadRtspPlay> rtspPlayThreadMap = new ConcurrentHashMap<>();

	public RtspThreadMngPlay(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		super(logMsgInterface, cancelToken, rtspSrvConfig);

		this.globalSessionInfoInterface = globalSessionInfoInterface;

		//
		this.pool = new ThreadPoolExecutor(
				rtspSrvConfig.getThreadsMaximumPlay(),
				rtspSrvConfig.getThreadsMaximumPlay(),
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(true)
			);
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
				break;
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

		if (pool == null) {
			return;
		}
		if (queueForNewThread.isEmpty()) {
			return;
		}

		// poll and remove the head of the queue - that way we don't run in an infinite loop if the pool is full
		VarsForNewThread varsForNewThread = queueForNewThread.poll();
		if (varsForNewThread == null) {
			return;
		}

		//
		if (countActiveThreads() >= rtspSrvConfig.getThreadsMaximumPlay()) {
			logWarn(FNC_NAME, "cannot start Play thread - pool full");
			return;
		}

		//
		ThreadRtspPlay threadRtspPlay = new ThreadRtspPlay(
				logMsgInterface,
				cancelToken,
				rtspSrvConfig,
				varsForNewThread.rtspSessionInfo(),
				varsForNewThread.childThreadsCbRtpTcpInterface(),
				varsForNewThread.availableStreamsInterface(),
				globalSessionInfoInterface
			);

		try {
			pool.submit(threadRtspPlay);

			//
			final int MAX_TIMEOUT_CNT = 100;
			int timeoutCnt = 0;
			while (! threadRtspPlay.isRunning() && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
			}

			if (threadRtspPlay.isRunning()) {
				rtspPlayThreadMap.put(varsForNewThread.rtspSessionInfo().getIdSession(), threadRtspPlay);
			}
		} catch (RejectedExecutionException e) {
			logError(FNC_NAME, "RejectedExecutionException caught: " + POOL_NAME + " is most likely full");
		}
	}

	@Override
	public void doHousekeeping() {
		final String FNC_NAME = getClass().getSimpleName() + ".doHousekeeping()";

		for (Map.Entry<RtspProtoIdSession, ThreadRtspPlay> entry : rtspPlayThreadMap.entrySet()) {
			ThreadRtspPlay threadRtspPlay = entry.getValue();
			if (! threadRtspPlay.isRunning()) {
				logDebug(FNC_NAME, "removing Play thread for SID=" + entry.getKey().getIdStr().orElse("-unset-"));
				shutdownThreadBySessionId(entry.getKey());
				continue;
			}
			boolean doShutdown = false;
			if (! threadRtspPlay.getIsTransportUdp()) {
				doShutdown = (! threadRtspPlay.getIsTcpConnectionAlive());
				if (doShutdown) {
					logDebug(FNC_NAME, "RTSP session sid=" + entry.getKey().getIdStr().orElse("-unset-") +
						" lost TCP connection");
				}
			} else {
				long tmpTimeDiff = threadRtspPlay.getLastIncomingRtspRequestTimeDeltaSeconds();
				if (tmpTimeDiff > RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT +
						RtspProtoHighConstants.SESSION_TIMEOUT_TOLERANCE_SEC + ADDITIONAL_SESSION_TIMEOUT_TOLERANCE_SECS) {
					logDebug(FNC_NAME, "RTSP session sid=" + entry.getKey().getIdStr().orElse("-unset-") +
							" timeout after " + tmpTimeDiff + " seconds");
					doShutdown = true;
				}
			}
			if (doShutdown) {
				shutdownThreadBySessionId(entry.getKey());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void shutdownThreadsForSessionIds(@NonNull Set<@NonNull RtspProtoIdSession> stopSessionIds) {
		for (RtspProtoIdSession entryId : stopSessionIds) {
			shutdownThreadBySessionId(entryId);
		}
	}

	public void shutdownAllThreads() {
		shutdownThreadsForSessionIds(rtspPlayThreadMap.keySet());
		//
		internalShutdownAllThreads(POOL_NAME);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private int countActiveThreads() {
		int resI = 0;
		for (ThreadRtspPlay entryT : rtspPlayThreadMap.values()) {
			if (entryT.isRunning()) {
				++resI;
			}
		}
		return resI;
	}

}
