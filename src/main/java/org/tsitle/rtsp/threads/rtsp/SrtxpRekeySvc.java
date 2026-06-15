package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtp.RtpConstants;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoRequestOutputSvc;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoResponseInputSvc;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspSessionState;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoKmdsStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;

import java.util.Optional;

final class SrtxpRekeySvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspChildThreadMng rtspChildThreadMng;

	private final @NonNull RtspProtoRequestOutputSvc rtspProtoRequestOutputSvc;
	private final @NonNull RtspProtoResponseInputSvc rtspProtoResponseInputSvc;

	SrtxpRekeySvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtspChildThreadMng rtspChildThreadMng,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspAvailableStreamsSvc availableStreamsSvc,
				@NonNull RtspStaticSessionDataSvc staticSessionDataSvc
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtspChildThreadMng = rtspChildThreadMng;

		//
		this.rtspProtoRequestOutputSvc = new RtspProtoRequestOutputSvc(
				logMsgInterface,
				cfgServerNameAndVersion,
				"",  // @TODO make Content-Language configurable
				rtspConfig.getIsDebugPrintRtspSdpSent(),
				rtspConfig.getIsDebugPrintRtspSent(),
				rtspSessionInfo,
				rtxpTcpReadWrite,
				availableStreamsSvc,
				staticSessionDataSvc
			);
		this.rtspProtoResponseInputSvc = new RtspProtoResponseInputSvc(
				logMsgInterface,
				true,
				rtspConfig.getIsDebugPrintRtspRcvd(),
				rtspSessionInfo,
				rtxpTcpReadWrite,
				null,
				null
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void srtxpRekeyInbound() {
		if (rtspSessionInfo.sessionState != RtspSessionState.PLAYING) {
			return;  // we're not ready yet
		}
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			if (ctfos.srtxpInboundRekeyingInProgress) {
				boolean tmpHasBeenCompleted = ctfos.rtcpThreadSendRecv.hasSrtcpInboundRekeyingBeenCompleted();
				if (tmpHasBeenCompleted) {
					ctfos.srtxpInboundRekeyingInProgress = false;
				}
				continue;
			}
			//
			srtxpRekeyInbound_oneStream(ctfos);
		}
	}

	boolean srtxpRekeyOutbound() throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound()";

		if (rtspSessionInfo.sessionState != RtspSessionState.PLAYING) {
			return true;  // we're not ready yet
		}

		// check sub-streams to see whether any of them need re-keying
		boolean needRekey = false;
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			if (ctfos.srtxpOutboundRekeyingInProgress) {
				boolean tmpHasBeenCompleted;
				if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
					tmpHasBeenCompleted = ctfos.rtcpThreadSendRecv.hasSrtcpOutboundRekeyingBeenCompleted();
				} else {
					tmpHasBeenCompleted = true;
				}
				tmpHasBeenCompleted = (tmpHasBeenCompleted && ctfos.rtpThreadSender.hasSrtpOutboundRekeyingBeenCompleted());
				if (tmpHasBeenCompleted) {
					ctfos.srtxpOutboundRekeyingInProgress = false;
				}
				return true;  // we ignore any other sub-stream for now
			}
			//
			long tmpPktCount = ctfos.rtpThreadSender.getPacketCountOutbound();
			if ((long)((double)tmpPktCount * 0.9) < RtpConstants.SRTXP_REKEYING_INTERVAL_PACKETS_INT) {
				continue;
			}
			//
			final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr() + ": ";
			//
			logDebug(FNC_NAME, String.format("%s90%% of maximum outbound RTP packet count reached: %s (RTCP in %s / out %s)",
					logMsgPrefix,
					Long.toUnsignedString(tmpPktCount),
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountInbound()) : "-",
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountOutbound()) : "-"));
			needRekey = true;
		}

		if (! needRekey) {
			return true;
		}

		// generate new KMDs for all sub-streams
		boolean rekeyingProtoIsMikey = true;
		RtspProtoKmdsStream kmdsOutbound = new RtspProtoKmdsStream();
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr() + ": ";
			//
			SrtxpKmd tmpNextKmdOutbound;
			try {
				tmpNextKmdOutbound = rtspProtoRequestOutputSvc.generateNewOutboundKmdForRekeying(ctfos.idSubStream);
			} catch (RtspInvalidRequestException e) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed: " + e.getMessage());
				return false;  // shutdown the session
			}
			kmdsOutbound.putKmdForSubStream(tmpNextKmdOutbound, ctfos.idSubStream);
			rekeyingProtoIsMikey = (rekeyingProtoIsMikey && ! tmpNextKmdOutbound.isForLegacySdes());
		}

		//
		if (rekeyingProtoIsMikey) {
			// send one request per sub-stream
			for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
				if (! srtxpRekeyOutbound_mikey_oneStream(ctfos, kmdsOutbound)) {
					return false;  // shutdown the session
				}
			}
			return true;
		}
		// send one request for all sub-streams together
		return srtxpRekeyOutbound_sdes(kmdsOutbound);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void srtxpRekeyInbound_oneStream(RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyInbound_oneStream()";

		Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = rtspSessionInfo.descrSetupInfosStream
				.getSiBySubStreamId(ctfos.idSubStream);
		if (tmpOptSiSs.isEmpty()) {
			logError(FNC_NAME, "Sub-Stream ID '" + ctfos.idSubStream.getIdStr() + "' not found");
			return;
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = tmpOptSiSs.get();

		if (! tmpSiSs.kmdInboundNext.isKmdSet()) {
			return;  // nothing to do
		}

		//
		final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr() + ": ";
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		ctfos.rtcpThreadSendRecv.setNextSrtcpKmdInbound(tmpSiSs.kmdInboundNext.getKmd().orElseThrow());
		tmpSiSs.kmdInboundNext.clear();
		ctfos.srtxpInboundRekeyingInProgress = true;
	}

	private void srtxpRekeyOutbound_updateThreads(
				RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos,
				@NonNull SrtxpKmd kmd
			) {
		if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
			ctfos.rtcpThreadSendRecv.setNextSrtcpKmdOutbound(kmd);
		}
		if (ctfos.rtpThreadSender != null && ctfos.rtpThreadSender.isRunning()) {
			ctfos.rtpThreadSender.setNextSrtpKmdOutbound(kmd);
		}
		ctfos.srtxpOutboundRekeyingInProgress = true;
	}

	private boolean srtxpRekeyOutbound_mikey_oneStream(
				RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_mikey_oneStream()";

		final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr() + ": ";

		Optional<RtspProtoRscUrl> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_onlySetup(ctfos.idSubStream);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no Resource URL found for Sub-Stream ID: '" +
					ctfos.idSubStream.getIdStr() + "'");
			return false;
		}
		final String resourceUrlForSs = tmpOptRscUrl.get().getUrlStr();

		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_options(resourceUrlForSs);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		Optional<SrtxpKmd> tmpOptNextKmdOutbound = kmdsOutbound.getKmdBySubStreamId(ctfos.idSubStream);
		if (tmpOptNextKmdOutbound.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no KMD found for Sub-Stream ID: '" +
					ctfos.idSubStream.getIdStr() + "'");
			return false;
		}
		final SrtxpKmd tmpNextKmdOutbound = tmpOptNextKmdOutbound.get();

		//
		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundMikey(
					resourceUrlForSs,
					ctfos.idSubStream,
					tmpNextKmdOutbound
				);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode == RtspStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed (" + requStatCode + ")");
				return false;
			}
		}

		//
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		srtxpRekeyOutbound_updateThreads(ctfos, tmpNextKmdOutbound);
		return true;
	}

	private boolean srtxpRekeyOutbound_sdes(
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_sdes()";

		Optional<RtspProtoRscUrl> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspMessageType.PLAY);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, "SRTxP re-keying failed - no Resource URL found");
			return false;
		}
		final String resourceUrl = tmpOptRscUrl.get().getUrlStr();

		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_options(resourceUrl);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundSdes(resourceUrl, kmdsOutbound);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode == RtspStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed (" + requStatCode + ")");
				return false;
			}
		}

		//
		logInfo(FNC_NAME, "SRTxP re-keying in progress");
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			Optional<SrtxpKmd> tmpOptNextKmdOutbound = kmdsOutbound.getKmdBySubStreamId(ctfos.idSubStream);
			if (tmpOptNextKmdOutbound.isEmpty()) {
				logError(FNC_NAME, "SRTxP re-keying failed - no KMD found for Sub-Stream ID: '" +
						ctfos.idSubStream.getIdStr() + "'");
				return false;
			}
			srtxpRekeyOutbound_updateThreads(ctfos, tmpOptNextKmdOutbound.get());
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspStatusCode recvResponseFromClient(@NonNull RtspMessageType requestMessageType)
			throws TcpSocketClosedException, TcpSocketIoException {
		int timeoutCnt = 0;
		RtspResponseBasics respBasics = null;
		while (++timeoutCnt < 100) {
			try {
				respBasics = rtspProtoResponseInputSvc.receiveResponse(requestMessageType);
			} catch (InputStreamNotReadyException ignored) {
				// ignore
			}
		}
		if (respBasics == null) {
			throw new TcpSocketIoException("could not receive response");
		}
		return respBasics.statusCode;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
