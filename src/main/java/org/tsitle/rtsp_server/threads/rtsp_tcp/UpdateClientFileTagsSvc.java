package org.tsitle.rtsp_server.threads.rtsp_tcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoPtrSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSendRequestFailedException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp_server.availstreams.AsGetFileTagsInterface;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsGetRunning;

import java.util.Optional;

/**
 * Service for sending 'File Tags' to the client.
 */
final class UpdateClientFileTagsSvc extends RtspSrvToCntSvcBase {

	private final @NonNull AsGetFileTagsInterface asGetFileTagsInterface;

	private final RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	private @NonNull String rscUrlForMainStream = "";
	private @NonNull String lastFileTagsHash = "";

	UpdateClientFileTagsSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull String cfgContentLanguage,
				@NonNull RtspProtoPtrSessionInfo sessionInfoPtr,
				@NonNull RtspChildThreadsGetRunning childThreadsGetRunningInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull AsGetFileTagsInterface asGetFileTagsInterface
			) {
		super(
				logMsgInterface,
				rtspSrvConfig,
				cfgServerNameAndVersion,
				cfgContentLanguage,
				sessionInfoPtr,
				childThreadsGetRunningInterface,
				rtxpTcpReadWrite,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

		//
		this.asGetFileTagsInterface = asGetFileTagsInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void updateClient() throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".updateClient()";

		if (sessionInfoPtr.ptr().getSessionState() != RtspProtoSessionState.PLAYING) {
			return;  // we're not ready yet
		}

		if (idInputSource.isEmpty()) {
			Optional<RtspProtoRscUrl> tmpOptRscUrl = sessionInfoPtr.ptr().getLastRequestResourceUrl_mainStream();
			if (tmpOptRscUrl.isEmpty()) {
				logError(FNC_NAME, "could not update client - no Resource URL found");
				return;
			}
			idInputSource.copyFrom(tmpOptRscUrl.get().idInputSource);

			rscUrlForMainStream = tmpOptRscUrl.get().getUrlStr();
		}

		Optional<String> tmpOptHash = asGetFileTagsInterface.getFileTagsHash(idInputSource);
		if (tmpOptHash.isEmpty()) {
			return;
		}
		String tmpCurHash = tmpOptHash.get();
		if (lastFileTagsHash.equals(tmpCurHash)) {
			return;  // no change
		}
		lastFileTagsHash = tmpCurHash;

		String tmpTags = asGetFileTagsInterface.getFileTagsValue(idInputSource).orElse("");

		if (! haveRequestedOptions) {
			if (! requestOptionsFromClient(FNC_NAME, "could not update client", rscUrlForMainStream, false)) {
				return;
			}
			if (! sessionInfoPtr.ptr().getRhSupportedMessageTypes().getMts().contains(RtspProtoMessageType.SET_PARAMETER)) {
				areClientOptionsOk = false;
			}
		}
		if (! areClientOptionsOk) {
			return;
		}

		RtspProtoRscUrl tmpRscUrl = sessionInfoPtr.ptr().getLastRequestResourceUrl_mainStream().orElseThrow();
		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry(RtspParamGetterSetterSvc.PARAM_KEY_FILETAGS, tmpTags);
		try {
			rtspProtoRequestOutputSvc.sendRequest_setParameter(tmpRscUrl, setParamKvs);
		} catch (RtspProtoSendRequestFailedException e) {
			logError(FNC_NAME, "RtspProtoSendRequestFailedException caught: " + e.getMessage());
			return;
		}

		try {
			recvResponseFromClient();
		} catch (TcpSocketIoException e) {
			logDebug(FNC_NAME, "ignoring TcpSocketIoException: " + e.getMessage());
		}
	}

}
