package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoLowHeaderEntryRequest extends RtspProtoLowHeaderEntryBase {

	public @NonNull RtspProtoLowHeaderTypeAccept hdValAccept = new RtspProtoLowHeaderTypeAccept();
	public @NonNull RtspProtoLowHeaderTypeKeymgmt hdValKeymgmt = new RtspProtoLowHeaderTypeKeymgmt();
	public @NonNull RtspProtoLowHeaderTypeRequire hdValRequire = new RtspProtoLowHeaderTypeRequire();
	public @NonNull RtspProtoLowHeaderTypeUa hdValUserAgent = new RtspProtoLowHeaderTypeUa();

	public RtspProtoLowHeaderEntryRequest() {
		super();
	}

	@SuppressWarnings("unused")
	public RtspProtoLowHeaderEntryRequest(@NonNull RtspProtoLowHeaderKey hdKeyEn) {
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
			case ACCEPT:
			case KEYMGMT:
			case REQUIRE:
			case USERAGENT:
				this.hdKeyEn = hdKeyEn;
				break;
			default:
				throw new IllegalArgumentException("Invalid header key for requests: " + hdKeyEn);
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
				case ACCEPT -> hdValAccept.toString();
				case KEYMGMT -> hdValKeymgmt.toString();
				case REQUIRE -> hdValRequire.toString();
				case USERAGENT -> hdValUserAgent.toString();
				default -> throw new IllegalArgumentException("Invalid header key for requests: " + hdKeyEn);
			};
	}

}
