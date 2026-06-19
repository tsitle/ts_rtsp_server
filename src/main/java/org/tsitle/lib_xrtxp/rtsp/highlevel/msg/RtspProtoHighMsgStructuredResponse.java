package org.tsitle.lib_xrtxp.rtsp.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamNames;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;

import java.util.*;

public final class RtspProtoHighMsgStructuredResponse extends RtspProtoHighMsgStructuredBase {

	/** Headers */
	public final @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> headers = new HashMap<>();

	/** Message body for 'DESCRIBE' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull RtspProtoDataCntSdpRaw bodyDescribeSdp = new RtspProtoDataCntSdpRaw();
	/** Message body for 'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull RtspProtoDataCntGetSetParamKvs bodyGetParamKv = new RtspProtoDataCntGetSetParamKvs();
	/** Message body for 'GET_PARAMETER'/'GET_PARAMETER' (requires the headers 'CONTENT_TYPE' and 'CONTENT_LENGTH') */
	public final @NonNull RtspProtoDataCntGetSetParamNames bodyGetSetInvalidParams = new RtspProtoDataCntGetSetParamNames();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoHighMsgStructuredResponse() {
		super();

		this.statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
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

	@Override
	public @NonNull String toString() {
		String resS =
				getClass().getSimpleName() + " [" +
				internalToStringFirstPart() +
				", headers=" + headers;

		if (messageType == RtspProtoMessageType.DESCRIBE) {
			resS += ", bodyDescribeSdp=" + bodyDescribeSdp;
		} else if (messageType == RtspProtoMessageType.GET_PARAMETER || messageType == RtspProtoMessageType.SET_PARAMETER) {
			if (! bodyGetSetInvalidParams.isParamNamesEmpty()) {
				resS += ", bodyGetSetInvalidParams=" + bodyGetSetInvalidParams;
			} else if (messageType == RtspProtoMessageType.GET_PARAMETER) {
				resS += ", bodyGetParamKv=" + bodyGetParamKv;
			}
		}
		return resS + "]";
	}

}
