package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

import java.util.*;

public final class RtspProtoHighMsgStructuredResponse extends RtspProtoHighMsgStructuredBase {

	/** Headers */
	public final @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> headers = new HashMap<>();

	/** Message body for 'DESCRIBE' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull List<String> bodyDescribeSdp = new ArrayList<>();
	/** Message body for 'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull Map<@NonNull String, @NonNull String> bodyGetParamKv = new HashMap<>();
	/** Message body for 'GET_PARAMETER'/'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull Set<@NonNull String> bodyGetSetInvalidParams = new HashSet<>();

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
			resS += ", bodyDescribeSdp=" + listToString(bodyDescribeSdp);
		} else if (messageType == RtspMessageType.GET_PARAMETER || messageType == RtspMessageType.SET_PARAMETER) {
			if (! bodyGetSetInvalidParams.isEmpty()) {
				resS += ", bodyGetSetInvalidParams=" + setToString(bodyGetSetInvalidParams);
			} else if (messageType == RtspMessageType.GET_PARAMETER) {
				resS += ", bodyGetParamKv=" + mapToString(bodyGetParamKv);
			}
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
