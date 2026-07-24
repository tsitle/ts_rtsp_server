package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;
import org.tsitle.rtsp_server.threads.rtcp.RtcpReceivedByeInterface;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ThreadRtspPlay extends RunnableBase
		implements RtspChildThreadsCbNotifyThreadReadyInterface, RtspChildThreadsCbRtcpFromRtpInterface,
				RtspChildThreadsGetRunning, RtcpReceivedByeInterface {

	private final @NonNull String threadName;

	private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;

	private final @NonNull RtspChildThreadMng rtspChildThreadMng;
	private final Map<@NonNull RtspProtoIdXsrc, @NonNull ChildThreadsForOneStream> cacheChildThreadsPerSsrcMap = new HashMap<>();
	/** Track 'Thread-Is-Ready-For-Playback' states per Sub-Stream */
	private final @NonNull Map<@NonNull RtspProtoIdSubStream, @NonNull Boolean> threadReadyStates = new ConcurrentHashMap<>();

	private final ReadWriteLock theLockScthbr = new ReentrantReadWriteLock();
	private final Lock theWriteLockScthbr = theLockScthbr.writeLock();
	private boolean startChildThreadsHasBeenRequested = false;

	private final @NonNull CancelToken localCancelToken = new CancelToken();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspConfig RTSP configuration
	 * @param rtspSessionInfo Session info
	 * @param rctcbRtpTcp Callback interface for RTSP child threads
	 * @param availableStreamsInterface Available streams instance
	 * @param globalSessionInfoInterface Global Session Info service
	 */
	public ThreadRtspPlay(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspChildThreadsCbRtxpTcpInterface rctcbRtpTcp,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		super(logMsgInterface, cancelToken);

		this.sessionInfoPtr = RtspProtoPtrSessionInfo.ofPointer(rtspSessionInfo);

		//
		this.threadName = "RTSP_PLAY#sid" + rtspSessionInfo.getIdSession().getIdStr().orElseThrow();

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
				subStreamIds,
				rtspSessionInfo.getIdSession(),
				rtspSessionInfo.getClientIpAddr(),
				setupInfoPerSsMap,
				this,
				rctcbRtpTcp,
				this,
				this,
				availableStreamsInterface,
				globalSessionInfoInterface
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
		logDebug(FNC_NAME, "Playing RTP");

		//
		try {
			int loopCounter = 0;
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
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
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			rtspChildThreadMng.pauseOrStopChildThreads(false);
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public void stopThread() {
		localCancelToken.cancelled = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void updateSessionInfo(@NonNull RtspProtoSessionInfo rtspSessionInfo) {
		sessionInfoPtr.updatePtr(rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized void cbSendRtcpSrPacketFromRtp(@NonNull RtspProtoIdXsrc ssrcId, @NonNull BufferExt rtcpPacketsBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbSendRtcpSrPacketFromRtp()";

		ChildThreadsForOneStream ctfosToUse = findCtfosBySsrc(FNC_NAME, ssrcId);
		if (ctfosToUse.rtcpThreadSendRecv != null &&
				! ctfosToUse.rtcpThreadSendRecv.hasBeenRequestedToStop() &&
				ctfosToUse.rtcpThreadSendRecv.isRunning()) {
			ctfosToUse.rtcpThreadSendRecv.appendToSendQueue(rtcpPacketsBuf);
		}
	}

	@Override
	public synchronized void cbSendRtcpByePacketFromRtp(@NonNull RtspProtoIdXsrc ssrcId) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbSendRtcpByePacketFromRtp()";

		ChildThreadsForOneStream ctfosToUse = findCtfosBySsrc(FNC_NAME, ssrcId);
		if (ctfosToUse.rtcpThreadSendRecv != null &&
				! ctfosToUse.rtcpThreadSendRecv.hasBeenRequestedToStop() &&
				ctfosToUse.rtcpThreadSendRecv.isRunning()) {
			ctfosToUse.rtcpThreadSendRecv.appendByePacketToSendQueue();
		}
	}

	@Override
	public synchronized void cbRtcpReceivedBye(@NonNull RtspProtoIdXsrc ssrcId) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtcpReceivedBye()";

		ChildThreadsForOneStream ctfosToUse = findCtfosBySsrc(FNC_NAME, ssrcId);
		if (ctfosToUse.rtpThreadSender != null &&
				! ctfosToUse.rtpThreadSender.hasBeenRequestedToStop() &&
				ctfosToUse.rtpThreadSender.isRunning()) {
			ctfosToUse.rtpThreadSender.stopAsap();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesOnlyRunning() {
		return rtspChildThreadMng.getCtfosMapValuesOnlyRunning();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void startChildThreads() {
		theWriteLockScthbr.lock();
		try {
			startChildThreadsHasBeenRequested = true;
		} finally {
			theWriteLockScthbr.unlock();
		}
	}

	public void pauseOrStopChildThreads(boolean doPause) {
		rtspChildThreadMng.pauseOrStopChildThreads(doPause);
	}

	public void unpauseChildThreads() {
		rtspChildThreadMng.unpauseChildThreads();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean seekStream(double targetTimestamp) {
		return rtspChildThreadMng.seekStream(targetTimestamp);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized void cbNotifyThreadReady(@NonNull RtspProtoIdSubStream idSubStream) {
		threadReadyStates.put(idSubStream, true);
	}

	@Override
	public synchronized @NonNull Boolean cbThreadMayStartPlayback() {
		boolean areAllReady = true;
		for (RtspProtoIdSubStream tmpIdSs : sessionInfoPtr.ptr().getDescrSetupInfoSubStreamIds()) {
			if (! threadReadyStates.getOrDefault(tmpIdSs, false)) {
				areAllReady = false;
				break;
			}
		}
		return areAllReady;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean getIsTransportUdp() {
		return sessionInfoPtr.ptr().getIsTransportUdp();
	}

	public long getLastIncomingRtspRequestTimeDeltaSeconds() {
		return sessionInfoPtr.ptr().getLastIncomingRequestTimeDeltaSeconds();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean ctfosMapContainsKey(@NonNull RtspProtoIdSubStream idSubStream) {
		return rtspChildThreadMng.ctfosMapContainsKey(idSubStream);
	}

	private @NonNull ChildThreadsForOneStream getCtfosMapValue(@NonNull RtspProtoIdSubStream idSubStream) {
		return rtspChildThreadMng.getCtfosMapValue(idSubStream);
	}

	private @NonNull ChildThreadsForOneStream findCtfosBySsrc(@NonNull String fncName, @NonNull RtspProtoIdXsrc ssrcId) {
		ChildThreadsForOneStream ctfosToUse = null;
		if (cacheChildThreadsPerSsrcMap.containsKey(ssrcId)) {
			ctfosToUse = cacheChildThreadsPerSsrcMap.get(ssrcId);
		} else {
			for (RtspProtoRscUrl tmpRscUrl : sessionInfoPtr.ptr().getDescrSetupInfoRscUrls()) {
				if (! ctfosMapContainsKey(tmpRscUrl.idSubStream)) {
					continue;
				}
				try {
					if (! sessionInfoPtr.ptr().getDescrSetupInfoSsrcOutboundBySubStreamsId(tmpRscUrl.idSubStream).equals(ssrcId)) {
						continue;
					}
				} catch (RtspProtoSessionInfoException e) {
					continue;
				}
				ctfosToUse = getCtfosMapValue(tmpRscUrl.idSubStream);
				cacheChildThreadsPerSsrcMap.put(ssrcId.clone(), ctfosToUse);
				break;
			}
		}
		if (ctfosToUse == null) {
			throw new IllegalStateException(fncName + ": No stream found for ssrcId: " + ssrcId);
		}
		return ctfosToUse;
	}

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
			logDebug(FNC_NAME, "esSrc=" + ctfos.idEsSource.getIdStr().orElse("-unset-") + ": " +
					"Congestion level changed to: " + currentTcl);
			ctfos.rtpThreadSender.notifyCongestionLevelChange(currentTcl);
		}
		ctfos.rtcpLastTargetCongestionLevel = currentTcl;
	}

	private void updateCongestionLevel() {
		if (sessionInfoPtr.ptr().getSessionState() != RtspProtoSessionState.PLAYING) {
			return;
		}
		for (RtspProtoIdSubStream tmpIdSs : sessionInfoPtr.ptr().getDescrSetupInfoSubStreamIds()) {
			if (! rtspChildThreadMng.ctfosMapContainsKey(tmpIdSs)) {
				continue;
			}
			ChildThreadsForOneStream ctfos = rtspChildThreadMng.getCtfosMapValue(tmpIdSs);
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter) throws InterruptedException {
		theWriteLockScthbr.lock();
		try {
			if (startChildThreadsHasBeenRequested) {
				rtspChildThreadMng.startChildThreads(
						sessionInfoPtr.ptr().getLastRequestResourceUrl_mainStream().orElseThrow()
					);
				startChildThreadsHasBeenRequested = false;
			}
		} finally {
			theWriteLockScthbr.unlock();
		}

		if (loopCounter % 37 == 0) {
			updateCongestionLevel();
		}

		//
		Thread.sleep(50);

		return true;
	}

}
