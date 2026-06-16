package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

import java.util.Optional;

public final class RtspProtoHeaderTypeRtpinfo {

	public static class SubStream {
		public @NonNull String urlStr = "";
		private int seqNr16bit = -1;
		private long rtpTimestamp32bit = -1L;
		private final @NonNull RtspProtoIdXsrc ssrcId = new RtspProtoIdXsrc();

		public void setSeqNr16bit(int seqNr16bit) throws RtspProtoNumberRangeException {
			if (seqNr16bit < 0 || seqNr16bit > 0xFFFF) {
				throw new RtspProtoNumberRangeException("seqNr16bit must be non-negative and within 16-bit range");
			}
			this.seqNr16bit = seqNr16bit;
		}

		public Optional<Short> getSeqNr16bit() {
			return (seqNr16bit < 0 ? Optional.empty() : Optional.of((short)seqNr16bit));
		}

		public void setRtpTimestamp32bit(long rtpTimestamp32bit) throws RtspProtoNumberRangeException {
			if (rtpTimestamp32bit < 0L || rtpTimestamp32bit > 0xFFFFFFFFL) {
				throw new RtspProtoNumberRangeException("rtpTimestamp32bit must be non-negative and within 32-bit range");
			}
			this.rtpTimestamp32bit = rtpTimestamp32bit;
		}

		public Optional<Integer> getRtpTimestamp32bit() {
			return (rtpTimestamp32bit < 0L ? Optional.empty() : Optional.of((int)rtpTimestamp32bit));
		}

		public void setSsrcId(@NonNull RtspProtoIdXsrc value) {
			ssrcId.copyFrom(value);
		}

		public @NonNull RtspProtoIdXsrc getSsrcId() {
			return ssrcId.clone();
		}

		@Override
		public @NonNull String toString() {
			return "[" +
					"urlStr='" + urlStr + "'" +
					", seqNr=" + (getSeqNr16bit().isPresent() ? Integer.toUnsignedString(seqNr16bit) : "unset") +
					", rtpTimestamp=" + (getRtpTimestamp32bit().isPresent() ? Long.toUnsignedString(rtpTimestamp32bit) : "unset") +
					", ssrcId=" + (ssrcId.isEmpty() ? "unset" : ssrcId.toHexString(true)) +
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
