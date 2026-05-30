package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderEntry {

	public @NonNull RtspProtoLowHeaderKey hdKeyEn = RtspProtoLowHeaderKey.NONE;

	public @NonNull RtspProtoLowHeaderTypeAccept hdValAccept = new RtspProtoLowHeaderTypeAccept();
	public @NonNull RtspProtoLowHeaderTypeAuth hdValAuth = new RtspProtoLowHeaderTypeAuth();
	public @NonNull RtspProtoLowHeaderTypeCseq hdValCseq = new RtspProtoLowHeaderTypeCseq();
	public @NonNull RtspProtoLowHeaderTypeDate hdValDate = new RtspProtoLowHeaderTypeDate();
	public @NonNull RtspProtoLowHeaderTypeKeymgmt hdValKeymgmt = new RtspProtoLowHeaderTypeKeymgmt();
	public @NonNull RtspProtoLowHeaderTypePublic hdValPublic = new RtspProtoLowHeaderTypePublic();
	public @NonNull RtspProtoLowHeaderTypeRange hdValRange = new RtspProtoLowHeaderTypeRange();
	public @NonNull RtspProtoLowHeaderTypeRequire hdValRequire = new RtspProtoLowHeaderTypeRequire();
	public @NonNull RtspProtoLowHeaderTypeSession hdValSession = new RtspProtoLowHeaderTypeSession();
	public @NonNull RtspProtoLowHeaderTypeTransport hdValTransport = new RtspProtoLowHeaderTypeTransport();
	public @NonNull RtspProtoLowHeaderTypeUa hdValUserAgent = new RtspProtoLowHeaderTypeUa();

	@Override
	public @NonNull String toString() {
		return switch (hdKeyEn) {
				case NONE -> "[empty]";
				case ACCEPT -> hdValAccept.toString();
				case AUTH -> hdValAuth.toString();
				case CSEQ -> hdValCseq.toString();
				case DATE -> hdValDate.toString();
				case KEYMGMT -> hdValKeymgmt.toString();
				case PUBLIC -> hdValPublic.toString();
				case RANGE -> hdValRange.toString();
				case REQUIRE -> hdValRequire.toString();
				case SESSION -> hdValSession.toString();
				case TRANSPORT -> hdValTransport.toString();
				case USERAGENT -> hdValUserAgent.toString();
			};
	}

}
