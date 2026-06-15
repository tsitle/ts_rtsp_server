package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.*;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoSessionInfoException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestProducer;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestProducer;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoClientCredentials;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoKmdsStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpProducer;

public final class RtspProtoRequestOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoHighRequestProducer rtspProtoHighRequestProducer;
	private final RtspProtoLowRequestProducer rtspProtoLowRequestProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	public RtspProtoRequestOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull String cfgServerNameAndVersion,
				@NonNull String cfgContentLanguage,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugPrintRtspSent,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpProducer sdpProducer = new RtspProtoSdpProducer(
				cfgServerNameAndVersion,
				cfgContentLanguage,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

		//
		this.rtspProtoHighRequestProducer = new RtspProtoHighRequestProducer(
				logMsgInterface,
				cfgIsDebugPrintRtspSdpSent,
				sdpProducer
			);
		this.rtspProtoLowRequestProducer = new RtspProtoLowRequestProducer(logMsgInterface);
		this.rtspProtoLowMsgWriter = new RtspProtoLowMsgWriter(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				cfgIsDebugPrintRtspSent
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoMessageType sendRequest_announce(
				@NonNull String resourceUrl,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoDataCntSdp announceSdp
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_announce()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requAnnouncedSdp.copyFrom(announceSdp);
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requRscUrl.idInputSource.copyFrom(idInputSource);

		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.ANNOUNCE,
				dummyClientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspProtoMessageType sendRequest_describe(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_describe()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.DESCRIBE, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.GET_PARAMETER, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamNames getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requGetParamNames.copyFrom(getParameterNames);
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.GET_PARAMETER,
				clientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspProtoMessageType sendRequest_options(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		return sendRequest_options(resourceUrl, dummyClientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_options()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.OPTIONS, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_pause(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_pause()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.PAUSE, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_play(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_play()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.PLAY, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_redirect(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_redirect()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.REDIRECT, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_setParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamKvs setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requSetParamValues.copyFrom(setParameterKvs);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.SET_PARAMETER,
				clientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspProtoMessageType sendRequest_setup(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setup()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.SETUP, resourceUrl, clientCredentials);
	}

	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundMikey(
				@NonNull String resourceUrlForSubStream,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull SrtxpKmd kmdOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundMikey()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrlForSubStream);

		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		if (kmdOutbound.isForLegacySdes()) {
			throw new IllegalArgumentException("KMD cannot be for legacy SDES");
		}

		RtspProtoKmdsStream tmpKmdsOutbound = new RtspProtoKmdsStream();
		tmpKmdsOutbound.putKmdForSubStream(kmdOutbound, idSubStream);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.SET_PARAMETER,
				dummyClientCredentials,
				idSubStream,
				inputDataRequ,
				tmpKmdsOutbound
			);
	}

	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundSdes(
				@NonNull String resourceUrl,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundSdes()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);

		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		if (kmdsOutbound.getNumberOfSubStreams() == 0) {
			throw new IllegalArgumentException("Need KMD for at least one sub-stream");
		}
		if (! kmdsOutbound.getAreKmdsForLegacySdes().orElse(false)) {
			throw new IllegalArgumentException("KMDs must be for legacy SDES");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.ANNOUNCE,
				dummyClientCredentials,
				null,
				inputDataRequ,
				kmdsOutbound
			);
	}

	public @NonNull RtspProtoMessageType sendRequest_teardown(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_teardown()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.TEARDOWN, resourceUrl, clientCredentials);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate new outbound Key Management Data (KMD) for re-keying.<br />
	 * <b>Note:</b> This requires that the supported methods of the remote host have already been queried
	 * and that there already exists a valid KMD for the specified sub-stream.
	 * @param idSubStream Sub-Stream identifier
	 * @return Key Management Data (either for MIKEY or SDES)
	 * @throws RtspProtoInvalidRequestException If the remote host does not support re-keying or no previous KMD exists
	 */
	public @NonNull SrtxpKmd generateNewOutboundKmdForRekeying(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".generateNewOutboundKmdForRekeying()";

		RtspProtoSetupInfoForSubStream tmpSiSs;
		try {
			tmpSiSs = rtspSessionInfo.getDescrSetupInfoBySubStreamsId(idSubStream);
		} catch (RtspProtoSessionInfoException e) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": No Sub-Stream Info found for " +
					"Sub-Stream ID '" + idSubStream.getIdStr() + "'");
		}

		// get the SrtxpKmd object
		if (! tmpSiSs.getKmdOutboundPtr().isKmdSet()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream ID '" + idSubStream.getIdStr() + "' " +
					"has no previous outbound KMD");
		}
		SrtxpKmd tmpSrtxpKmd = tmpSiSs.getKmdOutboundPtr().getKmd().orElseThrow();

		//
		RtspProtoDataCntMessageTypes tmpRhSuppMts = rtspSessionInfo.getRhSupportedMessageTypes();
		if ((tmpSrtxpKmd.isForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.ANNOUNCE)) ||
				(! tmpSrtxpKmd.isForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.SET_PARAMETER))) {
			throw new RtspProtoInvalidRequestException("remote host does not support SRTxP re-keying");
		}
		if (tmpSrtxpKmd.mki().isEmpty()) {
			throw new RtspProtoInvalidRequestException("cannot re-key when initial KMD had no MKI");
		}

		// generate the new Key Management Data
		if (! tmpSrtxpKmd.isForLegacySdes()) {
			final long nextMki = tmpSrtxpKmd.mki().value() + 1;  // will automatically be wrapped around
			return SrtxpKmd.createWithDefaults(nextMki, tmpSrtxpKmd.ssrcId());
		}
		return SrtxpKmd.createForLegacySdes(tmpSrtxpKmd.ssrcId());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		return internalSendRequest(
				fncName,
				requestMessageType,
				clientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	private @NonNull RtspProtoMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@Nullable RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		inputDataRequ.writeProtect();

		if (requestMessageType == RtspProtoMessageType.ANNOUNCE && inputDataRequ.requRscUrl.idInputSource.isEmpty()) {
			logError(fncName, "Input Source not found");
			return requestMessageType;
		}

		//
		RtspProtoDataRequest tmpInputDataRequCopy = new RtspProtoDataRequest(inputDataRequ);

		// load data from Session Info
		if (! loadFromSessionInfo(fncName, requestMessageType, clientCredentials, tmpInputDataRequCopy)) {
			return requestMessageType;
		}

		//
		tmpInputDataRequCopy.writeProtect();

		// build the outgoing message
		RtspProtoHighMsgStructuredRequest msgStructured;
		try {
			msgStructured = rtspProtoHighRequestProducer.buildRequest(
					requestMessageType,
					idSubStream,
					tmpInputDataRequCopy,
					kmdsOutbound
				);
		} catch (RtspProtoInvalidRequestException e) {
			logError(fncName, "Failed to build HL request: " + e.getMessage());
			return requestMessageType;
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowRequestProducer.buildMessage(msgStructured);
		} catch (RtspProtoInvalidRequestException e) {
			logError(fncName, "Failed to build LL request: " + e.getMessage());
			return requestMessageType;
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(fncName, String.format("Sent request '%s' to remote host (<%s>, CSeq=%s)\n",
				msgStructured.messageType,
				tmpInputDataRequCopy.requIdSession.isEmpty() ? "-" : tmpInputDataRequCopy.requIdSession.getIdStr(),
				msgStructured.getHeaderCseq().isPresent() ? msgStructured.getHeaderCseq().get() + "" : "-"));

		// update data in Session Info
		updateSessionInfo(tmpInputDataRequCopy);

		//
		return requestMessageType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean loadFromSessionInfo(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataRequest dataRequ
			) {
		dataRequ.requIdSession.copyFrom(rtspSessionInfo.getIdSession());
		dataRequ.setRtspProtoVersionToUse(rtspSessionInfo.getRtspProtoVersionToUse());
		dataRequ.copyAndIncrementCseqNrToSend(rtspSessionInfo.getCseqNr_requToRem_lastSent());
		dataRequ.setClientUa(rtspSessionInfo.getClientUserAgent());
		if (! rtspSessionInfo.getClientIpAddr().isEmpty()) {
			dataRequ.requClientIpAddr.copyFrom(rtspSessionInfo.getClientIpAddr());
		}

		if (requestMessageType == RtspProtoMessageType.ANNOUNCE) {
			try {
				dataRequ.requServerIpFromRscUrl.copyFrom(
						RtspProtoSessionInfo.findRtspIpFromResourceUrl(dataRequ.requRscUrl)
					);
			} catch (RtspProtoCannotFindIpFromRscUrlException e) {
				logError(fncName, e.getMessage());
				return false;
			}
		}

		// authentication parameters
		dataRequ.requAuthClient.setAuthUser(clientCredentials.authUser);
		dataRequ.requAuthClient.setAuthPlainPassword(clientCredentials.authPlainPassword);
		dataRequ.requAuthClient.setAuthRealm(rtspSessionInfo.getPermAuthServer().getAuthRealm());
		dataRequ.requAuthClient.setAuthNonce(rtspSessionInfo.getPermAuthServer().getAuthNonce());

		// main transport parameters
		dataRequ.requStreamTpMain.copyFrom(rtspSessionInfo.getStreamTpMain());

		return true;
	}

	private void updateSessionInfo(@NonNull RtspProtoDataRequest dataRequ) {
		rtspSessionInfo.setCseqNr_requToRem_lastSent(dataRequ.getCseqNrToSend());
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
