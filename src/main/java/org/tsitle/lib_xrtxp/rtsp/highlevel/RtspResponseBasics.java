package org.tsitle.lib_xrtxp.rtsp.highlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;

public final class RtspResponseBasics {

	public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;

	private RtspResponseBasics() { }

	public static @NonNull RtspResponseBasics createDefault(@NonNull RtspProtoStatusCode statusCode) {
		RtspResponseBasics resObj = new RtspResponseBasics();
		resObj.statusCode = statusCode;
		return resObj;
	}

	public static @NonNull RtspResponseBasics createInternalServerError() {
		return new RtspResponseBasics();
	}

}
