package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RtspProtoLowMsgStructuredRequest extends RtspProtoLowMsgStructuredBase {

	/** Authentication credentials: username (from URL or WWW-Authenticate header) */
	public @NonNull String authUser = "";
	/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
	public @NonNull String authPlainPassword = "";

	/** Headers */
	public @NonNull Map<@NonNull RtspProtoLowHeaderKey, @NonNull RtspProtoLowHeaderEntryRequest> headers = new HashMap<>();

	public RtspProtoLowMsgStructuredRequest() {
		super();
	}

	@Override
	public @NonNull String toString() {
		String resS = getClass().getSimpleName() + " [";
		resS += internalToString(true);
		resS += "authUser='" + authUser + "', ";
		resS += "authPlainPassword='" + authPlainPassword + "', ";
		resS += "headers=" + headers + ", ";
		resS += internalToString(false);
		return resS + "]";
	}

	public Optional<Integer> getHeaderCseq() {
		if (! headers.containsKey(RtspProtoLowHeaderKey.CSEQ)) {
			return Optional.empty();
		}
		return headers.get(RtspProtoLowHeaderKey.CSEQ).hdValCseq.getCseqNr32bit();
	}

	public Optional<String> getHeaderSessionId() {
		if (! headers.containsKey(RtspProtoLowHeaderKey.SESSION)) {
			return Optional.empty();
		}
		if (headers.get(RtspProtoLowHeaderKey.SESSION).hdValSession.sessionIdStr.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(headers.get(RtspProtoLowHeaderKey.SESSION).hdValSession.sessionIdStr);
	}

}
