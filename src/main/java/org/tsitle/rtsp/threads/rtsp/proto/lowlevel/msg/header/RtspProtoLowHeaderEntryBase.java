package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderEntryBase {

	protected @NonNull RtspProtoLowHeaderKey hdKeyEn = RtspProtoLowHeaderKey.NONE;
	protected boolean baseClassHandlesHdKeyType = false;

	public @NonNull RtspProtoLowHeaderTypeAuth hdValAuth = new RtspProtoLowHeaderTypeAuth();
	public @NonNull RtspProtoLowHeaderTypeContBase hdValContBase = new RtspProtoLowHeaderTypeContBase();
	public @NonNull RtspProtoLowHeaderTypeContType hdValContType = new RtspProtoLowHeaderTypeContType();
	public @NonNull RtspProtoLowHeaderTypeCseq hdValCseq = new RtspProtoLowHeaderTypeCseq();
	public @NonNull RtspProtoLowHeaderTypeDate hdValDate = new RtspProtoLowHeaderTypeDate();
	public @NonNull RtspProtoLowHeaderTypePublic hdValPublic = new RtspProtoLowHeaderTypePublic();
	public @NonNull RtspProtoLowHeaderTypeRange hdValRange = new RtspProtoLowHeaderTypeRange();
	public @NonNull RtspProtoLowHeaderTypeSession hdValSession = new RtspProtoLowHeaderTypeSession();
	public @NonNull RtspProtoLowHeaderTypeTransport hdValTransport = new RtspProtoLowHeaderTypeTransport();

	protected RtspProtoLowHeaderEntryBase() { }

	protected RtspProtoLowHeaderEntryBase(@NonNull RtspProtoLowHeaderKey hdKeyEn) {
		setHdKey(hdKeyEn);
	}

	public @NonNull RtspProtoLowHeaderKey getHdKey() {
		if (hdKeyEn == RtspProtoLowHeaderKey.NONE) {
			throw new IllegalStateException("Header key is not set");
		}
		return hdKeyEn;
	}

	protected void setHdKey(@NonNull RtspProtoLowHeaderKey hdKeyEn) {
		if (hdKeyEn == RtspProtoLowHeaderKey.NONE) {
			throw new IllegalArgumentException("Invalid header key: NONE");
		}
		switch (hdKeyEn) {
			case AUTH:
			case CONTENT_BASE:
			case CONTENT_TYPE:
			case CSEQ:
			case DATE:
			case PUBLIC:
			case RANGE:
			case SESSION:
			case TRANSPORT:
				this.hdKeyEn = hdKeyEn;
				this.baseClassHandlesHdKeyType = true;
				break;
			default:
				this.hdKeyEn = RtspProtoLowHeaderKey.NONE;
				this.baseClassHandlesHdKeyType = false;
				break;
		}
	}

	@Override
	public @NonNull String toString() {
		return switch (hdKeyEn) {
				case NONE -> "[empty]";
				case AUTH -> hdValAuth.toString();
				case CONTENT_BASE -> hdValContBase.toString();
				case CONTENT_TYPE -> hdValContType.toString();
				case CSEQ -> hdValCseq.toString();
				case DATE -> hdValDate.toString();
				case PUBLIC -> hdValPublic.toString();
				case RANGE -> hdValRange.toString();
				case SESSION -> hdValSession.toString();
				case TRANSPORT -> hdValTransport.toString();
				default -> throw new IllegalArgumentException("Invalid header key for base class: " + hdKeyEn);
			};
	}

}
