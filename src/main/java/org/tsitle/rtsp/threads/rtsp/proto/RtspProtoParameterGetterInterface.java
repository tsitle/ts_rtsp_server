package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;

public interface RtspProtoParameterGetterInterface {

	@NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull String rtspSessionId);

}
