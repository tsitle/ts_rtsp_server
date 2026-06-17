package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.lowlevel.RtspHeaderKey;

public class RtspProtoHeaderEntryBase {

	protected @NonNull RtspHeaderKey hdKeyEn = RtspHeaderKey.NONE;
	protected boolean baseClassHandlesHdKeyType = false;

	public @NonNull RtspProtoHeaderTypeConnection hdValConnection = new RtspProtoHeaderTypeConnection();
	public @NonNull RtspProtoHeaderTypeContBase hdValContBase = new RtspProtoHeaderTypeContBase();
	public @NonNull RtspProtoHeaderTypeContEnc hdValContEnc = new RtspProtoHeaderTypeContEnc();
	public @NonNull RtspProtoHeaderTypeContLang hdValContLang = new RtspProtoHeaderTypeContLang();
	public @NonNull RtspProtoHeaderTypeContLen hdValContLen = new RtspProtoHeaderTypeContLen();
	public @NonNull RtspProtoHeaderTypeContType hdValContType = new RtspProtoHeaderTypeContType();
	public @NonNull RtspProtoHeaderTypeCseq hdValCseq = new RtspProtoHeaderTypeCseq();
	public @NonNull RtspProtoHeaderTypeDate hdValDate = new RtspProtoHeaderTypeDate();
	public @NonNull RtspProtoHeaderTypeRange hdValRange = new RtspProtoHeaderTypeRange();
	public @NonNull RtspProtoHeaderTypeSession hdValSession = new RtspProtoHeaderTypeSession();
	public @NonNull RtspProtoHeaderTypeTransport hdValTransport = new RtspProtoHeaderTypeTransport();

	protected RtspProtoHeaderEntryBase() { }

	protected RtspProtoHeaderEntryBase(@NonNull RtspHeaderKey hdKeyEn) {
		setHdKey(hdKeyEn);
	}

	public @NonNull RtspHeaderKey getHdKey() {
		if (hdKeyEn == RtspHeaderKey.NONE) {
			throw new IllegalStateException("Header key is not set");
		}
		return hdKeyEn;
	}

	protected void setHdKey(@NonNull RtspHeaderKey hdKeyEn) {
		if (hdKeyEn == RtspHeaderKey.NONE) {
			throw new IllegalArgumentException("Invalid header key: NONE");
		}
		switch (hdKeyEn) {
			case CONNECTION:
			case CONTENT_BASE:
			case CONTENT_ENC:
			case CONTENT_LANG:
			case CONTENT_LEN:
			case CONTENT_TYPE:
			case CSEQ:
			case DATE:
			case RANGE:
			case SESSION:
			case TRANSPORT:
				this.hdKeyEn = hdKeyEn;
				this.baseClassHandlesHdKeyType = true;
				break;
			default:
				this.hdKeyEn = RtspHeaderKey.NONE;
				this.baseClassHandlesHdKeyType = false;
				break;
		}
	}

	@Override
	public @NonNull String toString() {
		return switch (hdKeyEn) {
				case NONE -> "[empty]";
				case CONNECTION -> hdValConnection.toString();
				case CONTENT_BASE -> hdValContBase.toString();
				case CONTENT_ENC -> hdValContEnc.toString();
				case CONTENT_LANG -> hdValContLang.toString();
				case CONTENT_LEN -> hdValContLen.toString();
				case CONTENT_TYPE -> hdValContType.toString();
				case CSEQ -> hdValCseq.toString();
				case DATE -> hdValDate.toString();
				case RANGE -> hdValRange.toString();
				case SESSION -> hdValSession.toString();
				case TRANSPORT -> hdValTransport.toString();
				default -> throw new IllegalArgumentException("Invalid header key for base class: " + hdKeyEn);
			};
	}

}
