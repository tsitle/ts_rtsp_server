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
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestProducer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.request.RtspProtoLowRequestProducer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpProducer;

import java.util.Set;

/**
 * Service for sending RTSP requests over a TCP connection.
 */
public final class RtspProtoRequestOutputSvc {

	private static class InternRequArgs {
		@Nullable RtspProtoIdSubStream inputIdSubStream = null;
		final @NonNull RtspProtoDataRequest inputDataRequ;
		@Nullable RtspProtoKmdsStream kmdsOutboundForAnnounceSetParam = null;
		boolean announceFromClientGenerateLegacySdesKmds = false;
		boolean setupUseTransportUdp = true;

		InternRequArgs(@NonNull RtspProtoDataRequest inputDataRequ) {
			this.inputDataRequ = inputDataRequ;
		}
	}

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isRequestFromClient;
	private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final @Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;

	private final RtspProtoHighRequestProducer rtspProtoHighRequestProducer;
	private final RtspProtoLowRequestProducer rtspProtoLowRequestProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isRequestFromClient Is this a request being sent by the client?
	 * @param cfgSenderAppNameAndVersion Server/client software name and version
	 * @param cfgConnectionPolicy Connection policy (can be null, default is KEEPALIVE)
	 * @param cfgContentLanguage Content language (can be empty)
	 * @param cfgIsDebugPrintRtspSdpSent Enable printing sent SDP data for debugging?
	 * @param cfgIsDebugPrintRtspSent Enable printing sent RTSP lines for debugging?
	 * @param sessionInfoPtr Session Info pointer
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 * @param availableStreamsInterface Available streams instance (only required for requests from the server)
	 * @param globalSessionInfoInterface Global session info instance (only required for requests from the server)
	 */
	public RtspProtoRequestOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull String cfgSenderAppNameAndVersion,
				@Nullable RtspConnectionPolicy cfgConnectionPolicy,
				@NonNull String cfgContentLanguage,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugPrintRtspSent,
				@NonNull RtspProtoPtrSessionInfo sessionInfoPtr,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		if (cfgSenderAppNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgSenderAppNameAndVersion cannot be blank");
		}
		if (! isRequestFromClient && availableStreamsInterface == null) {
			throw new IllegalArgumentException("availableStreamsInterface cannot be null for requests from the server");
		}
		if (! isRequestFromClient && globalSessionInfoInterface == null) {
			throw new IllegalArgumentException("globalSessionInfoInterface cannot be null for requests from the server");
		}

		this.logMsgInterface = logMsgInterface;
		this.isRequestFromClient = isRequestFromClient;
		this.sessionInfoPtr = sessionInfoPtr;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.globalSessionInfoInterface = globalSessionInfoInterface;

		//
		RtspProtoSdpProducer sdpProducer = new RtspProtoSdpProducer(
				cfgSenderAppNameAndVersion,
				cfgContentLanguage,
				availableStreamsInterface,
				globalSessionInfoInterface,
				null
			);

		//
		this.rtspProtoHighRequestProducer = new RtspProtoHighRequestProducer(
				logMsgInterface,
				isRequestFromClient,
				cfgSenderAppNameAndVersion,
				cfgConnectionPolicy == null ? RtspConnectionPolicy.KEEPALIVE : cfgConnectionPolicy,
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
	 * @return The message type
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If there is an I/O exception
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_announce(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_announce()";

		if (isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Server->Client requests");
		}

		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.ANNOUNCE,
				resourceUrl,
				dummyClientCredentials
			);
	}

	/**
	 * DESCRIBE retrieves the description of a presentation or media object identified by the request URL from a server.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_describe(@NonNull String resourceUrl)
			throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_describe(resourceUrl, dummyClientCredentials);
	}

	/**
	 * DESCRIBE retrieves the description of a presentation or media object identified by the request URL from a server.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_describe(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_describe()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.DESCRIBE,
				resourceUrl,
				clientCredentials
			);
	}

	/**
	 * GET_PARAMETER with no entity body may be used to test client or server liveness ("ping").
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_getParameter(resourceUrl, dummyClientCredentials);
	}

	/**
	 * GET_PARAMETER with no entity body may be used to test client or server liveness ("ping").
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.GET_PARAMETER,
				resourceUrl,
				clientCredentials
			);
	}

	/**
	 * GET_PARAMETER request retrieves the value of a parameter of a presentation or stream specified in the URI.<br />
	 * GET_PARAMETER with no entity body may be used to test client or server liveness ("ping").
	 * @param resourceUrl The Resource URL
	 * @param getParameterNames The names of the parameters to retrieve (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull RtspProtoRscUrl resourceUrl,
				@NonNull RtspProtoDataCntGetSetParamNames getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_getParameter(resourceUrl, dummyClientCredentials, getParameterNames);
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
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_getParameter(
				@NonNull RtspProtoRscUrl resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamNames getParameterNames
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_getParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrGetParamNames.copyFrom(getParameterNames);
		inputDataRequ.rrRscUrl.copyFrom(resourceUrl);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.GET_PARAMETER,
				clientCredentials,
				internRequArgs
			);
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_options(resourceUrl, dummyClientCredentials, Set.of(), Set.of());
	}

	/**
	 * OPTIONS request is used to query the server/client capabilities.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
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
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull Set<@NonNull String> requiredFeatures,
				@NonNull Set<@NonNull String> proxyRequiredFeatures
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_options(
				resourceUrl,
				dummyClientCredentials,
				requiredFeatures,
				proxyRequiredFeatures
			);
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
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_options(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull Set<@NonNull String> requiredFeatures,
				@NonNull Set<@NonNull String> proxyRequiredFeatures
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_options()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.requRequiredFeatures.putAllFeatureNames(requiredFeatures);
		inputDataRequ.requProxyRequiredFeatures.putAllFeatureNames(proxyRequiredFeatures);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.OPTIONS,
				clientCredentials,
				internRequArgs
			);
	}

	/**
	 * PAUSE request is used to pause the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_pause(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_pause(resourceUrl, dummyClientCredentials);
	}

	/**
	 * PAUSE request is used to pause the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_pause(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_pause()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.PAUSE,
				resourceUrl,
				clientCredentials
			);
	}

	/**
	 * PLAY request is used to start the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_play(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_play(resourceUrl, dummyClientCredentials);
	}

	/**
	 * PLAY request is used to start the playback of a presentation or stream.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client Credentials for authentication (can be empty)
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_play(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_play()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);
		inputDataRequ.setPlaybackRangeValue("npt=0.000-");

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.PLAY,
				clientCredentials,
				internRequArgs
			);
	}

	/**
	 * SET_PARAMETER requests to set the value of one or more parameters for a presentation or stream specified by the URI.
	 * @param resourceUrl The Resource URL
	 * @param setParameterKvs The key-value pairs of parameters to set
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_setParameter(
				@NonNull RtspProtoRscUrl resourceUrl,
				@NonNull RtspProtoDataCntGetSetParamKvs setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_setParameter(resourceUrl, dummyClientCredentials, setParameterKvs);
	}

	/**
	 * SET_PARAMETER requests to set the value of one or more parameters for a presentation or stream specified by the URI.
	 * @param resourceUrl The Resource URL
	 * @param clientCredentials Client credentials (can be empty)
	 * @param setParameterKvs The key-value pairs of parameters to set
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_setParameter(
				@NonNull RtspProtoRscUrl resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataCntGetSetParamKvs setParameterKvs
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setParameter()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.copyFrom(resourceUrl);
		inputDataRequ.requSetParamValues.copyFrom(setParameterKvs);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.SET_PARAMETER,
				clientCredentials,
				internRequArgs
			);
	}

	/**
	 * SETUP requests to establish a media session between the client and server.<br />
	 * <b>Note:</b> If encryption is enabled for the Sub-Stream, then MIKEY will be used for SRTxP KMDs.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrlForSubStream The Resource URL for the Sub-Stream
	 * @param useTransportUdp Use UDP transport?
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_setup(
				@NonNull RtspProtoRscUrl resourceUrlForSubStream,
				boolean useTransportUdp
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_setup(
				resourceUrlForSubStream,
				dummyClientCredentials,
				useTransportUdp
			);
	}

	/**
	 * SETUP requests to establish a media session between the client and server.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrlForSubStream The Resource URL for the Sub-Stream
	 * @param clientCredentials Client credentials (can be empty)
	 * @param useTransportUdp Use UDP transport?
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_setup(
				@NonNull RtspProtoRscUrl resourceUrlForSubStream,
				@NonNull RtspProtoClientCredentials clientCredentials,
				boolean useTransportUdp
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_setup()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		if (resourceUrlForSubStream.idSubStream.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": Resource URL object contains invalid Sub-Stream ID");
		}

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.copyFrom(resourceUrlForSubStream);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);
		internRequArgs.setupUseTransportUdp = useTransportUdp;

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.SETUP,
				clientCredentials,
				internRequArgs
			);
	}

	/**
	 * Send the initial Key Management Data (KMD) using the legacy SDES format for SRTP/SRTCP to the server.<br />
	 * This will issue an ANNOUNCE request.<br />
	 * <b>Note:</b> This can only work if the server supports the legacy SDES format and ANNOUNCE.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_srtxpInitialOutboundSdes(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpInitialOutboundSdes()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);

		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);
		internRequArgs.kmdsOutboundForAnnounceSetParam = null;
		internRequArgs.announceFromClientGenerateLegacySdesKmds = true;

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.ANNOUNCE,
				dummyClientCredentials,
				internRequArgs
			);
	}

	/**
	 * Send new Key Management Data (KMD) using MIKEY for re-keying of SRTP/SRTCP.<br />
	 * This will issue a SET_PARAMETER request.<br />
	 * <b>Note:</b> This can only work if the remote host supports MIKEY and SET_PARAMETER.
	 * @param resourceUrlForSubStream The Resource URL for the Sub-Stream
	 * @param kmdOutbound Key Management Data (KMD) for the Sub-Stream
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundMikey(
				@NonNull RtspProtoRscUrl resourceUrlForSubStream,
				@NonNull SrtxpKmd kmdOutbound
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundMikey()";

		if (resourceUrlForSubStream.idSubStream.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": Resource URL object contains invalid Sub-Stream ID");
		}

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.copyFrom(resourceUrlForSubStream);

		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		if (kmdOutbound.getMetaIsForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD cannot be for legacy SDES");
		}

		RtspProtoKmdsStream tmpKmdsOutbound = new RtspProtoKmdsStream();
		tmpKmdsOutbound.putKmdForSubStream(kmdOutbound, inputDataRequ.rrRscUrl.idSubStream);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);
		internRequArgs.inputIdSubStream = inputDataRequ.rrRscUrl.idSubStream;
		internRequArgs.kmdsOutboundForAnnounceSetParam = tmpKmdsOutbound;

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.SET_PARAMETER,
				dummyClientCredentials,
				internRequArgs
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
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_srtxpRekeyOutboundSdes(
				@NonNull String resourceUrl,
				@NonNull RtspProtoKmdsStream kmdsOutbound
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_srtxpRekeyOutboundSdes()";

		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);

		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		if (kmdsOutbound.getNumberOfSubStreams() == 0) {
			throw new IllegalArgumentException("Need KMD for at least one sub-stream");
		}
		if (! kmdsOutbound.getAreKmdsForLegacySdes().orElse(false)) {
			throw new IllegalArgumentException("KMDs must be for legacy SDES");
		}

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);
		internRequArgs.kmdsOutboundForAnnounceSetParam = kmdsOutbound;

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.ANNOUNCE,
				dummyClientCredentials,
				internRequArgs
			);
	}

	/**
	 * The TEARDOWN request stops the stream delivery for the given URI, freeing the resources associated with it.<br />
	 * If the URI is the presentation URI for this presentation, any RTSP session identifier
	 * associated with the session is no longer valid.<br />
	 * <b>Note:</b> This is only allowed for Client->Server requests.
	 * @param resourceUrl The Resource URL
	 * @return The message type that was sent
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_teardown(
				@NonNull String resourceUrl
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoClientCredentials dummyClientCredentials = RtspProtoClientCredentials.ofEmpty();

		return sendRequest_teardown(resourceUrl, dummyClientCredentials);
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
	 * @throws RtspProtoSendRequestFailedException If the sending the request has failed
	 */
	public @NonNull RtspProtoMessageType sendRequest_teardown(
				@NonNull String resourceUrl,
				@NonNull RtspProtoClientCredentials clientCredentials
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequest_teardown()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException("This method is only allowed for Client->Server requests");
		}

		return internalSendRequest(
				FNC_NAME,
				RtspProtoMessageType.TEARDOWN,
				resourceUrl,
				clientCredentials
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate new outbound Key Management Data (KMD) for re-keying.<br />
	 * <b>Note:</b> This requires that the supported methods of the remote host have already been queried
	 * and that there already exists a valid KMD for the specified sub-stream.
	 * @param sessionInfo Session Info
	 * @param idSubStream Sub-Stream identifier
	 * @return Key Management Data (either for MIKEY or SDES)
	 * @throws RtspProtoInvalidRequestException If the remote host does not support re-keying or no previous KMD exists
	 */
	public static @NonNull SrtxpKmd generateNewOutboundKmdForRekeying(
				@NonNull RtspProtoSessionInfo sessionInfo,
				@NonNull RtspProtoIdSubStream idSubStream
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = RtspProtoRequestOutputSvc.class.getSimpleName() + ".generateNewOutboundKmdForRekeying()";

		RtspProtoSetupInfoForSubStream tmpSiSs;
		try {
			tmpSiSs = sessionInfo.getDescrSetupInfoBySubStreamsId(idSubStream);
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
		RtspProtoDataCntMessageTypes tmpRhSuppMts = sessionInfo.getRhSupportedMessageTypes();
		if ((tmpSrtxpKmd.getMetaIsForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.ANNOUNCE)) ||
				(! tmpSrtxpKmd.getMetaIsForLegacySdes() && ! tmpRhSuppMts.containsMt(RtspProtoMessageType.SET_PARAMETER))) {
			throw new RtspProtoInvalidRequestException("remote host does not support SRTxP re-keying");
		}
		if (tmpSrtxpKmd.getMetaIsForLegacySdes() && tmpSrtxpKmd.getMetaTagForLegacySdes().isEmpty()) {
			// for SDES we need a Tag value. The MKI value is optional.
			throw new RtspProtoInvalidRequestException("cannot re-key with SDES when initial KMD had no Tag");
		}
		if (! tmpSrtxpKmd.getMetaIsForLegacySdes() && tmpSrtxpKmd.mki().isEmpty()) {
			// for MIKEY we need a MKI value
			throw new RtspProtoInvalidRequestException("cannot re-key with MIKEY when initial KMD had no MKI");
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
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		RtspProtoDataRequest inputDataRequ = new RtspProtoDataRequest();
		inputDataRequ.rrRscUrl.setUrlStr(resourceUrl);

		InternRequArgs internRequArgs = new InternRequArgs(inputDataRequ);

		return internalSendRequest(
				fncName,
				requestMessageType,
				clientCredentials,
				internRequArgs
			);
	}

	private @NonNull RtspProtoMessageType internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull InternRequArgs internRequArgs
			) throws TcpSocketClosedException, TcpSocketIoException, RtspProtoSendRequestFailedException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		if (internRequArgs.inputDataRequ.rrRscUrl.getUrlStr().isEmpty()) {
			throw new IllegalArgumentException(fncName + ": Resource URL is empty");
		}

		//
		internRequArgs.inputDataRequ.writeProtect();

		//
		RtspProtoDataRequest ioDataRequCopy = new RtspProtoDataRequest(internRequArgs.inputDataRequ);
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();

		// load data from Session Info
		if (! loadFromSessionInfo(
					fncName,
					requestMessageType,
					clientCredentials,
					ioDataRequCopy,
					ioSetupInfosStream
				)) {
			throw new RtspProtoSendRequestFailedException(fncName + ": Failed to load Session Info");
		}

		// build the outgoing message
		RtspProtoHighMsgStructuredRequest msgStructured;
		try {
			msgStructured = rtspProtoHighRequestProducer.buildRequest(
					requestMessageType,
					ioDataRequCopy,
					ioSetupInfosStream,
					internRequArgs.kmdsOutboundForAnnounceSetParam,
					internRequArgs.announceFromClientGenerateLegacySdesKmds,
					internRequArgs.setupUseTransportUdp
				);
		} catch (RtspProtoInvalidRequestException e) {
			throw new RtspProtoSendRequestFailedException(fncName + ": Failed to build HL request: " + e.getMessage());
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowRequestProducer.buildMessage(msgStructured);
		} catch (RtspProtoInvalidRequestException e) {
			throw new RtspProtoSendRequestFailedException(fncName + ": Failed to build LL request: " + e.getMessage());
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(fncName, String.format("Sent request '%s' to remote host (<%s>, CSeq=%s)\n",  // <-- intentional extra NL
				msgStructured.messageType,
				ioDataRequCopy.rrIdSession.isEmpty() ? "-" : ioDataRequCopy.rrIdSession.getIdStr().orElseThrow(),
				msgStructured.getHeaderCseq().isPresent() ? msgStructured.getHeaderCseq().get() + "" : "-"));

		// update data in Session Info
		updateSessionInfo(requestMessageType, ioDataRequCopy, ioSetupInfosStream);

		//
		return requestMessageType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean loadFromSessionInfo(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoClientCredentials clientCredentials,
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream
			) {
		// parse URL
		try {
			Set<@NonNull RtspProtoIdSubStream> tmpAvailableSubStreamIds = sessionInfoPtr.ptr().getDescrAvailableSubStreamIds();
			rtspProtoHighRequestProducer.parseOutputUrl(tmpAvailableSubStreamIds, dataRequ.rrRscUrl);
		} catch (RtspProtoInvalidUriException e) {
			logError(fncName, "Parsing the Resource URL UrlStr failed: " + e.getMessage());
			return false;
		}

		// sanity checks
		if (dataRequ.rrRscUrl.getUrlStr().isBlank()) {
			logError(fncName, "Resource URL UrlStr cannot be empty");
			return false;
		}
		if (dataRequ.rrRscUrl.idInputSource.isEmpty()) {
			logError(fncName, "Resource URL idInputSource cannot be empty");
			return false;
		}
		if (requestMessageType == RtspProtoMessageType.SETUP && dataRequ.rrRscUrl.idSubStream.isEmpty()) {
			logError(fncName, "Resource URL idSubStream cannot be empty for SETUP requests");
			return false;
		}

		//
		dataRequ.rrIdSession.copyFrom(sessionInfoPtr.ptr().getIdSession());
		dataRequ.setRtspProtoVersionToUse(sessionInfoPtr.ptr().getRtspProtoVersionToUse());
		dataRequ.copyAndIncrementCseqNrToSend(sessionInfoPtr.ptr().getCseqNr_requToRem_lastSent());
		if (sessionInfoPtr.ptr().getClientUserAgent().isPresent()) {
			dataRequ.setClientUa(sessionInfoPtr.ptr().getClientUserAgent().orElseThrow());
		}
		if (! sessionInfoPtr.ptr().getClientIpAddr().isEmpty()) {
			dataRequ.rrClientIpAddr.copyFrom(sessionInfoPtr.ptr().getClientIpAddr());
		}

		// server IP
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

		// received structured SDP data
		if (requestMessageType == RtspProtoMessageType.ANNOUNCE && sessionInfoPtr.ptr().getRhDescribeSdpStc().isPresent()) {
			dataRequ.requAnnouncedSdpStc.copyFrom(sessionInfoPtr.ptr().getRhDescribeSdpStc().orElseThrow());
		}

		// stream settings
		if (requestMessageType == RtspProtoMessageType.ANNOUNCE || requestMessageType == RtspProtoMessageType.SETUP) {
			Set<@NonNull RtspProtoIdSubStream> tmpSubStreamIds = sessionInfoPtr.ptr().getDescrSetupInfoSubStreamIds();
			if (tmpSubStreamIds.isEmpty()) {
				String tmpErrMsgPfx = (requestMessageType == RtspProtoMessageType.ANNOUNCE ? "An" : "A");
				logError(fncName, tmpErrMsgPfx + " " + requestMessageType +
						" request can only be sent when a DESCRIBE response has already been sent/received");
				return false;
			}
			dataRequ.requAdStreamSett.setIdInputSource(dataRequ.rrRscUrl.idInputSource);
			for (RtspProtoIdSubStream tmpIdSs : tmpSubStreamIds) {
				RtspProtoSetupInfoForSubStream tmpSiForSs;
				try {
					tmpSiForSs = sessionInfoPtr.ptr().getDescrSetupInfoBySubStreamsId(tmpIdSs);
				} catch (RtspProtoSessionInfoException e) {
					logError(fncName, e.getMessage());
					return false;
				}

				RtspProtoAdSettingsForSubStream tmpAdSettForSs = new RtspProtoAdSettingsForSubStream();
				tmpAdSettForSs.idSubStream.copyFrom(tmpIdSs);
				if (! isRequestFromClient && globalSessionInfoInterface != null) {
					try {
						RtspProtoIdEsSource tmpIdEsSource = globalSessionInfoInterface
								.getElementaryStreamSourceIdBySubStreamId(tmpIdSs, sessionInfoPtr.ptr().getClientIpAddr());
						tmpAdSettForSs.idEsSource.copyFrom(tmpIdEsSource);
					} catch (RtspProtoIdSubStreamNotFoundException e) {
						logError(fncName, "Could not find Sub-Stream ID in Global Session Info for " +
								requestMessageType + " request");
						return false;
					}
				}
				tmpAdSettForSs.ssrcInbound.copyFrom(tmpSiForSs.getSsrcInboundPtr());
				tmpAdSettForSs.ssrcOutbound.copyFrom(tmpSiForSs.getSsrcOutboundPtr());
				final String tmpOutRscUrlSubPath = tmpIdSs.getIdStr().orElseThrow();
				tmpAdSettForSs.setUrlSubPathForSubStream(tmpOutRscUrlSubPath);
				dataRequ.requAdStreamSett.putSettingsForSubStream(tmpAdSettForSs);
			}
			dataRequ.requAdStreamSett.writeProtect();
		}
		if (requestMessageType == RtspProtoMessageType.ANNOUNCE ||
				requestMessageType == RtspProtoMessageType.DESCRIBE ||
				requestMessageType == RtspProtoMessageType.SETUP ||
				requestMessageType == RtspProtoMessageType.SET_PARAMETER) {
			ioSetupInfosStream.copyFrom(sessionInfoPtr.ptr().getDescrSetupInfosStream());
		}

		// authentication parameters
		if (! clientCredentials.isEmpty()) {
			dataRequ.requAuthClient.setAuthUser(clientCredentials.getAuthUser().orElseThrow());
			dataRequ.requAuthClient.setAuthPlainPassword(clientCredentials.getAuthPlainPassword().orElse(""));
		}
		dataRequ.requAuthClient.setAuthRealm(sessionInfoPtr.ptr().getPermAuthServer().getAuthRealm());
		dataRequ.requAuthClient.setAuthNonce(sessionInfoPtr.ptr().getPermAuthServer().getAuthNonce());

		// main transport parameters
		dataRequ.rrStreamTpMain.copyFrom(sessionInfoPtr.ptr().getStreamTpMain());

		return true;
	}

	private void updateSessionInfo(
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoSetupInfosStream setupInfosStream
			) {
		sessionInfoPtr.ptr().setCseqNr_requToRem_lastSent(dataRequ.getCseqNrToSend());

		// main transport parameters
		if (dataRequ.rrStreamTpMain.getIsTransportUdp()) {
			sessionInfoPtr.ptr().setStreamTpMainIsTransportUdp();
		} else {
			sessionInfoPtr.ptr().setStreamTpMainIsTransportTcp();
		}

		// store the Resource URL object
		if (isRequestFromClient && dataRequ.rrRscUrl.idSubStream.isEmpty()) {
			sessionInfoPtr.ptr().setLastRequestRscUrl_mainStream(dataRequ.rrRscUrl);
		}
		sessionInfoPtr.ptr().setLastUsedOutgoingRequestResourceUrl(dataRequ.rrRscUrl);

		// store the request message type
		sessionInfoPtr.ptr().setLastUsedOutgoingRequestMsgType(requestMessageType);

		// store sub-stream setting from ANNOUNCE/(DESCRIBE)/SETUP/SET_PARAMETER request
		if (requestMessageType == RtspProtoMessageType.ANNOUNCE || requestMessageType == RtspProtoMessageType.DESCRIBE ||
				requestMessageType == RtspProtoMessageType.SET_PARAMETER || requestMessageType == RtspProtoMessageType.SETUP) {
			sessionInfoPtr.ptr().setDescrSetupInfosStream(setupInfosStream);
		}
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
