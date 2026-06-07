package org.tsitle.rtsp.threads.rtsp.proto.highlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

public final class RtspResponseBasics {

	public @NonNull RtspStatusCode statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;

	private RtspResponseBasics() { }

	public static @NonNull RtspResponseBasics createDefault(@NonNull RtspStatusCode statusCode) {
		RtspResponseBasics resObj = new RtspResponseBasics();
		resObj.statusCode = statusCode;
		return resObj;
	}

	public static @NonNull RtspResponseBasics createInternalServerError() {
		return new RtspResponseBasics();
	}

}
