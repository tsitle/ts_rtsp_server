package org.tsitle.lib.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.lib.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamNames;

public interface RtspProtoParameterNotifyInvalidInterface {

	void notifyInvalidRtspParameters(
			@NonNull RtspProtoIdSession idSession,
			@NonNull RtspProtoDataCntGetSetParamNames invalidParams
		);

}
