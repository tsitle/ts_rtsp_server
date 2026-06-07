package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public interface RtspProtoParameterGetterInterface {

	@NonNull Map<@NonNull String, @NonNull String> getAllRtspParameters(@NonNull String rtspSessionId);

}
