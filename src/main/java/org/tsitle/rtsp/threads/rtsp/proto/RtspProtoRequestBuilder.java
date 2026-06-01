package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.RtspStaticSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;
import static org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowConstants.RTSP_RR_CMD_PROTOCOL_VERSION_1;

public final class RtspProtoRequestBuilder extends RtspProtoBuilderBase {

	public RtspProtoRequestBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		super(logMsgInterface, rtxpTcpReadWrite, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void sendRequestOptions(@NonNull String subStreamId) throws TcpSocketIoException {
		sendRequestOptions(subStreamIdToRuioss(subStreamId));
	}

	public void sendRequestOptions(RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource)
			throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequestOptions()";

		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = getStreamInfo(FNC_NAME, requestUrlInputOrStreamSource);

		//
		internalSendRequest(FNC_NAME, RtspProtoMessageType.OPTIONS, tmpStreamInfo.inputSourceUrlSetup, new ArrayList<>());
	}

	public @NonNull SrtxpKmd sendRequestSrtxpRekey(
				@NonNull String subStreamId,
				@NonNull Set<@NonNull RtspProtoMessageType> supportedMessageTypes
			) throws TcpSocketIoException, RtspInvalidRequestException {
		return sendRequestSrtxpRekey(subStreamIdToRuioss(subStreamId), supportedMessageTypes);
	}

	public @NonNull SrtxpKmd sendRequestSrtxpRekey(
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull Set<@NonNull RtspProtoMessageType> supportedMessageTypes
			) throws TcpSocketIoException, RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequestSrtxpRekey()";

		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = getStreamInfo(FNC_NAME, requestUrlInputOrStreamSource);

		// get the StreamKmds object
		Objects.requireNonNull(requestUrlInputOrStreamSource.subStreamId);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.getClientIpAddr(),
				requestUrlInputOrStreamSource.subStreamId,
				tmpStreamInfo.rtspSsrcId
			);

		//
		if ((tmpStreamKmds.isForLegacySdes && ! supportedMessageTypes.contains(RtspProtoMessageType.ANNOUNCE)) ||
				(! tmpStreamKmds.isForLegacySdes && ! supportedMessageTypes.contains(RtspProtoMessageType.SET_PARAMETER))) {
			throw new RtspInvalidRequestException(FNC_NAME + ": client does not support SRTxP re-keying");
		}
		Objects.requireNonNull(tmpStreamKmds.kmdOutbound);
		if (tmpStreamKmds.kmdOutbound.mki().isEmpty()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": cannot re-key when initial KMD had no MKI");
		}

		//
		if (! tmpStreamKmds.isForLegacySdes) {
			final long nextMki = tmpStreamKmds.kmdOutbound.mki().value() + 1;  // will automatically be wrapped around
			tmpStreamKmds.nextKmdOutbound = SrtxpKmd.createWithDefaults(nextMki, tmpStreamInfo.rtspSsrcId);
		} else {
			tmpStreamKmds.nextKmdOutbound = SrtxpKmd.createForLegacySdes(tmpStreamInfo.rtspSsrcId);
		}
		String tmpCryptoStr;
		try {
			if (! tmpStreamKmds.isForLegacySdes) {
				// modern MIKEY key management
				tmpCryptoStr = MikeyGenerator.generate(tmpStreamKmds.nextKmdOutbound);
			} else {
				// legacy SDES key management (SDP Security Descriptions RFC-4568)
				tmpCryptoStr = tmpStreamKmds.nextKmdOutbound.getMasterKeyAndSaltAsBase64();
			}
		} catch (SrtxpSecurityException e) {
			throw new IllegalStateException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}

		//
		if (! tmpStreamKmds.isForLegacySdes) {
			List<String> contents = new ArrayList<>();
			contents.add(RTSP_RR_HEADER_TOKEN_XXX_CONTTYPE + " " + RTSP_RR_HEADER_PARAM_VAL_XXX_CT_MIKEY);

			@SuppressWarnings("StringBufferReplaceableByString")
			StringBuilder tmpKmSb = new StringBuilder();
			tmpKmSb
					.append(RTSP_RR_HEADER_TOKEN_XXX_KEYMGMT).append(" ")
					.append(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT).append(RTSP_RR_HEADER_PARAM_VAL_XXX_KM_MIKEY).append("; ")
					.append(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI).append("\"").append(tmpStreamInfo.inputSourceUrlSetup).append("\"; ")
					.append(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA).append("\"").append(tmpCryptoStr).append("\"");
			contents.add(tmpKmSb.toString());
			contents.add(RTSP_RR_HEADER_TOKEN_XXX_CONTLEN + " 0");

			//
			internalSendRequest(FNC_NAME, RtspProtoMessageType.SET_PARAMETER, tmpStreamInfo.inputSourceUrlSetup, contents);
		} else {
			/*
			 * we need to send an ANNOUNCE request that contains the entire SDP.
			 * Only the 'a=crypto' line must change and use a different tag.
			 * The initial SDP would contain something like 'a=crypto:1 ...' and the new SDP
			 * would contain something like 'a=crypto:2 ...'.
			 */
			logWarn(FNC_NAME, "Re-keying legacy SDES key is not supported yet");
			throw new RtspInvalidRequestException(FNC_NAME + ": Re-keying legacy SDES key is not supported yet");
		}

		return tmpStreamKmds.nextKmdOutbound;
	}

	@SuppressWarnings("unused")
	public void sendRequestTeardown(@NonNull String inputSourceUrl) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequestTeardown()";

		//
		internalSendRequest(FNC_NAME, RtspProtoMessageType.TEARDOWN, inputSourceUrl, new ArrayList<>());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource subStreamIdToRuioss(@NonNull String subStreamId) {
		RequestBasicInfo.RequestUrlInputOrStreamSource ruioss = new RequestBasicInfo.RequestUrlInputOrStreamSource();
		ruioss.subStreamId = subStreamId;
		return ruioss;
	}

	private RtspStaticSessionInfo.@NonNull StreamInfo getStreamInfo(
				@NonNull String fncName,
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) {
		Objects.requireNonNull(requestUrlInputOrStreamSource.subStreamId);
		return RtspStaticSessionInfo.getStreamInfoOrThrow(fncName, requestUrlInputOrStreamSource.subStreamId);
	}

	private void internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType messageType,
				@NonNull String uri,
				final @NonNull List<@NonNull String> contents
			) throws TcpSocketIoException {
		rtspSessionInfo.rtspServerSeqNrExpected = ++rtspSessionInfo.rtspServerSeqNrRequest;

		List<String> finalOutputLines = new ArrayList<>();

		String tmpRtspProtoVersStr = (rtspSessionInfo.lastRequestRtspProtoVersion == RtspProtocolVersion.RTSP_V1_0 ?
				RTSP_RR_CMD_PROTOCOL_VERSION_1 : RTSP_RR_CMD_PROTOCOL_VERSION_2);
		finalOutputLines.add(messageType.name() + " " + uri + " " + tmpRtspProtoVersStr + CRLF);
		//
		finalOutputLines.add(RTSP_RR_HEADER_TOKEN_XXX_CSEQ + " " + rtspSessionInfo.rtspServerSeqNrExpected + CRLF);
		if (! rtspSessionInfo.rtspSessionId.isBlank()) {
			finalOutputLines.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId + CRLF);
		}
		finalOutputLines.add(RTSP_RR_HEADER_TOKEN_XXX_DATE + " " + buildDateString() + CRLF);
		//
		for (String entry : contents) {
			finalOutputLines.add(entry + CRLF);
		}
		finalOutputLines.add(CRLF);
		rtxpTcpReadWrite.writeRtspLines(finalOutputLines);

		//
		logDebug(fncName, "Sent request '" + messageType +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspServerSeqNrExpected + ")\n");
	}

}
