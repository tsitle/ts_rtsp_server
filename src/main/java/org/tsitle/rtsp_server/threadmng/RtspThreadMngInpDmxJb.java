package org.tsitle.rtsp_server.threadmng;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp_server.availstreams.AsCodecSettingsChangedFromDmxJbInterface;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.inp_to_internal_mq.ThreadInpDmxJb;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public final class RtspThreadMngInpDmxJb extends RtspThreadMngBase {

	private static final String POOL_NAME = "POOLDMXJB";

	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull AsCodecSettingsChangedFromDmxJbInterface codecSettingsChangedInterface;

	private final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull ThreadInpDmxJb> dmxThreadMap = new ConcurrentHashMap<>();

	public RtspThreadMngInpDmxJb(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull AsCodecSettingsChangedFromDmxJbInterface codecSettingsChangedInterface
			) {
		super(logMsgInterface, cancelToken, rtspSrvConfig);

		this.availableStreamsInterface = availableStreamsInterface;
		this.codecSettingsChangedInterface = codecSettingsChangedInterface;

		//
		this.pool = new ThreadPoolExecutor(
				rtspSrvConfig.getThreadsMaximumDmxJb(),
				rtspSrvConfig.getThreadsMaximumDmxJb(),
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(true)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isThreadForInputSourceIdRunning(@NonNull RtspProtoIdInputSource idInputSource) {
		return (dmxThreadMap.containsKey(idInputSource) && dmxThreadMap.get(idInputSource).isRunning());
	}

	public void startNewDmxThread(@NonNull RtspProtoIdInputSource idInputSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".startNewDmxThread()";

		if (pool == null) {
			return;
		}

		if (countActiveThreads() >= rtspSrvConfig.getThreadsMaximumDmxJb()) {
			logWarn(FNC_NAME, "cannot start DmxJb thread - pool full");
			return;
		}

		//
		ProUri inputSourceDmxJbUri = ProUri.ofEmpty();
		RtspProtoEsSourceExpandedInfo.TcSettingsAudio tcSettingsAudio = null;
		RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();
		try {
			RtspProtoInputSource tmpIsObj = availableStreamsInterface.getInputSourceObj(idInputSource);
			Set<@NonNull RtspProtoIdEsSource> tmpEsIds = tmpIsObj.getEsSourceIds();
			if (tmpEsIds.isEmpty()) {
				logError(FNC_NAME, "Invalid DMX IS ID '" + idInputSource + "': no ES sources");
				return;
			}
			for (RtspProtoIdEsSource tmpIdEs : tmpEsIds) {
				try {
					RtspProtoEsSourceExpandedInfo tmpEsei = availableStreamsInterface.getElementaryStreamSourceExpInfo(tmpIdEs);
					idEsSource.copyFrom(tmpIdEs);
					inputSourceDmxJbUri = tmpEsei.inputUri().clone();
					tcSettingsAudio = (tmpEsei.tcSettingsAudio() == null ? null : tmpEsei.tcSettingsAudio().clone());
					break;
				} catch (RtspProtoIdEsSourceNotFoundException e) {
					// should never happen
					logError(FNC_NAME, "Invalid DMX ES Source ID '" + tmpIdEs + "': " + e.getMessage());
					return;
				}
			}
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			// should never happen
			logError(FNC_NAME, "Invalid DMX IS ID '" + idInputSource + "': " + e.getMessage());
			return;
		}
		if (idEsSource.isEmpty()) {
			// should never happen
			logError(FNC_NAME, "Invalid DMX IS '" + idInputSource + "': ES ID is missing");
			return;
		}
		if (inputSourceDmxJbUri.isEmpty()) {
			// should never happen
			logError(FNC_NAME, "Invalid DMX IS '" + idInputSource + "': ES URI is missing");
			return;
		}
		if (tcSettingsAudio == null) {
			// should never happen
			logError(FNC_NAME, "Invalid DMX IS '" + idInputSource + "': ES TcSettings are missing");
			return;
		}
		logDebug(FNC_NAME, "Starting DmxJb for '" +
				idInputSource.getIdStr().orElse("-unset-") + "'");

		//
		ThreadInpDmxJb threadDmx = new ThreadInpDmxJb(
				logMsgInterface,
				cancelToken,
				inputSourceDmxJbUri,
				tcSettingsAudio,
				codecSettingsChangedInterface,
				idInputSource,
				idEsSource
			);

		try {
			pool.submit(threadDmx);

			//
			final int MAX_TIMEOUT_CNT = 100;
			int timeoutCnt = 0;
			while (! threadDmx.isRunning() && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
			}

			if (threadDmx.isRunning()) {
				dmxThreadMap.put(idInputSource, threadDmx);
			}
		} catch (RejectedExecutionException e) {
			logError(FNC_NAME, "RejectedExecutionException caught: " + POOL_NAME + " is most likely full");
		}
	}

	@Override
	public void doHousekeeping() {
		final String FNC_NAME = getClass().getSimpleName() + ".doHousekeeping()";

		for (Map.Entry<RtspProtoIdInputSource, ThreadInpDmxJb> entry : dmxThreadMap.entrySet()) {
			ThreadInpDmxJb threadDmx = entry.getValue();
			if (threadDmx.isRunning()) {
				continue;
			}
			logDebug(FNC_NAME, "removing DmxJb thread for ID=" + entry.getKey());
			shutdownThreadByIdInputSource(entry.getKey());
		}
	}

	public void shutdownThreadByIdInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		if (! dmxThreadMap.containsKey(idInputSource)) {
			return;
		}
		ThreadInpDmxJb threadDmx = dmxThreadMap.get(idInputSource);
		if (threadDmx != null) {
			threadDmx.stopThread();
			dmxThreadMap.remove(idInputSource);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void shutdownThreadsForIsIds(@NonNull Set<@NonNull RtspProtoIdInputSource> stopInputSourceIds) {
		for (RtspProtoIdInputSource entryId : stopInputSourceIds) {
			shutdownThreadByIdInputSource(entryId);
		}
	}

	public void shutdownAllThreads() {
		shutdownThreadsForIsIds(dmxThreadMap.keySet());
		//
		internalShutdownAllThreads(POOL_NAME);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private int countActiveThreads() {
		int resI = 0;
		for (ThreadInpDmxJb entryT : dmxThreadMap.values()) {
			if (entryT.isRunning()) {
				++resI;
			}
		}
		return resI;
	}

}
