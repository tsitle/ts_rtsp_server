package org.tsitle.lib.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdInputSource;

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
