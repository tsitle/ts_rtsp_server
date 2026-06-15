package org.tsitle.rtsp.threads.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;

public interface RtspProtoParameterGetterInterface {

	@NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull RtspProtoIdSession idSession);

}
