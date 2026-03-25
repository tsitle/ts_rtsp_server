package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AudioAacInfo implements CodecInfoInterface<AudioAacInfo>, Cloneable {

	/** Sampling rates according to ISO/IEC 14496-3:2001(E) Table 1.10 */
	public enum SampleRate {
		SR96000(0),
		SR88200(1),
		SR64000(2),
		SR48000(3),
		SR44100(4),
		SR32000(5),
		SR24000(6),
		SR22050(7),
		SR16000(8),
		SR12000(9),
		SR11025(10),
		SR08000(11),
		SR07350(12),
		ESCAPE(15),
		UNKNOWN(255);

		public final int index;
		SampleRate(int index) {
			this.index = index;
		}
		public static SampleRate of(int index) {
			for (SampleRate rate : SampleRate.values()) {
				if (rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
		public int getHz() {
			return switch (this) {
				case SR96000 -> 96000;
				case SR88200 -> 88200;
				case SR64000 -> 64000;
				case SR48000 -> 48000;
				case SR44100 -> 44100;
				case SR32000 -> 32000;
				case SR24000 -> 24000;
				case SR22050 -> 22050;
				case SR16000 -> 16000;
				case SR12000 -> 12000;
				case SR11025 -> 11025;
				case SR08000 -> 8000;
				case SR07350 -> 7350;
				default -> -1;
			};
		}
	}

	/** Audio object types according to ISO/IEC 14496-3:2001(E) Table 1.1 */
	public enum AudioObjectType {
		AAC_MAIN(1),
		AAC_LC(2),
		AAC_SSR(3),
		AAC_LTP(4),
		RESERVED5(5),
		AAC_SCALABLE(6),
		UNKNOWN(255);

		public final int index;
		AudioObjectType(int index) {
			this.index = index;
		}
		public static AudioObjectType of(int index) {
			for (AudioObjectType rate : AudioObjectType.values()) {
				if (rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
	}

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Length of the complete AAC frame (including header) */
	public int frameLength;
	/** Samplerate of the audio data (4 bits) */
	public @NonNull SampleRate samplerate;
	/** MPEG-4 Audio Object Type (2 bits) */
	public @NonNull AudioObjectType audioObjectType;
	/** Channel configuration (3 bits) */
	public int channelConfiguration;
	/** AudioSpecificConfig for SDP 'fmtp config' as hex string */
	public @NonNull String sdpFmtpConfigHex;

	public AudioAacInfo() {
		reset();
	}

	@Override
	public int getPayloadOffset() {
		// we don't skip the ADTS header for the RTP payload
		return 0;
	}

	@Override
	public int getPayloadLength() {
		// since we don't skip the ADTS header for the RTP payload, the payload length equals the frame length
		return frameLength;
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		frameLength = 0;
		samplerate = SampleRate.UNKNOWN;
		audioObjectType = AudioObjectType.UNKNOWN;
		channelConfiguration = 0;
		sdpFmtpConfigHex = "";
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<AudioAacInfo> src) {
		reset();

		AudioAacInfo tmpSrc = (AudioAacInfo)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		frameLength = tmpSrc.frameLength;
		samplerate = tmpSrc.samplerate;
		audioObjectType = tmpSrc.audioObjectType;
		channelConfiguration = tmpSrc.channelConfiguration;
		//noinspection StringOperationCanBeSimplified
		sdpFmtpConfigHex = new String(tmpSrc.sdpFmtpConfigHex);
	}

	@Override
	public AudioAacInfo clone() {
		try {
			AudioAacInfo clone = (AudioAacInfo)super.clone();
			//noinspection StringOperationCanBeSimplified
			clone.sdpFmtpConfigHex = new String(sdpFmtpConfigHex);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", frameLength=" + Integer.toUnsignedString(frameLength) +
				", samplerate=" + samplerate +
				", aot=" + audioObjectType +
				", channelConfiguration=" + channelConfiguration +
				"]";
	}

	@Override
	public String toString(boolean shortOutput) {
		return toString();
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(samplesOffset);
		baos.write(samplesLength);
		baos.write(frameLength);
		baos.write(samplerate.ordinal());
		baos.write(audioObjectType.ordinal());
		baos.write(channelConfiguration);
		try {
			baos.write(sdpFmtpConfigHex.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}
