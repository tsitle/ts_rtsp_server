package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.RtspStaticSessionInfo;

import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public final class RtspProtoRequestBuilder extends RtspProtoBuilderBase {

	public RtspProtoRequestBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		super(logMsgInterface, rtxpTcpReadWriteInterface, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void sendRequestOptions(RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource)
			throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequestOptions()";

		Objects.requireNonNull(requestUrlInputOrStreamSource.subStreamId);
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);

		//
		internalSendRequest(FNC_NAME, RtspProtoMessageType.OPTIONS, tmpStreamInfo.inputSourceUrlSetup, new ArrayList<>());
	}

	public void sendRequestSrtxpRekey(RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource)
			throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendRequestSrtxpRekey()";

		Objects.requireNonNull(requestUrlInputOrStreamSource.subStreamId);
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);

		// get the StreamKmds object
		Objects.requireNonNull(rtspSessionInfo.clientIpAddr);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.clientIpAddr,
				requestUrlInputOrStreamSource.subStreamId,
				tmpStreamInfo.rtspSsrcId
			);
		//
		SrtxpKmd kmdOutbound;
		if (! tmpStreamKmds.isForLegacySdes) {
			kmdOutbound = SrtxpKmd.createWithDefaults(tmpStreamInfo.rtspSsrcId);
		} else {
			kmdOutbound = SrtxpKmd.createForLegacySdes(tmpStreamInfo.rtspSsrcId);
		}
		String tmpCryptoStr;
		try {
			if (! tmpStreamKmds.isForLegacySdes) {
				// modern MIKEY key management
				tmpCryptoStr = MikeyGenerator.generate(kmdOutbound);
			} else {
				// legacy SDES key management (SDP Security Descriptions RFC-4568)
				tmpCryptoStr = kmdOutbound.getMasterKeyAndSaltAsBase64();
			}
		} catch (SrtxpSecurityException e) {
			throw new IllegalStateException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}

		//
		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_CONTTYPE + " " + RTSP_RR_HEADER_PARAM_VAL_XXX_CT_MIKEY);

		//
		@SuppressWarnings("StringBufferReplaceableByString")
		StringBuilder tmpKmSb = new StringBuilder();
		tmpKmSb
				.append(RTSP_RR_HEADER_TOKEN_SET_KEYMGMT).append(" ")
				.append(RTSP_RR_HEADER_PARAM_KEY_SET_KM_PROT).append(RTSP_RR_HEADER_PARAM_VAL_SET_KM_MIKEY).append("; ")
				.append(RTSP_RR_HEADER_PARAM_KEY_SET_KM_URI).append("\"").append(tmpStreamInfo.inputSourceUrlSetup).append("\"; ")
				.append(RTSP_RR_HEADER_PARAM_KEY_SET_KM_DATA).append("\"").append(tmpCryptoStr).append("\"");
		contents.add(tmpKmSb.toString());
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_CONTLEN + " 0");

		//
		internalSendRequest(FNC_NAME, RtspProtoMessageType.SET_PARAMETER, tmpStreamInfo.inputSourceUrlSetup, contents);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalSendRequest(
				@NonNull String fncName,
				@NonNull RtspProtoMessageType messageType,
				@NonNull String uri,
				final @NonNull List<@NonNull String> contents
			) throws TcpSocketIoException {
		rtspSessionInfo.rtspServerSeqNrExpected = ++rtspSessionInfo.rtspServerSeqNrRequest;

		List<String> finalOutputLines = new ArrayList<>();

		finalOutputLines.add(messageType.name() + " " + uri + " " + rtspSessionInfo.lastRequestRtspProtoVersion + CRLF);
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
		rtxpTcpReadWriteInterface.writeRtspLines(finalOutputLines);

		//
		logDebug(fncName, "Sent request '" + messageType +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspServerSeqNrExpected + ")\n");
	}

}
