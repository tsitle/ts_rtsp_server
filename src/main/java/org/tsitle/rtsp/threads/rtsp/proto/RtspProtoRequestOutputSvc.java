package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.RtspStaticSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestBuilder;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspKeymgmtKmdsOutbound;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestBuilder;

import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RtspProtoRequestOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final boolean isRequestFromClient;

	private final RtspProtoHighRequestBuilder rtspProtoHighRequestBuilder;
	private final RtspProtoLowRequestBuilder rtspProtoLowRequestBuilder;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	public RtspProtoRequestOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				boolean isRequestFromClient
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.isRequestFromClient = isRequestFromClient;

		//
		this.rtspProtoHighRequestBuilder = new RtspProtoHighRequestBuilder(
				logMsgInterface,
				rtspSessionInfo
			);
		this.rtspProtoLowRequestBuilder = new RtspProtoLowRequestBuilder(logMsgInterface);
		this.rtspProtoLowMsgWriter = new RtspProtoLowMsgWriter(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				rtspConfig.getIsDebugPrintRtspSent()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
				@Nullable Set<String> getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.GET_PARAMETER,
				resourceUrl,
				null,
				null,
				getParameterNames,
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

	public @NonNull RtspMessageType sendRequest_record(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_record()";

		return internalSendRequest(FNC_NAME, RtspMessageType.RECORD, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_redirect(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_redirect()";

		return internalSendRequest(FNC_NAME, RtspMessageType.REDIRECT, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_setParameter(
				@NonNull String resourceUrl,
				@NonNull Map<@NonNull String, @NonNull String> setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setParameter()";

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.SET_PARAMETER,
				resourceUrl,
				null,
				null,
				null,
				setParameterKvs
			);
	}

	public @NonNull RtspMessageType sendRequest_setup(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setup()";

		return internalSendRequest(FNC_NAME, RtspMessageType.SETUP, resourceUrl);
	}

	public @NonNull RtspMessageType sendRequest_srtxpRekeyOutboundMikey(
				@NonNull String resourceUrlForSubStream,
				@NonNull String subStreamId,
				@NonNull SrtxpKmd kmdOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundMikey()";

		if (kmdOutbound.isForLegacySdes()) {
			throw new IllegalArgumentException("KMD cannot be for legacy SDES");
		}

		RtspKeymgmtKmdsOutbound tmpKmdsOutbound = new RtspKeymgmtKmdsOutbound();
		tmpKmdsOutbound.setKmdForSubStream1(kmdOutbound, subStreamId);

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.SET_PARAMETER,
				resourceUrlForSubStream,
				subStreamId,
				tmpKmdsOutbound,
				null,
				null
			);
	}

	public @NonNull RtspMessageType sendRequest_srtxpRekeyOutboundSdes(
				@NonNull String resourceUrl,
				@NonNull RtspKeymgmtKmdsOutbound kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundSdes()";

		if (kmdsOutbound.getNumberOfSubStreams() == 0) {
			throw new IllegalArgumentException("Need KMD for at least one sub-stream");
		}
		if (! kmdsOutbound.getAreKmdsForLegacySdes().orElse(false)) {
			throw new IllegalArgumentException("KMDs must be for legacy SDES");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspMessageType.ANNOUNCE,
				resourceUrl,
				null,
				kmdsOutbound,
				null,
				null
			);
	}

	public @NonNull RtspMessageType sendRequest_teardown(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_teardown()";

		return internalSendRequest(FNC_NAME, RtspMessageType.TEARDOWN, resourceUrl);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate new Key Management Data (KMD) for re-keying.<br />
	 * <b>Note:</b> This requires that the supported methods of the remote host have already been queried
	 * and that there already exists a valid KMD for the specified sub-stream.
	 * @param subStreamId Sub-Stream identifier
	 * @return Key Management Data (either for MIKEY or SDES)
	 * @throws RtspInvalidRequestException If the remote host does not support re-keying or no previous KMD exists
	 */
	public @NonNull SrtxpKmd generateNewKmdForRekeying(@NonNull String subStreamId)
			throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".generateNewKmdForRekeying()";

		RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
				FNC_NAME,
				subStreamId
			);

		// get the StreamKmds object
		InetAddress tmpRemoteHostIpAddr = getRemoteHostIpAddr();
		Optional<RtspStaticSessionInfo.StreamKmds> tmpOptStreamKmds = RtspStaticSessionInfo.getStreamKmds(
				tmpRemoteHostIpAddr,
				subStreamId
			);
		if (tmpOptStreamKmds.isEmpty()) {
			throw new RtspInvalidRequestException("no KMD found for Sub-Stream ID '" + subStreamId + "'");
		}
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = tmpOptStreamKmds.get();

		//
		if ((tmpStreamKmds.isForLegacySdes && ! rtspSessionInfo.rhSupportedMessageTypes.contains(RtspMessageType.ANNOUNCE)) ||
				(! tmpStreamKmds.isForLegacySdes && ! rtspSessionInfo.rhSupportedMessageTypes.contains(RtspMessageType.SET_PARAMETER))) {
			throw new RtspInvalidRequestException("remote host does not support SRTxP re-keying");
		}
		Objects.requireNonNull(tmpStreamKmds.kmdOutbound);
		if (tmpStreamKmds.kmdOutbound.mki().isEmpty()) {
			throw new RtspInvalidRequestException("cannot re-key when initial KMD had no MKI");
		}

		// generate the new Key Management Data
		if (! tmpStreamKmds.isForLegacySdes) {
			final long nextMki = tmpStreamKmds.kmdOutbound.mki().value() + 1;  // will automatically be wrapped around
			return SrtxpKmd.createWithDefaults(nextMki, tmpSetupSubStream.rtspSsrcId);
		}
		return SrtxpKmd.createForLegacySdes(tmpSetupSubStream.rtspSsrcId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspMessageType requestMessageType,
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException {
		return internalSendRequest(
				fncName,
				requestMessageType,
				resourceUrl,
				null,
				null,
				null,
				null
			);
	}

	private @NonNull RtspMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspMessageType requestMessageType,
				@NonNull String resourceUrl,
				@Nullable String subStreamId,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable Set<String> getParameterNames,
				@Nullable Map<@NonNull String, @NonNull String> setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		// build the outgoing message
		RtspProtoHighMsgStructuredRequest msgStructured;
		try {
			msgStructured = rtspProtoHighRequestBuilder.buildRequest(
					requestMessageType,
					resourceUrl,
					subStreamId,
					kmdsOutbound,
					getParameterNames,
					setParameterKvs
				);
		} catch (RtspInvalidRequestException e) {
			logError(fncName, "Failed to build HL request: " + e.getMessage());
			return requestMessageType;
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowRequestBuilder.buildMessage(msgStructured);
		} catch (RtspInvalidRequestException e) {
			logError(fncName, "Failed to build LL request: " + e.getMessage());
			return requestMessageType;
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(fncName, String.format("Sent request '%s' to remote host (<%s>, CSeq=%s)\n",
				msgStructured.messageType,
				rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId,
				msgStructured.getHeaderCseq().isPresent() ? Integer.toUnsignedString(msgStructured.getHeaderCseq().get()) : "-"));
		return requestMessageType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getRemoteHostIpAddr() throws RtspInvalidRequestException {
		Optional<InetAddress> tmpOptRemoteHostIpAddr = (isRequestFromClient ?
				rtspSessionInfo.getServerIpAddr() : rtspSessionInfo.getClientIpAddr());
		final String excMsg = (isRequestFromClient ? "Server" : "Client") + " IP address is not set";
		return tmpOptRemoteHostIpAddr.orElseThrow(() -> new RtspInvalidRequestException(excMsg));
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
