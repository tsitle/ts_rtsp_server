package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;

public final class RtspProtoLowHeaderEntryResponse extends RtspProtoLowHeaderEntryBase {

	public @NonNull RtspProtoLowHeaderTypeAuthServer hdValAuthServer = new RtspProtoLowHeaderTypeAuthServer();
	public @NonNull RtspProtoLowHeaderTypeRtpinfo hdValRtpinfo = new RtspProtoLowHeaderTypeRtpinfo();
	public @NonNull RtspProtoLowHeaderTypeServer hdValServer = new RtspProtoLowHeaderTypeServer();
	public @NonNull RtspProtoLowHeaderTypeUnsupported hdValUnsupported = new RtspProtoLowHeaderTypeUnsupported();

	@SuppressWarnings("unused")
	public RtspProtoLowHeaderEntryResponse() {
		super();
	}

	public RtspProtoLowHeaderEntryResponse(@NonNull RtspHeaderKey hdKeyEn) {
		super(hdKeyEn);

		setHdKey(hdKeyEn);
	}

	@Override
	public void setHdKey(@NonNull RtspHeaderKey hdKeyEn) {
		super.setHdKey(hdKeyEn);
		if (baseClassHandlesHdKeyType) {
			return;
		}
		switch (hdKeyEn) {
			case AUTH_SERVER:
			case RTPINFO:
			case SERVER:
			case UNSUPPORTED:
				this.hdKeyEn = hdKeyEn;
				break;
			default:
				throw new IllegalArgumentException("Invalid header key for responses: " + hdKeyEn);
		}
	}

	@Override
	public @NonNull String toString() {
		if (hdKeyEn == RtspHeaderKey.NONE) {
			throw new IllegalStateException("Header key is not set");
		}
		if (baseClassHandlesHdKeyType) {
			return super.toString();
		}
		return switch (hdKeyEn) {
				case AUTH_SERVER -> hdValAuthServer.toString();
				case RTPINFO -> hdValRtpinfo.toString();
				case SERVER -> hdValServer.toString();
				case UNSUPPORTED -> hdValUnsupported.toString();
				default -> throw new IllegalArgumentException("Invalid header key for responses: " + hdKeyEn);
			};
	}

}
