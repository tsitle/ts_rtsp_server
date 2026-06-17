package org.tsitle.lib_xrtxp.rtsp.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamNames;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdp;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;

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

	public Optional<Long> getHeaderCseq() {
		if (! headers.containsKey(RtspHeaderKey.CSEQ)) {
			return Optional.empty();
		}
		return headers.get(RtspHeaderKey.CSEQ).hdValCseq.cseqNr.getCseq32bit();
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

		if (messageType == RtspProtoMessageType.ANNOUNCE) {
			resS += ", bodyAnnounceSdp=" + bodyAnnounceSdp;
		} else if (messageType == RtspProtoMessageType.GET_PARAMETER) {
			resS += ", bodyGetParamNames=" + bodyGetParamNames;
		} else if (messageType == RtspProtoMessageType.SET_PARAMETER) {
			resS += ", bodySetParamKv=" + bodySetParamKv;
		}
		return resS + "]";
	}

}
