package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;

public final class RtspProtoHeaderEntryResponse extends RtspProtoHeaderEntryBase {

	public @NonNull RtspProtoHeaderTypeAuthServer hdValAuthServer = new RtspProtoHeaderTypeAuthServer();
	public @NonNull RtspProtoHeaderTypePublic hdValPublic = new RtspProtoHeaderTypePublic();
	public @NonNull RtspProtoHeaderTypeRtpinfo hdValRtpinfo = new RtspProtoHeaderTypeRtpinfo();
	public @NonNull RtspProtoHeaderTypeServer hdValServer = new RtspProtoHeaderTypeServer();
	public @NonNull RtspProtoHeaderTypeUnsupported hdValUnsupported = new RtspProtoHeaderTypeUnsupported();

	@SuppressWarnings("unused")
	public RtspProtoHeaderEntryResponse() {
		super();
	}

	public RtspProtoHeaderEntryResponse(@NonNull RtspHeaderKey hdKeyEn) {
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
			case PUBLIC:
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
				case PUBLIC -> hdValPublic.toString();
				case RTPINFO -> hdValRtpinfo.toString();
				case SERVER -> hdValServer.toString();
				case UNSUPPORTED -> hdValUnsupported.toString();
				default -> throw new IllegalArgumentException("Invalid header key for responses: " + hdKeyEn);
			};
	}

}
