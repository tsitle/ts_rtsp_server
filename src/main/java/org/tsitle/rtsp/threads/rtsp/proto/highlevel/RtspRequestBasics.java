package org.tsitle.rtsp.threads.rtsp.proto.highlevel;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;

public final class RtspRequestBasics {

	public @NonNull RtspMessageType messageType = RtspMessageType.UNKNOWN;
	public @NonNull RtspStatusCode statusCode = RtspStatusCode.OK;
	public final @NonNull RtspProtoRscUrl rscUrl = new RtspProtoRscUrl();

	private RtspRequestBasics() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid() { return (messageType != RtspMessageType.UNKNOWN && statusCode == RtspStatusCode.OK); }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspRequestBasics createUnknown() {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = RtspMessageType.UNKNOWN;
		return res;
	}

	public static @NonNull RtspRequestBasics createKnownWithError(
				@NonNull RtspMessageType messageType,
				@NonNull RtspStatusCode statusCode
			) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.statusCode = statusCode;
		return res;
	}

	public static @NonNull RtspRequestBasics createKnownWithOptionNotSupported(@NonNull RtspMessageType messageType) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.statusCode = RtspStatusCode.OPTION_NOT_SUPPORTED;
		return res;
	}

	public static @NonNull RtspRequestBasics createOk(
				@NonNull RtspMessageType messageType,
				@Nullable RtspProtoRscUrl rscUrl
			) {
		RtspRequestBasics resObj = new RtspRequestBasics();
		resObj.messageType = messageType;
		if (rscUrl != null) {
			resObj.rscUrl.copyFrom(rscUrl);
		}
		return resObj;
	}

}
