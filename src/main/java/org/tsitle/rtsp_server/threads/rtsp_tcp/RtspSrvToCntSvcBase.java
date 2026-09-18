package org.tsitle.rtsp_server.threads.rtsp_tcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoPtrSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoRequestOutputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoResponseInputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSendRequestFailedException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoTcpSocketNotReadyException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsGetRunning;

/**
 * Base class for services that send RTSP requests to the client.
 */
class RtspSrvToCntSvcBase {

	private final @NonNull LogMsgInterface logMsgInterface;
	protected final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;
	protected final @NonNull RtspChildThreadsGetRunning childThreadsGetRunningInterface;

	protected final @NonNull RtspProtoRequestOutputSvc rtspProtoRequestOutputSvc;
	protected final @NonNull RtspProtoResponseInputSvc rtspProtoResponseInputSvc;

	protected boolean haveRequestedOptions = false;
	protected boolean areClientOptionsOk = false;

	protected RtspSrvToCntSvcBase(
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

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected boolean requestOptionsFromClient(
				@NonNull String fncName,
				@NonNull String errMsgPfx,
				@NonNull String resourceUrl,
				boolean outputErrMsg
			) throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		haveRequestedOptions = true;

		try {
			rtspProtoRequestOutputSvc.sendRequest_options(resourceUrl);
		} catch (RtspProtoSendRequestFailedException e) {
			logError(fncName, "RtspProtoSendRequestFailedException caught: " + e.getMessage());
			return false;
		}
		RtspProtoStatusCode requStatCode;
		try {
			requStatCode = recvResponseFromClient();
		} catch (TcpSocketIoException e) {
			logDebug(fncName, "ignoring TcpSocketIoException: " + e.getMessage());
			return false;
		}
		if (requStatCode != RtspProtoStatusCode.OK) {
			String tmpLogMsg = errMsgPfx + " - client does not support OPTIONS request";
			if (outputErrMsg) {
				logError(fncName, tmpLogMsg);
			} else {
				logDebug(fncName, tmpLogMsg);
			}
			return false;
		}
		areClientOptionsOk = true;
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull RtspProtoStatusCode recvResponseFromClient()
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

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	protected void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}

	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
