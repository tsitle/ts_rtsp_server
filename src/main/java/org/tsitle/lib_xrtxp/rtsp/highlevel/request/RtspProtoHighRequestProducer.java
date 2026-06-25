package org.tsitle.lib_xrtxp.rtsp.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.MikeyGenerator;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighUdpPorts;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspTransportMode;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoAuthDigest;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpProducerInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspKeymgmtProto;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RtspProtoHighRequestProducer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull String cfgSenderAppNameAndVersion;
	private final boolean cfgIsDebugPrintRtspSdpSent;
	private final @Nullable RtspProtoSdpProducerInterface sdpProducerInterface;

	public RtspProtoHighRequestProducer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull String cfgSenderAppNameAndVersion,
				boolean cfgIsDebugPrintRtspSdpSent,
				@Nullable RtspProtoSdpProducerInterface sdpProducerInterface
			) {
		if (cfgSenderAppNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgSenderAppNameAndVersion cannot be blank");
		}
		if (! isRequestFromClient && sdpProducerInterface == null) {
			throw new IllegalArgumentException("sdpProducerInterface cannot be null for requests from the server");
		}

		this.logMsgInterface = logMsgInterface;
		this.cfgSenderAppNameAndVersion = cfgSenderAppNameAndVersion;
		this.cfgIsDebugPrintRtspSdpSent = cfgIsDebugPrintRtspSdpSent;
		this.sdpProducerInterface = sdpProducerInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void parseOutputUrl(
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoRscUrl ioRscUrl
			) throws RtspProtoInvalidUriException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseOutputUrl()";

		try {
			RtspProtoRscUrl tmpObj = ResourceUrlProcessorNg.parseUrlIntoRscUrlObject(
					null,
					null,
					ioRscUrl.getUrlStr(),
					RtspProtoIpAddr.ofLoopback(),  // doesn't matter since it's not used
					inpAvailableSubStreamIds
				);
			ioRscUrl.copyFrom(tmpObj);
		} catch (RtspProtoInvalidUriException |
					RtspProtoIdSubStreamNotFoundException | RtspProtoIdInputSourceNotFoundException e) {
			String tmpExcMsg = "Failed to parse URL: " + e.getMessage();
			logError(FNC_NAME, tmpExcMsg);
			throw new RtspProtoInvalidUriException(tmpExcMsg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredRequest buildRequest(
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoDataRequest ioDataRequ,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@Nullable RtspProtoKmdsStream kmdsOutboundForAnnounceSetParam,
				boolean setupUseTransportUdp
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest()";

		if (ioDataRequ.rrRscUrl.isEmpty()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Resource URL must be set");
		}

		//
		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();

		resObj.rtspProtoVersion = ioDataRequ.getRtspProtoVersionToUse();
		resObj.statusCode = RtspProtoStatusCode.OK;
		resObj.messageType = requestMessageType;

		//
		resObj.resourceUrl = ioDataRequ.rrRscUrl.getUrlStr();

		//
		addCommonHeaders(ioDataRequ, resObj);

		//
		switch (requestMessageType) {
			case ANNOUNCE -> buildRequest_announce(ioDataRequ, kmdsOutboundForAnnounceSetParam, resObj);
			case DESCRIBE -> buildRequest_describe(resObj);
			case GET_PARAMETER -> buildRequest_getParameter(ioDataRequ, resObj);
			case OPTIONS -> buildRequest_options(ioDataRequ, resObj);
			case PAUSE -> buildRequest_pause();
			case PLAY -> buildRequest_play(resObj);
			case REDIRECT -> buildRequest_redirect(resObj);
			case SET_PARAMETER -> buildRequest_setParameter(ioSetupInfosStream, ioDataRequ, kmdsOutboundForAnnounceSetParam, resObj);
			case SETUP -> buildRequest_setup(setupUseTransportUdp, ioDataRequ, ioSetupInfosStream, resObj);
			case TEARDOWN -> buildRequest_teardown();
			default -> throw new RtspProtoInvalidRequestException(FNC_NAME + ": Unsupported message type: " +
					requestMessageType);
		}

		//System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRequest_announce(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspProtoKmdsStream kmdsOutbound,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_announce()";

		/*
		 * Example:
		 *   "ANNOUNCE rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: application/sdp"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "v=0"
		 *   "a=tool:TS RTSP Server/1.0"
		 *   ...
		 */

		if (sdpProducerInterface == null) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": SDP producer must be set");
		}
		if (inputDataRequ.rrClientIpAddr.isEmpty()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Client IP address must be set");
		}

		try {
			sdpProducerInterface.buildUpdatedSdpForAnnounce(
					inputDataRequ.rrStreamTpMain.isSrtpRequired(),
					inputDataRequ.rrRscUrl.idInputSource,
					inputDataRequ.rrServerIpFromRscUrl,
					inputDataRequ.getClientUa(),
					inputDataRequ.rrClientIpAddr,
					inputDataRequ.requAdStreamSett,
					kmdsOutbound,
					output.bodyAnnounceSdp
				);
		} catch (RtspProtoSdpException e) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Building SDP failed: " + e.getMessage());
		}

		if (cfgIsDebugPrintRtspSdpSent) {
			logDebug(FNC_NAME, "-------- SDP:");
			for (String tmpSingleSdpLine : output.bodyAnnounceSdp.getSdpLinesAllRaw()) {
				logDebug(FNC_NAME, "---------------- " + tmpSingleSdpLine);
			}
		}

		// Content-Base
		{
			String tmpRscUrl = inputDataRequ.rrRscUrl.getUrlStr();
			if (tmpRscUrl.isBlank()) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": Resource URL must be set");
			}
			//
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_BASE);
			hdEntry.hdValContBase.contentBaseStr = tmpRscUrl + (tmpRscUrl.endsWith("/") ? "" : "/");
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(output.bodyAnnounceSdp.getContentLang(), output);
	}

	private void buildRequest_describe(@NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * Example:
		 *   "DESCRIBE rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   ...
		 *   "Accept: application/sdp"
		 */

		// Accept
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.ACCEPT);
			hdEntry.hdValAccept.rtspMimeType = RtspMimeType.SDP;
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void buildRequest_getParameter(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		/*
		 * Example:
		 *   "GET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: text/parameters"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "packets_received"
		 *   "jitter"
		 */

		if (inputDataRequ.rrGetParamNames.isParamNamesEmpty()) {
			return;
		}
		output.bodyGetParamNames.copyFrom(inputDataRequ.rrGetParamNames);
		// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
		addContentTypeHeader(output);
	}

	private void buildRequest_options(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) {
		/*
		 * Example:
		 *   "OPTIONS rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   ...
		 *   "Require: implicit-play"
		 *   "Proxy-Require: gzipped-messages"
		 */

		// Require
		if (! inputDataRequ.requRequiredFeatures.isFeatureNamesEmpty()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.REQUIRE);
			hdEntry.hdValRequire.requiredFeatures.addAll(inputDataRequ.requRequiredFeatures.getFeatureNames());
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Proxy-Require
		if (! inputDataRequ.requProxyRequiredFeatures.isFeatureNamesEmpty()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.PROXY_REQU);
			hdEntry.hdValProxyRequ.requiredFeatures.addAll(inputDataRequ.requProxyRequiredFeatures.getFeatureNames());
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void buildRequest_pause() {
		/*
		 * Example:
		 *   "PAUSE rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   "CSeq: 96"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Session: 126437FE"
		 */

		// nothing to do
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildRequest_play(@NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * Example:
		 *   "PLAY rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   ...
		 *   "Range: npt=0.000-"
		 */

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.RANGE);
			hdEntry.hdValRange.rangeStr = "npt=0.000-";  // @TODO make configurable
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void buildRequest_redirect(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_redirect()";

		/*
		 * A redirect request informs the client that it must connect to another
		 * server location. It contains the mandatory header Location, which
		 * indicates that the client should issue requests for that URL. It may
		 * contain the parameter Range, which indicates when the redirection
		 * takes effect. If the client wants to continue to send or receive
		 * media for this URI, the client MUST issue a TEARDOWN request for the
		 * current session and a SETUP for the new session at the designated
		 * host.
		 *
		 * This example request redirects traffic for this URI to the new server
		 * at the given playtime:
		 *   "REDIRECT rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 732"
		 *   "Location: rtsp://bigserver.com:8001"
		 *   "Range: clock=19960213T143205Z-"
		 */

		// @TODO set some headers

		logError(FNC_NAME, "REDIRECT is not supported yet");
		throw new RtspProtoInvalidRequestException(FNC_NAME + ": REDIRECT is not supported yet");
	}

	private void buildRequest_setParameter(
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspProtoKmdsStream kmdsOutbound,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setParameter()";

		/*
		 * Example:
		 *   "SET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: text/parameters"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "packets_received: 100.0"
		 *   "jitter: 13.8"
		 */

		//
		if (! inputDataRequ.requSetParamValues.isParamKvsEmpty()) {
			output.bodySetParamKv.copyFrom(inputDataRequ.requSetParamValues);
			// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
			addContentTypeHeader(output);
			// Content-Language
			addContentLangHeader(inputDataRequ.requSetParamValues.getContentLang(), output);
		}

		// set the SRTxP Key Management Data for a SET_PARAMETER request used for re-keying
		if (kmdsOutbound == null) {
			return;
		}
		RtspProtoIdSubStream idSubStreamPtr = inputDataRequ.rrRscUrl.idSubStream;
		if (idSubStreamPtr.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": idSubStream must be set when " +
					"setting SRTxP key management data for SET_PARAMETER request");
		}
		Optional<SrtxpKmd> tmpOptKmd = kmdsOutbound.getKmdBySubStreamId(idSubStreamPtr);
		if (tmpOptKmd.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD for Sub-Stream not found");
		}
		SrtxpKmd tmpKmd = tmpOptKmd.get();
		if (tmpKmd.getMetaIsForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD must not be for legacy SDES key management");
		}

		if (! ioSetupInfosStream.containsSiForSubStreamId(idSubStreamPtr)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": no SETUP info found for Sub-Stream ID '" +
					idSubStreamPtr.getIdStr().orElse("-unset-") + "'");
		}
		RtspProtoSetupInfoForSubStream siForSs = ioSetupInfosStream.getSiBySubStreamId(idSubStreamPtr).orElseThrow();
		siForSs.getKmdOutboundPtr().setKmd(tmpKmd, idSubStreamPtr);

		// Keymgmt
		addKeymgmtHeader(tmpKmd, output);
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void buildRequest_setup(
				boolean setupUseTransportUdp,
				@NonNull RtspProtoDataRequest ioDataRequ,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setup()";

		/*
		 * Example:
		 *   "SETUP rtsp://example.com/fizzle/foo/substreamidf528764d_93b6207a RTSP/1.0"
		 *   "CSeq: 6"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Transport: RTP/SAVP;unicast;client_port=43704-43705"
		 *   "Session: 126437FE"
		 *   "KeyMgmt: prot=mikey; uri=\"rtsp://example.com/fizzle/foo/substreamidf528764d_93b6207a\"; data=\"...\""
		 */

		RtspProtoIdSubStream idSubStreamPtr = ioDataRequ.rrRscUrl.idSubStream;

		if (idSubStreamPtr.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": inputIdSubStream must be set for a SETUP request");
		}

		//
		if (! ioSetupInfosStream.containsSiForSubStreamId(idSubStreamPtr)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": no SETUP info found for Sub-Stream ID '" +
					idSubStreamPtr.getIdStr().orElse("-unset-") + "'");
		}
		RtspProtoSetupInfoForSubStream siForSs = ioSetupInfosStream.getSiBySubStreamId(idSubStreamPtr).orElseThrow();

		// Transport
		{
			ioDataRequ.rrStreamTpMain.setIsTransportUdp(setupUseTransportUdp);

			//
			RtspProtoDataCntSubStreamTp ssTp = siForSs.getSubStreamTpPtr();
			ssTp.setIsUdp(setupUseTransportUdp);
			ssTp.setIsInterleaved(! setupUseTransportUdp);
			ssTp.setIsUnicast(true);
			if (setupUseTransportUdp) {
				// we need to open the UDP sockets now so we can get the port numbers
				try {
					RtspProtoHighUdpPorts.findAndOpenUdpSocketPorts(false, siForSs);
				} catch (RtspProtoCouldNotFindUdpPortsException | UdpSocketIoException e) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " + e.getMessage());
				}
				Objects.requireNonNull(siForSs.getClientUdpSocketRtpPtr());
				Objects.requireNonNull(siForSs.getClientUdpSocketRtcpPtr());
				try {
					ssTp.getClientUdpPortRtpPtr().setPort16bit(siForSs.getClientUdpSocketRtpPtr().getLocalPort());
					ssTp.getClientUdpPortRtcpPtr().setPort16bit(siForSs.getClientUdpSocketRtcpPtr().getLocalPort());
				} catch (RtspProtoNumberRangeException e) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": Setting Client UDP ports failed: " + e.getMessage());
				}
			} else {
				RtspProtoTcpChannelNr tmpMaxTcpChann = ioSetupInfosStream.getHighestTcpChannelNrFromAllSubStreams();
				int tmpMaxInt = tmpMaxTcpChann.getChannel8bit().orElse(0);
				try {
					ssTp.getClientTcpChannRtpPtr().setChannel8bit(tmpMaxInt + 1);
					ssTp.getClientTcpChannRtcpPtr().setChannel8bit(tmpMaxInt + 2);
				} catch (RtspProtoNumberRangeException e) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": Setting Client TCP channels failed: " + e.getMessage());
				}
			}

			//
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.TRANSPORT);
			hdEntry.hdValTransport.tpSubStream.setIsUdp(ssTp.getIsUdp());
			hdEntry.hdValTransport.tpSubStream.setIsInterleaved(ssTp.getIsInterleaved());
			hdEntry.hdValTransport.tpSubStream.setIsUnicast(ssTp.getIsUnicast());
			hdEntry.hdValTransport.tpSubStream.setIsEncr(ssTp.getIsEncr());
			hdEntry.hdValTransport.tpMode = RtspTransportMode.PLAY;
			if (setupUseTransportUdp) {
				try {
					hdEntry.hdValTransport.tpSubStream.getClientUdpPortRtpPtr().setPort16bit(siForSs.getClientUdpSocketRtpPtr().getLocalPort());
					hdEntry.hdValTransport.tpSubStream.getClientUdpPortRtcpPtr().setPort16bit(siForSs.getClientUdpSocketRtcpPtr().getLocalPort());
				} catch (RtspProtoNumberRangeException e) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": Setting Client UDP ports failed: " + e.getMessage());
				}
			} else {
				try {
					hdEntry.hdValTransport.tpSubStream.getClientTcpChannRtpPtr().setChannel8bit(
							ssTp.getClientTcpChannRtpPtr().getChannel8bit().orElseThrow()
						);
					hdEntry.hdValTransport.tpSubStream.getClientTcpChannRtcpPtr().setChannel8bit(
							ssTp.getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow()
						);
				} catch (RtspProtoNumberRangeException e) {
					// this will never happen
				}
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// Keymgmt
		if (ioDataRequ.rrStreamTpMain.getIsTransportSrtpSrtcp() && siForSs.getKmdInboundCurPtr().isKmdSet()) {
			if (siForSs.getKmdInboundCurPtr().getIsKmdForLegacySdes().orElse(true)) {
				throw new IllegalArgumentException(FNC_NAME + ": KMD must not be for legacy SDES key management");
			}
			// generate outbound KMD (with its own SSRC)
			SrtxpKmd outboundKmd = SrtxpKmd.createForMikeyWithDefaults(
					SrtxpMki.of(1, SrtxpKmd.DEFAULT_MKI_LEN),
					siForSs.getSsrcOutboundPtr()
				);
			siForSs.getKmdOutboundPtr().setKmd(outboundKmd, idSubStreamPtr);
			//
			addKeymgmtHeader(outboundKmd, output);
		}

		//throw new RtspProtoInvalidRequestException("SETUP not implemented");
	}

	private void buildRequest_teardown() {
		/*
		 * Example:
		 *   "TEARDOWN rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   "CSeq: 8"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 */

		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCommonHeaders()";

		// CSeq
		{
			if (inputDataRequ.getCseqNrToSend().getCseq32bit().orElse(-1L) < 1L) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": CSeq to send must be >= 1");
			}
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.copyFrom(inputDataRequ.getCseqNrToSend());
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.DATE);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Session
		if (! inputDataRequ.rrIdSession.isEmpty()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.copyFrom(inputDataRequ.rrIdSession);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth(Client)
		if (! inputDataRequ.requAuthClient.getAuthNonce().isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.AUTH_CLIENT);
			hdEntry.hdValAuthClient.authUser = inputDataRequ.requAuthClient.getAuthUser();
			hdEntry.hdValAuthClient.authUri = output.resourceUrl;
			hdEntry.hdValAuthClient.authRealm = inputDataRequ.requAuthClient.getAuthRealm();
			hdEntry.hdValAuthClient.authNonce = inputDataRequ.requAuthClient.getAuthNonce();
			try {
				hdEntry.hdValAuthClient.authResp = RtspProtoAuthDigest.computeAuthResponse(
						inputDataRequ.requAuthClient.getAuthUser(),
						inputDataRequ.requAuthClient.getAuthPlainPassword(),
						hdEntry.hdValAuthClient.authUri,
						output.messageType,
						hdEntry.hdValAuthClient.authRealm,
						hdEntry.hdValAuthClient.authNonce
					);
			} catch (IllegalArgumentException e) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": Computing authentication response failed: " +
						e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// UserAgent
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.USERAGENT);
			hdEntry.hdValUserAgent.userAgentStr = cfgSenderAppNameAndVersion;
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void addContentTypeHeader(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentTypeHeader()";

		if (output.messageType != RtspProtoMessageType.ANNOUNCE &&
				output.messageType != RtspProtoMessageType.GET_PARAMETER && output.messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Content-Type header only allowed for " +
					"DESCRIBE/GET_PARAMETER/SET_PARAMETER messages");
		}
		RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_TYPE);
		hdEntry.hdValContType.contentType = (output.messageType == RtspProtoMessageType.ANNOUNCE ?
				RtspMimeType.SDP : RtspMimeType.PARAMETERS);
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addContentLangHeader(
				@NonNull String contLang,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentLangHeader()";

		if (output.messageType != RtspProtoMessageType.ANNOUNCE && output.messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Content-Language header only allowed for " +
					"ANNOUNCE/SET_PARAMETER messages");
		}
		if (contLang.isBlank()) {
			return;
		}
		RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LANG);
		hdEntry.hdValContLang.contentLangStr = contLang;
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addKeymgmtHeader(
				@NonNull SrtxpKmd kmdOutbound,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addKeymgmtHeader()";

		if (kmdOutbound.getMetaIsForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD must not be for legacy SDES key management");
		}
		String tmpCryptoStrB64;
		try {
			// modern MIKEY key management
			tmpCryptoStrB64 = MikeyGenerator.generate(kmdOutbound);
		} catch (SrtxpSecurityException e) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
		RtspProtoHeaderEntryRequest entry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.KEYMGMT);
		entry.hdValKeymgmt.proto = RtspKeymgmtProto.MIKEY;
		entry.hdValKeymgmt.uriStr = output.resourceUrl;
		entry.hdValKeymgmt.dataStr = tmpCryptoStrB64;
		output.headers.put(RtspHeaderKey.KEYMGMT, entry);
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
