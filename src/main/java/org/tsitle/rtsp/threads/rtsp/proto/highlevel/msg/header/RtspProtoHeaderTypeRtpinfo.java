package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;

import java.util.Optional;

public class RtspProtoHeaderTypeRtpinfo {

	public static class SubStream {
		public @NonNull String urlStr = "";
		private int seqNr16bit = -1;
		private long rtpTimestamp32bit = -1L;
		private long ssrcId32bit = -1L;

		public void setSeqNr16bit(int seqNr16bit) throws RtspNumberRangeException {
			if (seqNr16bit < 0 || seqNr16bit > 0xFFFF) {
				throw new RtspNumberRangeException("seqNr16bit must be non-negative and within 16-bit range");
			}
			this.seqNr16bit = seqNr16bit;
		}

		public Optional<Short> getSeqNr16bit() {
			return (seqNr16bit < 0 ? Optional.empty() : Optional.of((short)seqNr16bit));
		}

		public void setRtpTimestamp32bit(long rtpTimestamp32bit) throws RtspNumberRangeException {
			if (rtpTimestamp32bit < 0L || rtpTimestamp32bit > 0xFFFFFFFFL) {
				throw new RtspNumberRangeException("rtpTimestamp32bit must be non-negative and within 32-bit range");
			}
			this.rtpTimestamp32bit = rtpTimestamp32bit;
		}

		public Optional<Integer> getRtpTimestamp32bit() {
			return (rtpTimestamp32bit < 0L ? Optional.empty() : Optional.of((int)rtpTimestamp32bit));
		}

		public void setSsrcId32bit(long ssrc32bit) throws RtspNumberRangeException {
			if (ssrc32bit < 1L || ssrc32bit > 0xFFFFFFFFL) {
				throw new RtspNumberRangeException("ssrc32bit must be between 1 and 0xFFFFFFFF, got: " + ssrc32bit);
			}
			this.ssrcId32bit = ssrc32bit;
		}

		public Optional<Integer> getSsrcId32bit() {
			return (ssrcId32bit < 0L ? Optional.empty() : Optional.of((int)ssrcId32bit));
		}

		@Override
		public @NonNull String toString() {
			return "[" +
					"urlStr='" + urlStr + "', " +
					optionalShortToStr("seqNr", getSeqNr16bit()) + ", " +
					optionalIntToStr("rtpTimestamp", getRtpTimestamp32bit()) + ", " +
					optionalIntToStr("ssrcId", getSsrcId32bit()) +
					"]";
		}

		@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
		private static @NonNull String optionalShortToStr(@NonNull String desc, @NonNull Optional<Short> value) {
			return desc + "=" + (value.isPresent() ? Short.toUnsignedInt(value.get()) : "unset");
		}

		@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
		private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
			return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
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
