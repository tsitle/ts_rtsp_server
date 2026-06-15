package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamNames;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestProducer;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestProducer;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoKmdsStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpProducer;

import java.util.Optional;

public final class RtspProtoRequestOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
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
				@NonNull RtspSessionInfo rtspSessionInfo,
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

	public @NonNull RtspMessageType sendRequest_announce(
				@NonNull String resourceUrl,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoDataCntSdp announceSdp
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_announce()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requAnnouncedSdp.copyFrom(announceSdp);
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requRscUrl.idInputSource.copyFrom(idInputSource);

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.ANNOUNCE,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspMessageType sendRequest_describe(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_describe()";

		return internalSendRequest(FNC_NAME, RtspMessageType.DESCRIBE, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_getParameter(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		return internalSendRequest(FNC_NAME, RtspMessageType.GET_PARAMETER, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoDataCntGetSetParamNames getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requGetParamNames.copyFrom(getParameterNames);
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.GET_PARAMETER,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspMessageType sendRequest_options(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_options()";

		return internalSendRequest(FNC_NAME, RtspMessageType.OPTIONS, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_pause(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_pause()";

		return internalSendRequest(FNC_NAME, RtspMessageType.PAUSE, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_play(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_play()";

		return internalSendRequest(FNC_NAME, RtspMessageType.PLAY, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_redirect(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_redirect()";

		return internalSendRequest(FNC_NAME, RtspMessageType.REDIRECT, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_setParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoDataCntGetSetParamKvs setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requSetParamValues.copyFrom(setParameterKvs);

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.SET_PARAMETER,
				null,
				inputDataRequ,
				null
			);
	}

	public @NonNull RtspMessageType sendRequest_setup(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setup()";

		return internalSendRequest(FNC_NAME, RtspMessageType.SETUP, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_srtxpRekeyOutboundMikey(
				@NonNull String resourceUrlForSubStream,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull SrtxpKmd kmdOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundMikey()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrlForSubStream);

		if (kmdOutbound.isForLegacySdes()) {
			throw new IllegalArgumentException("KMD cannot be for legacy SDES");
		}

		RtspProtoKmdsStream tmpKmdsOutbound = new RtspProtoKmdsStream();
		tmpKmdsOutbound.putKmdForSubStream(kmdOutbound, idSubStream);

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.SET_PARAMETER,
				idSubStream,
				inputDataRequ,
				tmpKmdsOutbound
			);
	}

	public @NonNull RtspMessageType sendRequest_srtxpRekeyOutboundSdes(
				@NonNull String resourceUrl,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundSdes()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);

		if (kmdsOutbound.getNumberOfSubStreams() == 0) {
			throw new IllegalArgumentException("Need KMD for at least one sub-stream");
		}
		if (! kmdsOutbound.getAreKmdsForLegacySdes().orElse(false)) {
			throw new IllegalArgumentException("KMDs must be for legacy SDES");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.ANNOUNCE,
				null,
				inputDataRequ,
				kmdsOutbound
			);
	}

	public @NonNull RtspMessageType sendRequest_teardown(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_teardown()";

		return internalSendRequest(FNC_NAME, RtspMessageType.TEARDOWN, resourceUrl);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate new outbound Key Management Data (KMD) for re-keying.<br />
	 * <b>Note:</b> This requires that the supported methods of the remote host have already been queried
	 * and that there already exists a valid KMD for the specified sub-stream.
	 * @param idSubStream Sub-Stream identifier
	 * @return Key Management Data (either for MIKEY or SDES)
	 * @throws RtspInvalidRequestException If the remote host does not support re-keying or no previous KMD exists
	 */
	public @NonNull SrtxpKmd generateNewOutboundKmdForRekeying(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".generateNewOutboundKmdForRekeying()";

		Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = rtspSessionInfo.descrSetupInfosStream.getSiBySubStreamId(idSubStream);
		if (tmpOptSiSs.isEmpty()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": No Sub-Stream Info found for " +
					"Sub-Stream ID '" + idSubStream.getIdStr() + "'");
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = tmpOptSiSs.get();

		// get the SrtxpKmd object
		if (! tmpSiSs.kmdOutbound.isKmdSet()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Sub-Stream ID '" + idSubStream.getIdStr() + "' " +
					"has no previous outbound KMD");
		}
		SrtxpKmd tmpSrtxpKmd = tmpSiSs.kmdOutbound.getKmd().orElseThrow();

		//
		if ((tmpSrtxpKmd.isForLegacySdes() && ! rtspSessionInfo.rhSupportedMessageTypes.containsMt(RtspMessageType.ANNOUNCE)) ||
				(! tmpSrtxpKmd.isForLegacySdes() && ! rtspSessionInfo.rhSupportedMessageTypes.containsMt(RtspMessageType.SET_PARAMETER))) {
			throw new RtspInvalidRequestException("remote host does not support SRTxP re-keying");
		}
		if (tmpSrtxpKmd.mki().isEmpty()) {
			throw new RtspInvalidRequestException("cannot re-key when initial KMD had no MKI");
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

	private @NonNull RtspMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspMessageType requestMessageType,
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException {
		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requRscUrl.setUrlStr(resourceUrl);
		return internalSendRequest(
				fncName,
				requestMessageType,
				null,
				inputDataRequ,
				null
			);
	}

	private @NonNull RtspMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspMessageType requestMessageType,
				@Nullable RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		inputDataRequ.writeProtect();

		if (requestMessageType == RtspMessageType.ANNOUNCE && inputDataRequ.requRscUrl.idInputSource.isEmpty()) {
			logError(fncName, "Input Source not found");
			return requestMessageType;
		}

		//
		RtspProtoDataRequest tmpInputDataRequCopy = new RtspProtoDataRequest(inputDataRequ);

		// load data from Session Info
		if (! loadFromSessionInfo(fncName, requestMessageType, tmpInputDataRequCopy)) {
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
		} catch (RtspInvalidRequestException e) {
			logError(fncName, "Failed to build HL request: " + e.getMessage());
			return requestMessageType;
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowRequestProducer.buildMessage(msgStructured);
		} catch (RtspInvalidRequestException e) {
			logError(fncName, "Failed to build LL request: " + e.getMessage());
			return requestMessageType;
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(fncName, String.format("Sent request '%s' to remote host (<%s>, CSeq=%s)\n",
				msgStructured.messageType,
				tmpInputDataRequCopy.requIdSession.isEmpty() ? "-" : tmpInputDataRequCopy.requIdSession.getIdStr(),
				msgStructured.getHeaderCseq().isPresent() ? Integer.toUnsignedString(msgStructured.getHeaderCseq().get()) : "-"));
		return requestMessageType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean loadFromSessionInfo(
				@NonNull String fncName,
				@NonNull RtspMessageType requestMessageType,
				@NonNull RtspProtoDataRequest dataRequ
			) {
		dataRequ.requIdSession.copyFrom(rtspSessionInfo.idSession);
		dataRequ.setRtspProtoVersionToUse(rtspSessionInfo.rtspProtoVersionToUse);
		dataRequ.setCseqNrToSend(++rtspSessionInfo.seqNr_requToRem_lastSent);
		dataRequ.setClientUa(rtspSessionInfo.clientUserAgent);
		if (! rtspSessionInfo.clientIpAddr.isEmpty()) {
			dataRequ.requClientIpAddr.copyFrom(rtspSessionInfo.clientIpAddr);
		}

		if (requestMessageType == RtspMessageType.ANNOUNCE) {
			try {
				dataRequ.requServerIpFromRscUrl.copyFrom(
						rtspSessionInfo.findRtspIpFromResourceUrl(dataRequ.requRscUrl)
					);
			} catch (RtspCannotFindIpFromRscUrlException e) {
				logError(fncName, e.getMessage());
				return false;
			}
		}

		// authentication parameters
		dataRequ.requAuthClient.setAuthUser(rtspSessionInfo.permAuthClient.authUser);
		dataRequ.requAuthClient.setAuthPlainPassword(rtspSessionInfo.permAuthClient.authPlainPassword);
		dataRequ.requAuthClient.setAuthRealm(rtspSessionInfo.permAuthServer.getAuthRealm());
		dataRequ.requAuthClient.setAuthNonce(rtspSessionInfo.permAuthServer.getAuthNonce());

		// main transport parameters
		dataRequ.requStreamTpMain.copyFrom(rtspSessionInfo.streamTpMain);

		return true;
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
