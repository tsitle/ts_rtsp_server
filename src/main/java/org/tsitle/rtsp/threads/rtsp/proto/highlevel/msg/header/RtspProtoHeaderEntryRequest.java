package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;

public final class RtspProtoHeaderEntryRequest extends RtspProtoHeaderEntryBase {

	public @NonNull RtspProtoHeaderTypeAccept hdValAccept = new RtspProtoHeaderTypeAccept();
	public @NonNull RtspProtoHeaderTypeAuthClient hdValAuthClient = new RtspProtoHeaderTypeAuthClient();
	public @NonNull RtspProtoHeaderTypeKeymgmt hdValKeymgmt = new RtspProtoHeaderTypeKeymgmt();
	public @NonNull RtspProtoHeaderTypeRequire hdValRequire = new RtspProtoHeaderTypeRequire();
	public @NonNull RtspProtoHeaderTypeUa hdValUserAgent = new RtspProtoHeaderTypeUa();

	public RtspProtoHeaderEntryRequest() {
		super();
	}

	@SuppressWarnings("unused")
	public RtspProtoHeaderEntryRequest(@NonNull RtspHeaderKey hdKeyEn) {
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
			case ACCEPT:
			case AUTH_CLIENT:
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
		if (hdKeyEn == RtspHeaderKey.NONE) {
			throw new IllegalStateException("Header key is not set");
		}
		if (baseClassHandlesHdKeyType) {
			return super.toString();
		}
		return switch (hdKeyEn) {
				case ACCEPT -> hdValAccept.toString();
				case AUTH_CLIENT -> hdValAuthClient.toString();
				case KEYMGMT -> hdValKeymgmt.toString();
				case REQUIRE -> hdValRequire.toString();
				case USERAGENT -> hdValUserAgent.toString();
				default -> throw new IllegalArgumentException("Invalid header key for requests: " + hdKeyEn);
			};
	}

}
