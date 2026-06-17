package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;

public interface RtspProtoParameterGetterInterface {

	@NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull RtspProtoIdSession idSession);

}
