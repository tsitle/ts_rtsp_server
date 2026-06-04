package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;

import java.util.Optional;

public final class RtspProtoHighRequestBuilder {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final boolean isRequestFromClient;

	public RtspProtoHighRequestBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				boolean isRequestFromClient
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
		this.isRequestFromClient = isRequestFromClient;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredRequest buildRequest(
				@NonNull RtspMessageType requestMessageType,
				@NonNull String resourceUrl,
				@Nullable String subStreamId,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable String getOrSetParameterName
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest()";

		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();

		resObj.rtspProtoVersion = rtspSessionInfo.lastRequestRtspProtoVersion;
		resObj.statusCode = RtspStatusCode.OK;
		resObj.messageType = requestMessageType;

		//
		resObj.resourceUrl = resourceUrl;

		//
		addCommonHeaders(resObj);

		//
		switch (requestMessageType) {
			case ANNOUNCE -> buildRequest_announce(kmdsOutbound, resObj);
			case DESCRIBE -> buildRequest_describe(resObj);
			case GET_PARAMETER -> buildRequest_getParameter(getOrSetParameterName, resObj);
			case OPTIONS -> buildRequest_options(resObj);
			case PAUSE -> buildRequest_pause(resObj);
			case PLAY -> buildRequest_play(resObj);
			case RECORD -> buildRequest_record(resObj);
			case REDIRECT -> buildRequest_redirect(resObj);
			case SET_PARAMETER -> buildRequest_setParameter(kmdsOutbound, subStreamId, getOrSetParameterName, resObj);
			case SETUP -> buildRequest_setup(resObj);
			case TEARDOWN -> buildRequest_teardown(resObj);
			default -> throw new RtspInvalidRequestException(FNC_NAME + ": Unsupported message type: " +
					requestMessageType);
		}

		//
		if (! resObj.body.isBlank()) {
			if (! resObj.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
				resObj.body += RtspProtoLowMsgConstants.CRLF;
			}
			addContentLengthHeader(resObj);
		}

		System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);  // @TODO

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRequest_announce(
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@NonNull RtspProtoHighMsgStructuredRequest msg
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_announce()";

		// @TODO build complete SDP with optional KMDs if SRTxP encryption is enabled

		logError(FNC_NAME, "ANNOUNCE is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": ANNOUNCE is not supported yet");

		/*
		 * Re-keying legacy SDES key:
		 * we need to send an ANNOUNCE request that contains the entire SDP.
		 * Only the 'a=crypto' line must change and use a different tag.
		 * The initial SDP would contain something like 'a=crypto:1 ...' and the new SDP
		 * would contain something like 'a=crypto:2 ...'.
		 */
		/*
		if (! kmd.isForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": SrtxpKmd must be for legacy SDES key management");
		}
		// legacy SDES key management (SDP Security Descriptions RFC-4568)
		String tmpCryptoStrB64 = kmd.getMasterKeyAndSaltAsBase64();
		*/
	}

	private void buildRequest_describe(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	private void buildRequest_getParameter(
				@Nullable String parameterName,
				@NonNull RtspProtoHighMsgStructuredRequest msg
			) {
		// @TODO handle parameterName
	}

	private void buildRequest_options(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	private void buildRequest_pause(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildRequest_play(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_play()";

		// @TODO set some headers

		logError(FNC_NAME, "PLAY is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": PLAY is not supported yet");
	}

	private void buildRequest_record(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	private void buildRequest_redirect(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	private void buildRequest_setParameter(
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable String subStreamId,
				@Nullable String parameterName,
				@NonNull RtspProtoHighMsgStructuredRequest msg
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setParameter()";

		// @TODO handle parameterName

		// set the SRTxP Key Management Data for a SET_PARAMETER request used for re-keying
		if (kmdsOutbound == null) {
			return;
		}
		if (subStreamId == null) {
			throw new IllegalArgumentException(FNC_NAME + ": subStreamId must not be null when " +
					"setting SRTxP key management data for SET_PARAMETER request");
		}
		Optional<SrtxpKmd> tmpOptKmd = kmdsOutbound.getKmdForSubStream(subStreamId);
		if (tmpOptKmd.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD for Sub-Stream not found");
		}
		SrtxpKmd tmpKmd = tmpOptKmd.get();
		if (tmpKmd.isForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD must not be for legacy SDES key management");
		}
		String tmpCryptoStrB64;
		try {
			// modern MIKEY key management
			tmpCryptoStrB64 = MikeyGenerator.generate(tmpKmd);
		} catch (SrtxpSecurityException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
		RtspProtoHeaderEntryRequest entry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.KEYMGMT);
		entry.hdValKeymgmt.proto = RtspKeymgmtProto.MIKEY;
		entry.hdValKeymgmt.uriStr = msg.resourceUrl;
		entry.hdValKeymgmt.dataStr = tmpCryptoStrB64;
		msg.headers.put(RtspHeaderKey.KEYMGMT, entry);
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void buildRequest_setup(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setup()";

		// @TODO set some headers
		logError(FNC_NAME, "SETUP is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": SETUP is not supported yet");
	}

	private void buildRequest_teardown(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCommonHeaders()";

		// CSeq
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			try {
				hdEntry.hdValCseq.setCseqNr32bit(++rtspSessionInfo.seqNr_respRem_expected);
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Setting CSeq failed: " + e.getMessage());
			}
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.DATE);
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Session
		if (! rtspSessionInfo.rtspSessionId.isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.sessionIdStr = rtspSessionInfo.rtspSessionId;
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth(Client)
		if (! rtspSessionInfo.authInfo.authNonceServer.isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.AUTH_CLIENT);
			hdEntry.hdValAuthClient.authUri = msg.resourceUrl;
			hdEntry.hdValAuthClient.authRealm = rtspSessionInfo.authInfo.authRealmClient;
			hdEntry.hdValAuthClient.authNonce = rtspSessionInfo.authInfo.authNonceServer;
			try {
				hdEntry.hdValAuthClient.authResp = RtspProtoAuthDigest.computeAuthResponse(
						rtspSessionInfo.authInfo.authUser,
						rtspSessionInfo.authInfo.authPlainPassword,
						hdEntry.hdValAuthClient.authUri,
						msg.messageType,
						hdEntry.hdValAuthClient.authRealm,
						hdEntry.hdValAuthClient.authNonce
					);
			} catch (IllegalArgumentException e) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Computing authentication response failed: " +
						e.getMessage());
			}
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void addContentLengthHeader(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentLengthHeader()";

		RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LEN);
		try {
			hdEntry.hdValContLen.setContentLen32bit(msg.body.length());
		} catch (RtspNumberRangeException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Setting Content-Length failed: " + e.getMessage());
		}
		msg.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
