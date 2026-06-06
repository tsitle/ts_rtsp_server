package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RtspProtoHighMsgStructuredResponse extends RtspProtoHighMsgStructuredBase {

	/** Headers */
	public @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> headers = new HashMap<>();

	/** Message body for 'DESCRIBE' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public @NonNull String bodyDescribeSdp = "";
	/** Message body for 'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public @NonNull Map<@NonNull String, @NonNull String> bodyGetParamKv = new HashMap<>();

	public RtspProtoHighMsgStructuredResponse() {
		super();

		this.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
	}

	@Override
	public @NonNull String toString() {
		String resS =
				getClass().getSimpleName() + " [" +
				internalToStringFirstPart() +
				", headers=" + headers;

		if (messageType == RtspMessageType.DESCRIBE) {
			resS += ", bodyDescribeSdp='" + cleanUpBodyString(bodyDescribeSdp) + "'";
		} else if (messageType == RtspMessageType.GET_PARAMETER) {
			resS += ", bodySetParamKv='" + mapToString(bodyGetParamKv) + "'";
		}
		return resS + "]";
	}

	public Optional<Integer> getHeaderCseq() {
		if (! headers.containsKey(RtspHeaderKey.CSEQ)) {
			return Optional.empty();
		}
		return headers.get(RtspHeaderKey.CSEQ).hdValCseq.getCseqNr32bit();
	}

	public Optional<String> getHeaderSessionId() {
		if (! headers.containsKey(RtspHeaderKey.SESSION)) {
			return Optional.empty();
		}
		if (headers.get(RtspHeaderKey.SESSION).hdValSession.sessionIdStr.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(headers.get(RtspHeaderKey.SESSION).hdValSession.sessionIdStr);
	}

}
