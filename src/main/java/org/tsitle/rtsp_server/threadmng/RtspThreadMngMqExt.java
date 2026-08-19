package org.tsitle.rtsp_server.threadmng;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.mq_e2i.CodecSettingsChangedFromMqInterface;
import org.tsitle.rtsp_server.threads.mq_e2i.ThreadMqE2I;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

public final class RtspThreadMngMqExt extends RtspThreadMngBase {

	private static final String POOL_NAME = "POOLMQEXT";

	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedInterface;

	private final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull ThreadMqE2I> mqThreadMap = new ConcurrentHashMap<>();

	public RtspThreadMngMqExt(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedInterface
			) {
		super(logMsgInterface, cancelToken, rtspSrvConfig);

		this.availableStreamsInterface = availableStreamsInterface;
		this.codecSettingsChangedInterface = codecSettingsChangedInterface;

		//
		this.pool = new ThreadPoolExecutor(
				rtspSrvConfig.getThreadsMaximumMq(),
				rtspSrvConfig.getThreadsMaximumMq(),
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(true)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isThreadForEsSourceIdRunning(@NonNull RtspProtoIdEsSource idEsSource) {
		return (mqThreadMap.containsKey(idEsSource) && mqThreadMap.get(idEsSource).isRunning());
	}

	public void startNewMqExtThread(@NonNull RtspProtoIdEsSource idEsSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".startNewMqExtThread()";

		if (pool == null) {
			return;
		}

		if (countActiveThreads() >= rtspSrvConfig.getThreadsMaximumMq()) {
			logWarn(FNC_NAME, "cannot start MqE2I thread - pool full");
			return;
		}

		//
		RtspProtoEsSourceExpandedInfo esei;
		try {
			esei = availableStreamsInterface.getElementaryStreamSourceExpInfo(idEsSource);
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			// should never happen
			logError(FNC_NAME, "Invalid MQ ES Source ID '" + idEsSource + "': " + e.getMessage());
			return;
		}
		Optional<String> tmpSslCertPath;
		try {
			tmpSslCertPath = rtspSrvConfig.getMqServerSslCertificatePath(esei.inputUri());
		} catch (ConfigInvalidException e) {
			// should never happen
			throw new IllegalStateException(e);
		}
		MqElementaryStreamSourceSettings mqSetts = MqElementaryStreamSourceSettings.of(
				esei.credentials(),
				esei.inputUri()
			);
		logDebug(FNC_NAME, "Starting MqE2I for '" +
				mqSetts.getHostname() + ":" + Integer.toUnsignedString(mqSetts.getPort().getPort16bit().orElseThrow()) + ":" +
				mqSetts.getRscGroup() + ":" + mqSetts.getRscChannel() + "'");

		//
		ThreadMqE2I threadMqExt = new ThreadMqE2I(
				logMsgInterface,
				cancelToken,
				codecSettingsChangedInterface,
				idEsSource,
				mqSetts,
				tmpSslCertPath.orElse("")
			);

		try {
			pool.submit(threadMqExt);

			//
			final int MAX_TIMEOUT_CNT = 100;
			int timeoutCnt = 0;
			while (! threadMqExt.isRunning() && ++timeoutCnt <= MAX_TIMEOUT_CNT) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
				}
			}

			if (threadMqExt.isRunning()) {
				mqThreadMap.put(idEsSource, threadMqExt);
			}
		} catch (RejectedExecutionException e) {
			logError(FNC_NAME, "RejectedExecutionException caught: " + POOL_NAME + " is most likely full");
		}
	}

	@Override
	public void doHousekeeping() {
		final String FNC_NAME = getClass().getSimpleName() + ".doHousekeeping()";

		for (Map.Entry<RtspProtoIdEsSource, ThreadMqE2I> entry : mqThreadMap.entrySet()) {
			ThreadMqE2I threadMqExt = entry.getValue();
			if (threadMqExt.isRunning()) {
				continue;
			}
			logDebug(FNC_NAME, "removing MqE2I thread for ID=" + entry.getKey());
			shutdownThreadByIdEsSource(entry.getKey());
		}
	}

	public void shutdownThreadByIdEsSource(@NonNull RtspProtoIdEsSource idEsSource) {
		if (! mqThreadMap.containsKey(idEsSource)) {
			return;
		}
		ThreadMqE2I threadMqExt = mqThreadMap.get(idEsSource);
		if (threadMqExt != null) {
			threadMqExt.stopThread();
			mqThreadMap.remove(idEsSource);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void shutdownThreadsForEsIds(@NonNull Set<@NonNull RtspProtoIdEsSource> stopEsSourceIds) {
		for (RtspProtoIdEsSource entryId : stopEsSourceIds) {
			shutdownThreadByIdEsSource(entryId);
		}
	}

	public void shutdownAllThreads() {
		shutdownThreadsForEsIds(mqThreadMap.keySet());
		//
		internalShutdownAllThreads(POOL_NAME);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private int countActiveThreads() {
		int resI = 0;
		for (ThreadMqE2I entryT : mqThreadMap.values()) {
			if (entryT.isRunning()) {
				++resI;
			}
		}
		return resI;
	}

}
