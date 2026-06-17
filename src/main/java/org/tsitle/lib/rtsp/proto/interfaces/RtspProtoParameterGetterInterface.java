package org.tsitle.lib.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSession;

public interface RtspProtoParameterGetterInterface {

	@NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull RtspProtoIdSession idSession);

}
