package org.tsitle.lib.rtsp.proto.highlevel;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoStatusCode;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRscUrl;

public final class RtspRequestBasics {

	public @NonNull RtspProtoMessageType messageType = RtspProtoMessageType.UNKNOWN;
	public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.OK;
	public final @NonNull RtspProtoRscUrl rscUrl = new RtspProtoRscUrl();

	private RtspRequestBasics() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid() { return (messageType != RtspProtoMessageType.UNKNOWN && statusCode == RtspProtoStatusCode.OK); }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspRequestBasics createUnknown() {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = RtspProtoMessageType.UNKNOWN;
		return res;
	}

	public static @NonNull RtspRequestBasics createKnownWithError(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoStatusCode statusCode
			) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.statusCode = statusCode;
		return res;
	}

	public static @NonNull RtspRequestBasics createKnownWithOptionNotSupported(@NonNull RtspProtoMessageType messageType) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.statusCode = RtspProtoStatusCode.OPTION_NOT_SUPPORTED;
		return res;
	}

	public static @NonNull RtspRequestBasics createOk(
				@NonNull RtspProtoMessageType messageType,
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
