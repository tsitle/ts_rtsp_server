package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

public interface RtspProtoUserAuthInterface {

	boolean authenticate(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspProtoMessageType messageType
			);

	boolean checkAccessToInputSource(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspProtoIdInputSource idInputSource
			);

}
