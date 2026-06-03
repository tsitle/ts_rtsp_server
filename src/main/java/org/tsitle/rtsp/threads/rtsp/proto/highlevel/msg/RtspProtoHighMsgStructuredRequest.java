package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RtspProtoHighMsgStructuredRequest extends RtspProtoHighMsgStructuredBase {

	/** Resource URL without Query Parameters */
	public @NonNull String resourceUrl = "";
	/** URL Query Parameters */
	public @NonNull Map<@NonNull String, @NonNull String> queryParams = new HashMap<>();

	/** Authentication credentials: username (from URL or WWW-Authenticate header) */
	public @NonNull String authUser = "";
	/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
	public @NonNull String authPlainPassword = "";

	/** Headers */
	public @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryRequest> headers = new HashMap<>();

	public RtspProtoHighMsgStructuredRequest() {
		super();
	}

	private @NonNull String queryParamsToString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (Map.Entry<@NonNull String, @NonNull String> tmpEntry : queryParams.entrySet()) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry.getKey()).append("='").append(tmpEntry.getValue()).append("'");
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public @NonNull String toString() {
		String resS = getClass().getSimpleName() + " [";
		resS += internalToString(true);
		resS += "resourceUrl='" + resourceUrl + "', ";
		resS += "queryParams=" + queryParamsToString() + ", ";
		resS += "authUser='" + authUser + "', ";
		resS += "authPlainPassword='" + authPlainPassword + "', ";
		resS += "headers=" + headers + ", ";
		resS += internalToString(false);
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
