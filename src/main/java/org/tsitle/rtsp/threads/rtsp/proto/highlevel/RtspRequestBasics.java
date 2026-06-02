package org.tsitle.rtsp.threads.rtsp.proto.highlevel;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

public class RtspRequestBasics {

	public static class RequestUrlInputOrStreamSource {
		public @Nullable String subStreamId = null;
		public @Nullable String inputSourceId = null;
		public int streamSourceId = -1;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspMessageType messageType = RtspMessageType.UNKNOWN;
	public @NonNull RtspStatusCode statusCode = RtspStatusCode.OK;
	public @Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;
	public @NonNull String unsupportedOptionName = "";

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

	public static @NonNull RtspRequestBasics createKnownWithOptionNotSupported(
				@NonNull RtspMessageType messageType,
				@NonNull String optionName
			) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.statusCode = RtspStatusCode.OPTION_NOT_SUPPORTED;
		res.unsupportedOptionName = optionName;
		return res;
	}

	public static @NonNull RtspRequestBasics createOk(
				@NonNull RtspMessageType messageType,
				@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) {
		RtspRequestBasics res = new RtspRequestBasics();
		res.messageType = messageType;
		res.requestUrlInputOrStreamSource = requestUrlInputOrStreamSource;
		return res;
	}

}
