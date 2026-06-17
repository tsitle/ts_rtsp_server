package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;

public interface RtspProtoParameterNotifyRcvdInterface {

	void notifyReceivedRtspParameters(
			@NonNull RtspProtoIdSession idSession,
			@NonNull RtspProtoDataCntGetSetParamKvs paramKvs
		);

}
