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
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.availstreams.CodecSettingsChangedFromDmxRtspInterface;
import org.tsitle.rtsp_server.threads.inp_e2i.ThreadInpDmxRtsp;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

public final class RtspThreadMngInpDmxRtsp extends RtspThreadMngBase {

	private static final String POOL_NAME = "POOLDMXRTSP";

	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull CodecSettingsChangedFromDmxRtspInterface codecSettingsChangedInterface;

	private final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull ThreadInpDmxRtsp> dmxThreadMap = new ConcurrentHashMap<>();

	public RtspThreadMngInpDmxRtsp(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull CodecSettingsChangedFromDmxRtspInterface codecSettingsChangedInterface
			) {
		super(logMsgInterface, cancelToken, rtspSrvConfig);

		this.availableStreamsInterface = availableStreamsInterface;
		this.codecSettingsChangedInterface = codecSettingsChangedInterface;

		//
		this.pool = new ThreadPoolExecutor(
				rtspSrvConfig.getThreadsMaximumDmxRtsp(),
				rtspSrvConfig.getThreadsMaximumDmxRtsp(),
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

		if (countActiveThreads() >= rtspSrvConfig.getThreadsMaximumDmxRtsp()) {
			logWarn(FNC_NAME, "cannot start DmxRtsp thread - pool full");
			return;
		}

		//
		ProUri inputUri = null;
		RtspProtoIdEsSource idEsSourceVid = RtspProtoIdEsSource.ofEmpty();
		RtspProtoIdEsSource idEsSourceAud = RtspProtoIdEsSource.ofEmpty();
		try {
			RtspProtoInputSource tmpIsObj = availableStreamsInterface.getInputSourceObj(idInputSource);
			Set<@NonNull RtspProtoIdEsSource> tmpEsIds = tmpIsObj.getEsSourceIds();
			if (tmpEsIds.isEmpty()) {
				logError(FNC_NAME, "Invalid DMX IS Source ID '" + idInputSource + "': no ES sources");
				return;
			}
			for (RtspProtoIdEsSource tmpIdEs : tmpEsIds) {
				try {
					RtspProtoEsSourceExpandedInfo tmpEsei = availableStreamsInterface.getElementaryStreamSourceExpInfo(tmpIdEs);
					if (tmpEsei.codec().isVideo()) {
						idEsSourceVid.copyFrom(tmpIdEs);
					} else if (tmpEsei.codec().isAudio()) {
						idEsSourceAud.copyFrom(tmpIdEs);
					} else {
						continue;
					}
					inputUri = tmpEsei.inputUri();
				} catch (RtspProtoIdEsSourceNotFoundException e) {
					// should never happen
					logError(FNC_NAME, "Invalid DMX ES Source ID '" + tmpIdEs + "': " + e.getMessage());
					return;
				}
			}
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			// should never happen
			logError(FNC_NAME, "Invalid DMX IS Source ID '" + idInputSource + "': " + e.getMessage());
			return;
		}
		logDebug(FNC_NAME, "Starting DmxRtsp for '" +
				idInputSource.getIdStr().orElse("-unset-") + "'");

		//
		ThreadInpDmxRtsp threadDmx = new ThreadInpDmxRtsp(
				logMsgInterface,
				cancelToken,
				Objects.requireNonNull(inputUri),
				codecSettingsChangedInterface,
				idInputSource,
				idEsSourceVid,
				idEsSourceAud
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

		for (Map.Entry<RtspProtoIdInputSource, ThreadInpDmxRtsp> entry : dmxThreadMap.entrySet()) {
			ThreadInpDmxRtsp threadDmx = entry.getValue();
			if (threadDmx.isRunning()) {
				continue;
			}
			logDebug(FNC_NAME, "removing DmxRtsp thread for ID=" + entry.getKey());
			shutdownThreadByIdInputSource(entry.getKey());
		}
	}

	public void shutdownThreadByIdInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		if (! dmxThreadMap.containsKey(idInputSource)) {
			return;
		}
		ThreadInpDmxRtsp threadDmx = dmxThreadMap.get(idInputSource);
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
		for (ThreadInpDmxRtsp entryT : dmxThreadMap.values()) {
			if (entryT.isRunning()) {
				++resI;
			}
		}
		return resI;
	}

}
