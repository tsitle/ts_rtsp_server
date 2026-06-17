package org.tsitle.lib.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.lib.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;

public interface RtspProtoParameterNotifyRcvdInterface {

	void notifyReceivedRtspParameters(
			@NonNull RtspProtoIdSession idSession,
			@NonNull RtspProtoDataCntGetSetParamKvs paramKvs
		);

}
