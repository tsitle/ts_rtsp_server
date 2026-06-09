package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspUnknownRtspParamException;

public interface RtspProtoParameterSetterInterface {

	void setRtspParameter(
			@NonNull RtspProtoIdSession idSession,
			@NonNull String contentLanguage,
			@NonNull String key,
			@NonNull String value
		) throws RtspUnknownRtspParamException;

}
