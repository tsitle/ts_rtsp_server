package org.tsitle.rtsp_server.threads.rtsp_tcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSendRequestFailedException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoTcpSocketNotReadyException;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp_server.threads.rtsp_play.ChildThreadsForOneStream;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsGetRunning;

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
	private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;
	private final @NonNull RtspChildThreadsGetRunning childThreadsGetRunningInterface;

	private final @NonNull RtspProtoRequestOutputSvc rtspProtoRequestOutputSvc;
	private final @NonNull RtspProtoResponseInputSvc rtspProtoResponseInputSvc;

	SrtxpRekeySvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull String cfgContentLanguage,
				@NonNull RtspProtoPtrSessionInfo sessionInfoPtr,
				@NonNull RtspChildThreadsGetRunning childThreadsGetRunningInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.sessionInfoPtr = sessionInfoPtr;
		this.childThreadsGetRunningInterface = childThreadsGetRunningInterface;

		//
		this.rtspProtoRequestOutputSvc = new RtspProtoRequestOutputSvc(
				logMsgInterface,
				false,
				cfgServerNameAndVersion,
				RtspConnectionPolicy.KEEPALIVE,
				cfgContentLanguage,
				rtspSrvConfig.getIsDebugPrintRtspSdpSent(),
				rtspSrvConfig.getIsDebugPrintRtspSent(),
				sessionInfoPtr,
				rtxpTcpReadWrite,
				availableStreamsInterface,
				globalSessionInfoInterface
			);
		this.rtspProtoResponseInputSvc = new RtspProtoResponseInputSvc(
				logMsgInterface,
				true,
				rtspSrvConfig.getIsDebugPrintRtspRcvd(),
				sessionInfoPtr,
				rtxpTcpReadWrite
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void srtxpRekeyInbound() {
		if (sessionInfoPtr.ptr().getSessionState() != RtspProtoSessionState.PLAYING) {
			return;  // we're not ready yet
		}
		for (ChildThreadsForOneStream ctfos : childThreadsGetRunningInterface.getCtfosMapValuesOnlyRunning()) {
			if (ctfos.rtcpThreadSendRecv == null) {
				continue;
			}
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

	boolean srtxpRekeyOutbound() throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound()";

		if (sessionInfoPtr.ptr().getSessionState() != RtspProtoSessionState.PLAYING) {
			return true;  // we're not ready yet
		}

		// check sub-streams to see whether any of them need re-keying
		boolean needRekey = false;
		for (ChildThreadsForOneStream ctfos : childThreadsGetRunningInterface.getCtfosMapValuesOnlyRunning()) {
			if (ctfos.rtpThreadSender == null) {
				continue;
			}
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
			final String logMsgPrefix = "esSrc=" + ctfos.idEsSource.getIdStr().orElse("-unset-") + ": ";
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
		for (ChildThreadsForOneStream ctfos : childThreadsGetRunningInterface.getCtfosMapValuesOnlyRunning()) {
			final String logMsgPrefix = "esSrc=" + ctfos.idEsSource.getIdStr().orElse("-unset-") + ": ";
			//
			SrtxpKmd tmpNextKmdOutbound;
			try {
				tmpNextKmdOutbound = RtspProtoRequestOutputSvc.generateNewOutboundKmdForRekeying(
						sessionInfoPtr.ptr(),
						ctfos.idSubStream
					);
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
			for (ChildThreadsForOneStream ctfos : childThreadsGetRunningInterface.getCtfosMapValuesOnlyRunning()) {
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

	private void srtxpRekeyInbound_oneStream(@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyInbound_oneStream()";

		Optional<SrtxpKmd> tmpNextKmdInbound = sessionInfoPtr.ptr().getDescrSetupInfoNextKmdInboundForSubStreamId(ctfos.idSubStream);
		if (tmpNextKmdInbound.isEmpty()) {
			return;  // nothing to do
		}

		//
		if (ctfos.rtcpThreadSendRecv != null) {
			final String logMsgPrefix = "esSrc=" + ctfos.idEsSource.getIdStr().orElse("-unset-") + ": ";
			logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
			ctfos.rtcpThreadSendRecv.setNextSrtcpKmdInbound(tmpNextKmdInbound.orElseThrow());
		}

		sessionInfoPtr.ptr().clearDescrSetupInfoNextKmdInboundForSubStreamId(ctfos.idSubStream);

		ctfos.srtxpInboundRekeyingInProgress = true;
	}

	private void srtxpRekeyOutbound_updateThreads(
				@NonNull ChildThreadsForOneStream ctfos,
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
				@NonNull ChildThreadsForOneStream ctfos,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_mikey_oneStream()";

		final String logMsgPrefix = "esSrc=" + ctfos.idEsSource.getIdStr().orElse("-unset-") + ": ";

		Optional<RtspProtoRscUrl> tmpOptRscUrl = sessionInfoPtr.ptr().getRequestResourceUrl_subStream(ctfos.idSubStream);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no Resource URL found for Sub-Stream ID: '" +
					ctfos.idSubStream.getIdStr().orElse("-unset-") + "'");
			return false;
		}
		RtspProtoRscUrl tmpRscUrl = tmpOptRscUrl.get();
		final String resourceUrlForSs = tmpRscUrl.getUrlStr();
		if (! tmpRscUrl.idSubStream.equals(ctfos.idSubStream)) {
			logError(FNC_NAME, logMsgPrefix + "Resource URL object contains invalid Sub-Stream ID");
			return false;
		}

		{
			try {
				rtspProtoRequestOutputSvc.sendRequest_options(resourceUrlForSs);
			} catch (RtspProtoSendRequestFailedException e) {
				logError(FNC_NAME, logMsgPrefix + "RtspProtoSendRequestFailedException caught: " + e.getMessage());
				return false;
			}
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
			try {
				rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundMikey(tmpRscUrl, tmpNextKmdOutbound);
			} catch (RtspProtoSendRequestFailedException e) {
				logError(FNC_NAME, logMsgPrefix + "RtspProtoSendRequestFailedException caught: " + e.getMessage());
				return false;
			}
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

	private boolean srtxpRekeyOutbound_sdes(@NonNull RtspProtoKmdsStream kmdsOutbound)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_sdes()";

		Optional<RtspProtoRscUrl> tmpOptRscUrl = sessionInfoPtr.ptr().getLastRequestResourceUrl_mainStream();
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, "SRTxP re-keying failed - no Resource URL found");
			return false;
		}
		final String resourceUrl = tmpOptRscUrl.get().getUrlStr();

		{
			try {
				rtspProtoRequestOutputSvc.sendRequest_options(resourceUrl);
			} catch (RtspProtoSendRequestFailedException e) {
				logError(FNC_NAME, "RtspProtoSendRequestFailedException caught: " + e.getMessage());
				return false;
			}
			RtspProtoStatusCode requStatCode = recvResponseFromClient();
			if (requStatCode != RtspProtoStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		{
			try {
				rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundSdes(resourceUrl, kmdsOutbound);
			} catch (RtspProtoSendRequestFailedException e) {
				logError(FNC_NAME, "RtspProtoSendRequestFailedException caught: " + e.getMessage());
				return false;
			}
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
		for (ChildThreadsForOneStream ctfos : childThreadsGetRunningInterface.getCtfosMapValuesOnlyRunning()) {
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

	private @NonNull RtspProtoStatusCode recvResponseFromClient()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		int timeoutCnt = 0;
		RtspResponseBasics respBasics = null;
		while (++timeoutCnt < 100) {
			try {
				respBasics = rtspProtoResponseInputSvc.receiveResponse();
			} catch (RtspProtoTcpSocketNotReadyException ignored) {
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
