package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreadRtspPlay extends RunnableBase implements RtspChildThreadsCbNotifyThreadReadyInterface {

	private final @NonNull String threadName;

	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;

	private final @NonNull RtspChildThreadMng rtspChildThreadMng;

	/** Track 'Thread-Is-Ready-For-Playback' states per Sub-Stream */
	private final @NonNull Map<@NonNull RtspProtoIdSubStream, @NonNull Boolean> threadReadyStates = new ConcurrentHashMap<>();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspConfig RTSP configuration
	 * @param rtspSessionInfo Session info
	 * @param rctcbRtpTcp Callback interface for RTSP child threads
	 * @param rctcbRtcpFromRtp Callback interface for RTSP child threads
	 * @param availableStreamsInterface Available streams instance
	 * @param globalSessionInfoSvc Global Session Info service
	 * @param clientConnectionNr Client connection number
	 */
	public ThreadRtspPlay(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspChildThreadsCbRtxpTcpInterface rctcbRtpTcp,
				@NonNull RtspChildThreadsCbRtcpFromRtpInterface rctcbRtcpFromRtp,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc,
				int clientConnectionNr
			) {
		super(logMsgInterface, cancelToken);

		this.rtspSessionInfo = rtspSessionInfo;

		//
		this.threadName = "RTSP_PLAY#c" + clientConnectionNr;

		//
		Map<RtspProtoIdSubStream, RtspProtoSetupInfoForSubStream> setupInfoPerSsMap = new HashMap<>();
		Set<RtspProtoIdSubStream> subStreamIds = rtspSessionInfo.getDescrAvailableSubStreamIds();
		for (RtspProtoIdSubStream tmpIdSs : subStreamIds) {
			try {
				RtspProtoSetupInfoForSubStream tmpSiSs = rtspSessionInfo.getDescrSetupInfoBySubStreamsId(tmpIdSs);
				setupInfoPerSsMap.put(tmpIdSs, tmpSiSs);
			} catch (RtspProtoSessionInfoException e) {
				throw new IllegalStateException(getClass().getSimpleName() + ".ctor(): idSubStream not found");
			}
		}
		this.rtspChildThreadMng = new RtspChildThreadMng(
				logMsgInterface,
				rtspConfig,
				clientConnectionNr,
				subStreamIds,
				this.rtspSessionInfo.getIdSession(),
				this.rtspSessionInfo.getClientIpAddr(),
				setupInfoPerSsMap,
				this,
				rctcbRtpTcp,
				rctcbRtcpFromRtp,
				availableStreamsInterface,
				globalSessionInfoSvc
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logInfo(FNC_NAME, "Playing RTP");

		//
		try {
			int loopCounter = 0;
			while (! hasBeenRequestedToStop()) {
				if (! mainLoop(++loopCounter)) {
					break;
				}
			}

			// send a BYE packet per stream to let the client know we are terminating the session
			for (ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesAll()) {
				if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
					continue;
				}
				ctfos.rtcpThreadSendRecv.appendByePacketToSendQueue();
			}
		} catch (InterruptedException e2) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			//e.printStackTrace();
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			logInfo(FNC_NAME, "Closing");
			//
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			rtspChildThreadMng.pauseOrStopChildThreads(false);
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesOnlyRunning() {
		return rtspChildThreadMng.getCtfosMapValuesOnlyRunning();
	}

	public boolean ctfosMapContainsKey(@NonNull RtspProtoIdSubStream idSubStream) {
		return rtspChildThreadMng.ctfosMapContainsKey(idSubStream);
	}

	public @NonNull ChildThreadsForOneStream getCtfosMapValue(@NonNull RtspProtoIdSubStream idSubStream) {
		return rtspChildThreadMng.getCtfosMapValue(idSubStream);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void startChildThreads() {
		rtspChildThreadMng.startChildThreads(
				rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspProtoMessageType.PLAY).orElseThrow()
			);
	}

	public void pauseOrStopChildThreads(boolean doPause) {
		rtspChildThreadMng.pauseOrStopChildThreads(doPause);
	}

	public void unpauseChildThreads() {
		rtspChildThreadMng.unpauseChildThreads();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized void cbNotifyThreadReady(@NonNull RtspProtoIdSubStream idSubStream) {
		threadReadyStates.put(idSubStream, true);
	}

	@Override
	public synchronized @NonNull Boolean cbThreadMayStartPlayback() {
		boolean areAllReady = true;
		for (RtspProtoIdSubStream tmpIdSs : rtspSessionInfo.getDescrSetupInfoSubStreamIds()) {
			if (! threadReadyStates.getOrDefault(tmpIdSs, false)) {
				areAllReady = false;
				break;
			}
		}
		return areAllReady;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void updateCongestionLevel_oneStream(@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateCongestionLevel_oneStream()";

		if (ctfos.rtpThreadSender == null || ctfos.rtcpThreadSendRecv == null ||
				ctfos.rtpThreadSender.isPaused() || ctfos.rtcpThreadSendRecv.isPaused()) {
			return;
		}
		int currentTcl = ctfos.rtcpThreadSendRecv.getTargetCongestionLevel();
		if (currentTcl == ctfos.rtcpLastTargetCongestionLevel) {
			return;
		}
		if (ctfos.rtcpLastTargetCongestionLevel >= 0) {
			logDebug(FNC_NAME, "ss=" + ctfos.idStreamSource.getIdStr().orElse("-unset-") + ": " +
					"Congestion level changed to: " + currentTcl);
			ctfos.rtpThreadSender.notifyCongestionLevelChange(currentTcl);
		}
		ctfos.rtcpLastTargetCongestionLevel = currentTcl;
	}

	private void updateCongestionLevel() {
		if (rtspSessionInfo.getSessionState() != RtspProtoSessionState.PLAYING) {
			return;
		}
		for (RtspProtoIdSubStream tmpIdSs : rtspSessionInfo.getDescrSetupInfoSubStreamIds()) {
			if (! rtspChildThreadMng.ctfosMapContainsKey(tmpIdSs)) {
				continue;
			}
			ChildThreadsForOneStream ctfos = rtspChildThreadMng.getCtfosMapValue(tmpIdSs);
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter) throws InterruptedException {
		if (loopCounter % 37 == 0) {
			updateCongestionLevel();
		}
		Thread.sleep(50);
		return true;
	}

}
