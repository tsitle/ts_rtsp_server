package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamNames;

public interface RtspProtoParameterNotifyInvalidInterface {

	void notifyInvalidRtspParameters(
			@NonNull RtspProtoIdSession idSession,
			@NonNull RtspProtoDataCntGetSetParamNames invalidParams
		);

}
