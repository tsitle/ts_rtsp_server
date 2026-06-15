package org.tsitle.rtsp.threads.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;

public interface RtspProtoUserAuthInterface {

	boolean authenticate(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspMessageType messageType
			);

	boolean checkAccessToInputSource(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspProtoIdInputSource idInputSource
			);

}
