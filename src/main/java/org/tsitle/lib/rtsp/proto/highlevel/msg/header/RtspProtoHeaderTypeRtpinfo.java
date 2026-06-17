package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

import java.util.Optional;

public final class RtspProtoHeaderTypeRtpinfo {

	public static class SubStream {
		public @NonNull String urlStr = "";
		public final @NonNull RtspProtoRtpSeqNr seqNr = RtspProtoRtpSeqNr.ofEmpty();
		public final @NonNull RtspProtoRtpTimestamp rtpTimestamp = RtspProtoRtpTimestamp.ofEmpty();
		public final @NonNull RtspProtoIdXsrc ssrcId = RtspProtoIdXsrc.ofEmpty();

		@Override
		public @NonNull String toString() {
			return "[" +
					"urlStr='" + urlStr + "'" +
					", seqNr=" + seqNr +
					", rtpTimestamp=" + rtpTimestamp +
					", ssrcId=" + ssrcId +
					"]";
		}
	}

	private @Nullable SubStream subStream1 = null;
	private @Nullable SubStream subStream2 = null;

	public void setSubStream1(@NonNull SubStream subStream) {
		this.subStream1 = subStream;
	}

	public Optional<SubStream> getSubStream1() {
		return Optional.ofNullable(subStream1);
	}

	public void setSubStream2(@NonNull SubStream subStream) {
		this.subStream2 = subStream;
	}

	public Optional<SubStream> getSubStream2() {
		return Optional.ofNullable(subStream2);
	}

	@Override
	public @NonNull String toString() {
		String resS = "[";
		if (subStream1 == null && subStream2 == null) {
			resS += "no substreams";
		} else {
			if (subStream1 != null) {
				resS += "subStream1=" + subStream1;
			}
			if (subStream2 != null) {
				if (subStream1 != null) {
					resS += ", ";
				}
				resS += "subStream2=" + subStream2;
			}
		}
		resS += "]";
		return resS;
	}

}
