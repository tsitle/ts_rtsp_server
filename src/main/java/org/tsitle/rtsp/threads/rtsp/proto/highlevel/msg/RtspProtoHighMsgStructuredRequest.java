package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.util.*;

public final class RtspProtoHighMsgStructuredRequest extends RtspProtoHighMsgStructuredBase {

	/** Resource URL without Query Parameters */
	public @NonNull String resourceUrl = "";
	/** URL Query Parameters */
	public final @NonNull Map<@NonNull String, @NonNull String> queryParams = new HashMap<>();

	/** Authentication credentials: username (from URL or WWW-Authenticate header) */
	public @NonNull String authUser = "";
	/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
	public @NonNull String authPlainPassword = "";

	/** Headers */
	public final @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryRequest> headers = new HashMap<>();

	/** Message body for 'ANNOUNCE' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull List<@NonNull String> bodyAnnounceSdp = new ArrayList<>();
	/** Message body for 'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull Set<@NonNull String> bodyGetParamKeys = new HashSet<>();
	/** Message body for 'SET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull Map<@NonNull String, @NonNull String> bodySetParamKv = new HashMap<>();

	public RtspProtoHighMsgStructuredRequest() {
		super();
	}

	@Override
	public @NonNull String toString() {
		String resS =
				getClass().getSimpleName() + " [" +
				internalToStringFirstPart() +
				", resourceUrl='" + resourceUrl + "'" +
				", queryParams=" + mapToString(queryParams) +
				", authUser='" + authUser + "', " +
				", authPlainPassword='" + authPlainPassword + "'" +
				", headers=" + headers;

		if (messageType == RtspMessageType.ANNOUNCE) {
			resS += ", bodyAnnounceSdp=" + listToString(bodyAnnounceSdp);
		} else if (messageType == RtspMessageType.GET_PARAMETER) {
			resS += ", bodyGetParamKeys=" + setToString(bodyGetParamKeys);
		} else if (messageType == RtspMessageType.SET_PARAMETER) {
			resS += ", bodySetParamKv=" + mapToString(bodySetParamKv);
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
