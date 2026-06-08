package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;

public interface RtspProtoParameterGetterInterface {

	RtspSessionInfo.@NonNull DataGetSetParamKvs getAllRtspParameters(@NonNull String rtspSessionId);

}
