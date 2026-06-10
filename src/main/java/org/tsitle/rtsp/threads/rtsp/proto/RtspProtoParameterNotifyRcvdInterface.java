package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamNames;

public interface RtspProtoParameterNotifyRcvdInterface {

	void notifyReceivedRtspParameters(
			@NonNull RtspProtoIdSession idSession,
			@NonNull RtspProtoDataCntGetSetParamKvs paramKvs
		);

}
