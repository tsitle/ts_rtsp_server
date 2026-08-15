package org.tsitle.rtsp_server.threadmng;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.rtsp_tcp.RtspPlayThreadMngInterface;
import org.tsitle.rtsp_server.threads.rtsp_tcp.ThreadRtspTcpClientInbound;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public final class RtspThreadMngTci extends RtspThreadMngBase {

	private static final String POOL_NAME = "POOLRTSPTCI";

	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspPlayThreadMngInterface playThreadMngInterface;

	private final @NonNull Map<@NonNull Integer, @NonNull ThreadRtspTcpClientInbound> rtspTciThreadMap = new ConcurrentHashMap<>();

	public RtspThreadMngTci(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull RtspPlayThreadMngInterface playThreadMngInterface
			) {
		super(logMsgInterface, cancelToken, rtspSrvConfig);

		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.playThreadMngInterface = playThreadMngInterface;

		//
		this.pool = new ThreadPoolExecutor(
				rtspSrvConfig.getThreadsMaximumTci(),
				rtspSrvConfig.getThreadsMaximumTci(),
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(true)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void startNewTciThread(
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				boolean isRtspsConnection
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".startNewTciThread()";

		if (pool == null) {
			return;
		}

		if (countActiveThreads() >= rtspSrvConfig.getThreadsMaximumTci()) {
			logWarn(FNC_NAME, "cannot start TCI thread - pool full");
			return;
		}

		ThreadRtspTcpClientInbound threadRtspTci = new ThreadRtspTcpClientInbound(
				logMsgInterface,
				cancelToken,
				rtspSrvConfig,
				cfgServerNameAndVersion,
				availableStreamsInterface,
				globalSessionInfoInterface,
				playThreadMngInterface,
				clientConnectionNr,
				rtspSocketTcp,
				isRtspsConnection
			);

		try {
			pool.submit(threadRtspTci);

			//
			final int MAX_TIMEOUT_CNT = 100;
			int timeoutCnt = 0;
			while (! threadRtspTci.isRunning() && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
				}
			}

			if (threadRtspTci.isRunning()) {
				rtspTciThreadMap.put(clientConnectionNr, threadRtspTci);
			}
		} catch (RejectedExecutionException e) {
			logError(FNC_NAME, "RejectedExecutionException caught: " + POOL_NAME + " is most likely full");
			try { rtspSocketTcp.close(); } catch (IOException ignored) { }
		}
	}

	@Override
	public void doHousekeeping() {
		final String FNC_NAME = getClass().getSimpleName() + ".doHousekeeping()";

		for (Map.Entry<Integer, ThreadRtspTcpClientInbound> entry : rtspTciThreadMap.entrySet()) {
			ThreadRtspTcpClientInbound threadRtspTci = entry.getValue();
			if (threadRtspTci.isRunning()) {
				continue;
			}
			logDebug(FNC_NAME, "removing TCI thread for CCN=" + entry.getKey());
			shutdownThreadByCcn(entry.getKey());
		}
	}

	public void shutdownThreadByCcn(int clientConnectionNr) {
		if (! rtspTciThreadMap.containsKey(clientConnectionNr)) {
			return;
		}
		ThreadRtspTcpClientInbound threadRtspTci = rtspTciThreadMap.get(clientConnectionNr);
		if (threadRtspTci != null) {
			threadRtspTci.stopThread();
			rtspTciThreadMap.remove(clientConnectionNr);
		}
	}

	public void shutdownThreadBySessionId(@NonNull RtspProtoIdSession idSession) {
		if (idSession.isEmpty()) {
			return;
		}
		Set<Integer> keysToRemove = new HashSet<>();
		for (Map.Entry<Integer, ThreadRtspTcpClientInbound> entry : rtspTciThreadMap.entrySet()) {
			ThreadRtspTcpClientInbound threadRtspTci = entry.getValue();
			if (threadRtspTci.isRunning() && threadRtspTci.usesSessionId(idSession)) {
				keysToRemove.add(entry.getKey());
			}
		}
		for (Integer tmpCcn : keysToRemove) {
			shutdownThreadByCcn(tmpCcn);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void shutdownThreadsForSessionIds(@NonNull Set<@NonNull RtspProtoIdSession> stopSessionIds) {
		for (RtspProtoIdSession entryId : stopSessionIds) {
			shutdownThreadBySessionId(entryId);
		}
	}

	public void shutdownAllThreads() {
		for (Integer tmpCcn : rtspTciThreadMap.keySet()) {
			shutdownThreadByCcn(tmpCcn);
		}
		//
		internalShutdownAllThreads(POOL_NAME);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private int countActiveThreads() {
		int resI = 0;
		for (ThreadRtspTcpClientInbound entryT : rtspTciThreadMap.values()) {
			if (entryT.isRunning()) {
				++resI;
			}
		}
		return resI;
	}

}
