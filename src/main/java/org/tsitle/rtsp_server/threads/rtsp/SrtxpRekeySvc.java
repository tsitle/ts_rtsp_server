package org.tsitle.rtsp_server.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamNotReadyException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoRequestOutputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoResponseInputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;

import java.util.Optional;

/**
 * Re-keying service for SRTP and SRTCP encryption.<br />
 * <br />
 * From <a href="https://datatracker.ietf.org/doc/html/rfc4568#section-6.2.1">RFC-4568 Section 6.2.1</a>:<br />
 * <br />
 * SRTP allows 2^48 SRTP packets or 2^31 SRTCP packets, whichever comes first.<br />
 * However, it is RECOMMENDED that automated key management allows easy and efficient
 * rekeying at intervals far smaller than 2^31 packets given today's media rates or even HDTV media rates.
 */
final class SrtxpRekeySvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtspChildThreadMng rtspChildThreadMng;

	private final @NonNull RtspProtoRequestOutputSvc rtspProtoRequestOutputSvc;
	private final @NonNull RtspProtoResponseInputSvc rtspProtoResponseInputSvc;

	SrtxpRekeySvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspChildThreadMng rtspChildThreadMng,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspAvailableStreamsSvc availableStreamsSvc,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtspChildThreadMng = rtspChildThreadMng;

		//
		this.rtspProtoRequestOutputSvc = new RtspProtoRequestOutputSvc(
				logMsgInterface,
				false,
				cfgServerNameAndVersion,
				"",  // @TODO make Content-Language configurable
				rtspConfig.getIsDebugPrintRtspSdpSent(),
				rtspConfig.getIsDebugPrintRtspSent(),
				rtspSessionInfo,
				rtxpTcpReadWrite,
				availableStreamsSvc,
				globalSessionInfoInterface
			);
		this.rtspProtoResponseInputSvc = new RtspProtoResponseInputSvc(
				logMsgInterface,
				true,
				rtspConfig.getIsDebugPrintRtspRcvd(),
				rtspSessionInfo,
				rtxpTcpReadWrite
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void srtxpRekeyInbound() {
		if (rtspSessionInfo.getSessionState() != RtspProtoSessionState.PLAYING) {
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

		if (rtspSessionInfo.getSessionState() != RtspProtoSessionState.PLAYING) {
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
			final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr().orElse("-unset-") + ": ";
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
			final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr().orElse("-unset-") + ": ";
			//
			SrtxpKmd tmpNextKmdOutbound;
			try {
				tmpNextKmdOutbound = rtspProtoRequestOutputSvc.generateNewOutboundKmdForRekeying(ctfos.idSubStream);
			} catch (RtspProtoInvalidRequestException e) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed: " + e.getMessage());
				return false;  // shutdown the session
			}
			kmdsOutbound.putKmdForSubStream(tmpNextKmdOutbound, ctfos.idSubStream);
			rekeyingProtoIsMikey = (rekeyingProtoIsMikey && ! tmpNextKmdOutbound.getMetaIsForLegacySdes());
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

		Optional<SrtxpKmd> tmpNextKmdInbound = rtspSessionInfo.getDescrSetupInfoNextKmdInboundForSubStreamId(ctfos.idSubStream);
		if (tmpNextKmdInbound.isEmpty()) {
			return;  // nothing to do
		}

		//
		final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr().orElse("-unset-") + ": ";
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		ctfos.rtcpThreadSendRecv.setNextSrtcpKmdInbound(tmpNextKmdInbound.orElseThrow());

		rtspSessionInfo.clearDescrSetupInfoNextKmdInboundForSubStreamId(ctfos.idSubStream);

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

		final String logMsgPrefix = "ss=" + ctfos.idStreamSource.getIdStr().orElse("-unset-") + ": ";

		Optional<RtspProtoRscUrl> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_onlySetup(ctfos.idSubStream);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no Resource URL found for Sub-Stream ID: '" +
					ctfos.idSubStream.getIdStr().orElse("-unset-") + "'");
			return false;
		}
		final String resourceUrlForSs = tmpOptRscUrl.get().getUrlStr();

		{
			rtspProtoRequestOutputSvc.sendRequest_options(resourceUrlForSs);
			RtspProtoStatusCode requStatCode = recvResponseFromClient();
			if (requStatCode != RtspProtoStatusCode.OK) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		Optional<SrtxpKmd> tmpOptNextKmdOutbound = kmdsOutbound.getKmdBySubStreamId(ctfos.idSubStream);
		if (tmpOptNextKmdOutbound.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no KMD found for Sub-Stream ID: '" +
					ctfos.idSubStream.getIdStr().orElse("-unset-") + "'");
			return false;
		}
		final SrtxpKmd tmpNextKmdOutbound = tmpOptNextKmdOutbound.get();

		//
		{
			rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundMikey(
					resourceUrlForSs,
					ctfos.idSubStream,
					tmpNextKmdOutbound
				);
			RtspProtoStatusCode requStatCode = recvResponseFromClient();
			if (requStatCode == RtspProtoStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspProtoStatusCode.OK) {
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

		Optional<RtspProtoRscUrl> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspProtoMessageType.PLAY);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, "SRTxP re-keying failed - no Resource URL found");
			return false;
		}
		final String resourceUrl = tmpOptRscUrl.get().getUrlStr();

		{
			rtspProtoRequestOutputSvc.sendRequest_options(resourceUrl);
			RtspProtoStatusCode requStatCode = recvResponseFromClient();
			if (requStatCode != RtspProtoStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		{
			rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundSdes(resourceUrl, kmdsOutbound);
			RtspProtoStatusCode requStatCode = recvResponseFromClient();
			if (requStatCode == RtspProtoStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspProtoStatusCode.OK) {
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
						ctfos.idSubStream.getIdStr().orElse("-unset-") + "'");
				return false;
			}
			srtxpRekeyOutbound_updateThreads(ctfos, tmpOptNextKmdOutbound.get());
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoStatusCode recvResponseFromClient() throws TcpSocketClosedException, TcpSocketIoException {
		int timeoutCnt = 0;
		RtspResponseBasics respBasics = null;
		while (++timeoutCnt < 100) {
			try {
				respBasics = rtspProtoResponseInputSvc.receiveResponse();
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
