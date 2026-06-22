package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestProducer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.request.RtspProtoLowRequestProducer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoClientCredentials;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpProducer;

import java.util.Set;

/**
 * Service for sending RTSP requests over a TCP connection.
 */
public final class RtspProtoRequestOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isRequestFromClient;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoHighRequestProducer rtspProtoHighRequestProducer;
	private final RtspProtoLowRequestProducer rtspProtoLowRequestProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isRequestFromClient Is this a request being sent by the client?
	 * @param cfgSenderAppNameAndVersion Server/client software name and version
	 * @param cfgContentLanguage Content language (can be empty)
	 * @param cfgIsDebugPrintRtspSdpSent Enable printing sent SDP data for debugging?
	 * @param cfgIsDebugPrintRtspSent Enable printing sent RTSP lines for debugging?
	 * @param rtspSessionInfo RTSP session info
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 * @param availableStreamsInterface Available streams instance
	 * @param globalSessionInfoInterface Global session info instance
	 */
	public RtspProtoRequestOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull String cfgSenderAppNameAndVersion,
				@NonNull String cfgContentLanguage,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugPrintRtspSent,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		if (cfgSenderAppNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgSenderAppNameAndVersion cannot be blank");
		}

		this.logMsgInterface = logMsgInterface;
		this.isRequestFromClient = isRequestFromClient;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpProducer sdpProducer = new RtspProtoSdpProducer(
				cfgSenderAppNameAndVersion,
				cfgContentLanguage,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

		//
		this.rtspProtoHighRequestProducer = new RtspProtoHighRequestProducer(
				logMsgInterface,
				cfgSenderAppNameAndVersion,
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

	/**
	 * ANNOUNCE updates the session description in real-time.<br />
	 * <b>Note:</b> This is only allowed for Server->Client requests.
	 * @param resourceUrl The Resource URL
	 * @param idInputSource The Input Source ID
	 * @param announceSdp The session description
	 * @return The message type
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If there is an I/O exception
	 */
	public @NonNull RtspProtoMessageType sendRequest_announce(
				@NonNull String resourceUrl,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoDataCntSdpRaw announceSdp
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_announce()";

		if (isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Server->Client requests");
		}

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.requAnnouncedSdpRaw.copyFrom(announceSdp);
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.rrRscUrl.idInputSource.copyFrom(idInputSource);

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

	/**
	 * DESCRIBE retrieves the description of a presentation or media object identified by the request URL from a server.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_describe(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_describe()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.DESCRIBE, resourceUrl, clientCredentials);
	}

	/**
	 * GET_PARAMETER with no entity body may be used to test client or server liveness ("ping").
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.GET_PARAMETER, resourceUrl, clientCredentials);
	}

	/**
	 * GET_PARAMETER request retrieves the value of a parameter of a presentation or stream specified in the URI.<br />
	 * GET_PARAMETER with no entity body may be used to test client or server liveness ("ping").
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @param getParameterNames The names of the parameters to retrieve (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamNames getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrGetParamNames.copyFrom(getParameterNames);
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.GET_PARAMETER,
				clientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		return sendRequest_options(resourceUrl, dummyClientCredentials, Set.of(), Set.of());
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		return sendRequest_options(resourceUrl, clientCredentials, Set.of(), Set.of());
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @param requiredFeatures The required features that the remote host must support (can be empty)
	 * @param proxyRequiredFeatures The required features that the proxy must support (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull Set<@NonNull String> requiredFeatures,
				@NonNull Set<@NonNull String> proxyRequiredFeatures
			) throws TcpSocketClosedException, TcpSocketIoException {
		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		return sendRequest_options(resourceUrl, dummyClientCredentials, requiredFeatures, proxyRequiredFeatures);
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @param requiredFeatures The required features that the remote host must support (can be empty)
	 * @param proxyRequiredFeatures The required features that the proxy must support (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull Set<@NonNull String> requiredFeatures,
				@NonNull Set<@NonNull String> proxyRequiredFeatures
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_options()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requRequiredFeatures.putAllFeatureNames(requiredFeatures);
		inputDataRequ.requProxyRequiredFeatures.putAllFeatureNames(proxyRequiredFeatures);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.OPTIONS,
				clientCredentials,
				null,
				inputDataRequ,
				null
			);
	}

	/**
	 * PAUSE request is used to pause the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_pause(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_pause()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.PAUSE, resourceUrl, clientCredentials);
	}

	/**
	 * PLAY request is used to start the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_play(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_play()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		// @TODO add parameter for Range

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.PLAY, resourceUrl, clientCredentials);
	}

	/**
	 * REDIRECT request is used to redirect the client to a new URL.<br />
	 * <b>Note:</b> This is only allowed for Server->Client requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_redirect(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_redirect()";

		if (isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Server->Client requests");
		}

		// @TODO add Location and Range parameters

		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.REDIRECT, resourceUrl, dummyClientCredentials);
	}

	/**
	 * SET_PARAMETER requests to set the value of one or more parameters for a presentation or stream specified by the URI.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client credentials (can be empty)
	 * @param setParameterKvs The key-value pairs of parameters to set
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_setParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamKvs setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
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

	/**
	 * SETUP requests to establish a media session between the client and server.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrlForSubStream The Resource URL for the Sub-Stream
	 * @param clientCredentials Client credentials (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_setup(
				@NonNull String resourceUrlForSubStream,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setup()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		// @TODO add parameters for Transport and KeyMgmt

		return internalSendRequest(FNC_NAME, RtspProtoMessageType.SETUP, resourceUrlForSubStream, clientCredentials);
	}

	/**
	 * Send new Key Management Data (KMD) using MIKEY for re-keying of SRTP/SRTCP.<br />
	 * This will issue a SET_PARAMETER request.<br />
	 * <b>Note:</b> This can only work if the remote host supports MIKEY and SET_PARAMETER.
	 * @param resourceUrlForSubStream The Resource URL for the Sub-Stream
	 * @param idSubStream Sub-Stream identifier
	 * @param kmdOutbound Key Management Data (KMD) for the Sub-Stream
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundMikey(
				@NonNull String resourceUrlForSubStream,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull SrtxpKmd kmdOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundMikey()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrlForSubStream);

		RtspProtoClientCredentials dummyClientCredentials = new RtspProtoClientCredentials();

		if (kmdOutbound.getMetaIsForLegacySdes()) {
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

	/**
	 * Send new Key Management Data (KMD) using the legacy SDES format for re-keying of SRTP/SRTCP.<br />
	 * This will issue an ANNOUNCE request.<br />
	 * <b>Note:</b> This can only work if the remote host supports the legacy SDES format and ANNOUNCE.
	 * @param resourceUrl The Resource URL
	 * @param kmdsOutbound Key Management Data (KMD) for all Sub-Streams
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundSdes(
				@NonNull String resourceUrl,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundSdes()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);

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

	/**
	 * The TEARDOWN request stops the stream delivery for the given URI, freeing the resources associated with it.<br />
	 * If the URI is the presentation URI for this presentation, any RTSP session identifier
	 * associated with the session is no longer valid.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client credentials (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 */
	public @NonNull RtspProtoMessageType sendRequest_teardown(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_teardown()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

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
					"Sub-Stream ID '" + idSubStream.getIdStr().orElse("-unset-") + "'");
		}

		// get the SrtxpKmd object
		if (! tmpSiSs.getKmdOutboundPtr().isKmdSet()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream ID '" +
					idSubStream.getIdStr().orElse("-unset-") + "' " + "has no previous outbound KMD");
		}
		SrtxpKmd tmpSrtxpKmd = tmpSiSs.getKmdOutboundPtr().getKmd().orElseThrow();

		//
		RtspProtoDataCntMessageTypes tmpRhSuppMts = rtspSessionInfo.getRhSupportedMessageTypes();
		if ((tmpSrtxpKmd.getMetaIsForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.ANNOUNCE)) ||
				(! tmpSrtxpKmd.getMetaIsForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.SET_PARAMETER))) {
			throw new RtspProtoInvalidRequestException("remote host does not support SRTxP re-keying");
		}
		if (tmpSrtxpKmd.mki().isEmpty()) {
			throw new RtspProtoInvalidRequestException("cannot re-key when initial KMD had no MKI");
		}

		// generate the new Key Management Data
		SrtxpMki tmpMkiObj;
		if (tmpSrtxpKmd.mki().isEmpty()) {
			tmpMkiObj = SrtxpMki.ofEmpty();
		} else {
			final long nextMki = tmpSrtxpKmd.mki().getValue().orElseThrow() + 1L;  // will automatically be wrapped around
			tmpMkiObj = SrtxpMki.of(nextMki, tmpSrtxpKmd.mki().getSizeBytes());
		}
		if (! tmpSrtxpKmd.getMetaIsForLegacySdes()) {
			return SrtxpKmd.createForMikeyWithDefaults(tmpMkiObj, tmpSrtxpKmd.ssrcId());
		}
		return SrtxpKmd.createForLegacySdesWithDefaults(
				tmpSrtxpKmd.getMetaTagForLegacySdes().orElse(0) + 1,
				tmpMkiObj,
				tmpSrtxpKmd.ssrcId(),
				tmpSrtxpKmd.kdr()
			);
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
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
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

		if (requestMessageType == RtspProtoMessageType.ANNOUNCE && inputDataRequ.rrRscUrl.idInputSource.isEmpty()) {
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
				tmpInputDataRequCopy.rrIdSession.isEmpty() ? "-" : tmpInputDataRequCopy.rrIdSession.getIdStr().orElseThrow(),
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
		dataRequ.rrIdSession.copyFrom(rtspSessionInfo.getIdSession());
		dataRequ.setRtspProtoVersionToUse(rtspSessionInfo.getRtspProtoVersionToUse());
		dataRequ.copyAndIncrementCseqNrToSend(rtspSessionInfo.getCseqNr_requToRem_lastSent());
		if (rtspSessionInfo.getClientUserAgent().isPresent()) {
			dataRequ.setClientUa(rtspSessionInfo.getClientUserAgent().orElseThrow());
		}
		if (! rtspSessionInfo.getClientIpAddr().isEmpty()) {
			dataRequ.rrClientIpAddr.copyFrom(rtspSessionInfo.getClientIpAddr());
		}

		if (requestMessageType == RtspProtoMessageType.ANNOUNCE) {
			try {
				dataRequ.rrServerIpFromRscUrl.copyFrom(
						RtspProtoSessionInfo.findRtspIpFromResourceUrl(dataRequ.rrRscUrl)
					);
			} catch (RtspProtoCannotFindIpFromRscUrlException e) {
				logError(fncName, e.getMessage());
				return false;
			}
		}

		// authentication parameters
		if (! clientCredentials.isEmpty()) {
			dataRequ.requAuthClient.setAuthUser(clientCredentials.getAuthUser().orElseThrow());
			dataRequ.requAuthClient.setAuthPlainPassword(clientCredentials.getAuthPlainPassword().orElse(""));
		}
		dataRequ.requAuthClient.setAuthRealm(rtspSessionInfo.getPermAuthServer().getAuthRealm());
		dataRequ.requAuthClient.setAuthNonce(rtspSessionInfo.getPermAuthServer().getAuthNonce());

		// main transport parameters
		dataRequ.rrStreamTpMain.copyFrom(rtspSessionInfo.getStreamTpMain());

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
