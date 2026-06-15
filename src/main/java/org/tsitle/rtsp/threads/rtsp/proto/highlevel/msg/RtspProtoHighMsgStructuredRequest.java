package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamNames;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;

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
	public final @NonNull RtspProtoDataCntSdp bodyAnnounceSdp = new RtspProtoDataCntSdp();
	/** Message body for 'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull RtspProtoDataCntGetSetParamNames bodyGetParamNames = new RtspProtoDataCntGetSetParamNames();
	/** Message body for 'SET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull RtspProtoDataCntGetSetParamKvs bodySetParamKv = new RtspProtoDataCntGetSetParamKvs();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoHighMsgStructuredRequest() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Integer> getHeaderCseq() {
		if (! headers.containsKey(RtspHeaderKey.CSEQ)) {
			return Optional.empty();
		}
		return headers.get(RtspHeaderKey.CSEQ).hdValCseq.getCseqNr32bit();
	}

	public Optional<RtspProtoIdSession> getHeaderSessionId() {
		if (! headers.containsKey(RtspHeaderKey.SESSION)) {
			return Optional.empty();
		}
		if (headers.get(RtspHeaderKey.SESSION).hdValSession.idSession.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(headers.get(RtspHeaderKey.SESSION).hdValSession.idSession);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String mapToString(@NonNull Map<@NonNull String, @Nullable String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (Map.Entry<@NonNull String, @Nullable String> tmpEntry : input.entrySet()) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(tmpEntry.getKey()).append("'=");
			if (tmpEntry.getValue() == null) {
				sb.append("unset");
			} else {
				sb.append("'").append(tmpEntry.getValue()).append("'");
			}
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
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
			resS += ", bodyAnnounceSdp=" + bodyAnnounceSdp;
		} else if (messageType == RtspMessageType.GET_PARAMETER) {
			resS += ", bodyGetParamNames=" + bodyGetParamNames;
		} else if (messageType == RtspMessageType.SET_PARAMETER) {
			resS += ", bodySetParamKv=" + bodySetParamKv;
		}
		return resS + "]";
	}

}
