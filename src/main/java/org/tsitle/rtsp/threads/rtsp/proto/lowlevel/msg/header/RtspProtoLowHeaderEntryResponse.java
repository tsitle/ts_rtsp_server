package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoLowHeaderEntryResponse extends RtspProtoLowHeaderEntryBase {

	public @NonNull RtspProtoLowHeaderTypeRtpinfo hdValRtpinfo = new RtspProtoLowHeaderTypeRtpinfo();
	public @NonNull RtspProtoLowHeaderTypeServer hdValServer = new RtspProtoLowHeaderTypeServer();

	public RtspProtoLowHeaderEntryResponse() {
		super();
	}

	@SuppressWarnings("unused")
	public RtspProtoLowHeaderEntryResponse(@NonNull RtspProtoLowHeaderKey hdKeyEn) {
		super(hdKeyEn);

		setHdKey(hdKeyEn);
	}

	@Override
	public void setHdKey(@NonNull RtspProtoLowHeaderKey hdKeyEn) {
		super.setHdKey(hdKeyEn);
		if (baseClassHandlesHdKeyType) {
			return;
		}
		switch (hdKeyEn) {
			case RTPINFO:
			case SERVER:
				this.hdKeyEn = hdKeyEn;
				break;
			default:
				throw new IllegalArgumentException("Invalid header key for responses: " + hdKeyEn);
		}
	}

	@Override
	public @NonNull String toString() {
		if (hdKeyEn == RtspProtoLowHeaderKey.NONE) {
			throw new IllegalStateException("Header key is not set");
		}
		if (baseClassHandlesHdKeyType) {
			return super.toString();
		}
		return switch (hdKeyEn) {
				case RTPINFO -> hdValRtpinfo.toString();
				case SERVER -> hdValServer.toString();
				default -> throw new IllegalArgumentException("Invalid header key for responses: " + hdKeyEn);
			};
	}

}
