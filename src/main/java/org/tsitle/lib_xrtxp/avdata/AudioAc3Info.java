package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class AudioAc3Info implements CodecInfoInterface<AudioAc3Info>, Cloneable {

	/** Sampling rates according to ATSC A/52:2015 Table 5.6 */
	public enum Samplerate {
		SR48000(0),
		SR44100(1),
		SR32000(2),
		RESERVED(3),
		UNKNOWN(255);

		public final int index;
		Samplerate(int index) {
			this.index = index;
		}
		public static @NonNull Samplerate of(int index) {
			for (Samplerate rate : Samplerate.values()) {
				if (rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
		public int getHz() {
			return switch (this) {
					case SR48000 -> 48000;
					case SR44100 -> 44100;
					case SR32000 -> 32000;
					default -> -1;
				};
		}
	}

	/** Nominal bit rates according to ATSC A/52:2015 Table 5.18 */
	public enum Bitrate {
		BR032,
		BR040,
		BR048,
		BR056,
		BR064,
		BR080,
		BR096,
		BR112,
		BR128,
		BR160,
		BR192,
		BR224,
		BR256,
		BR320,
		BR384,
		BR448,
		BR512,
		BR576,
		BR640,
		UNKNOWN;

		public int getBitrateAsInt() {
			return switch (this) {
					case BR032 -> 32;
					case BR040 -> 40;
					case BR048 -> 48;
					case BR056 -> 56;
					case BR064 -> 64;
					case BR080 -> 80;
					case BR096 -> 96;
					case BR112 -> 112;
					case BR128 -> 128;
					case BR160 -> 160;
					case BR192 -> 192;
					case BR224 -> 224;
					case BR256 -> 256;
					case BR320 -> 320;
					case BR384 -> 384;
					case BR448 -> 448;
					case BR512 -> 512;
					case BR576 -> 576;
					case BR640 -> 640;
					default -> -1;
				};
		}
	}

	/** Audio Coding Mode according to ATSC A/52:2015 Table 5.8 */
	public enum AudioCodingMode {
		/** Channel Array Ordering: Ch1, Ch2 */
		ACM_1PLUS1(0),
		/** Channel Array Ordering: C */
		ACM_1ZERO(1),
		/** Channel Array Ordering: L, R */
		ACM_2ZERO(2),
		/** Channel Array Ordering: L, C, R */
		ACM_3ZERO(3),
		/** Channel Array Ordering: L, R, S */
		ACM_2ONE(4),
		/** Channel Array Ordering: L, C, R, S */
		ACM_3ONE(5),
		/** Channel Array Ordering: L, R, SL, SR */
		ACM_2TWO(6),
		/** Channel Array Ordering: L, C, R, SL, SR */
		ACM_3TWO(7),
		UNKNOWN(255);

		public final int index;
		AudioCodingMode(int index) {
			this.index = index;
		}
		public static @NonNull AudioCodingMode of(int index) {
			for (AudioCodingMode acm : AudioCodingMode.values()) {
				if (acm.index == index) {
					return acm;
				}
			}
			return UNKNOWN;
		}
		public int getChannelCount() {
			return switch (this) {
					case ACM_1ZERO -> 1;
					case ACM_1PLUS1, ACM_2ZERO -> 2;
					case ACM_3ZERO, ACM_2ONE -> 3;
					case ACM_3ONE, ACM_2TWO -> 4;
					case ACM_3TWO -> 5;
					default -> -1;
				};
		}
	}

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Length of the complete Syncframe (including header) */
	public int frameLength;
	/** Samplerate of the audio data (2 bits) */
	public @NonNull Samplerate samplerate;
	/** Nominal Bit Rate of the audio data (6 bits) */
	public @NonNull Bitrate bitrate;
	/** Audio Coding Mode (3 bits) */
	public AudioCodingMode audioCodingMode;

	public AudioAc3Info() {
		reset();
	}

	@Override
	public int getPayloadOffset() {
		// the RTP payload starts with the Syncword
		return samplesOffset;
	}

	@Override
	public int getPayloadLength() {
		return samplesLength;
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		frameLength = 0;
		samplerate = Samplerate.UNKNOWN;
		bitrate = Bitrate.UNKNOWN;
		audioCodingMode = AudioCodingMode.UNKNOWN;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<AudioAc3Info> src) {
		reset();

		AudioAc3Info tmpSrc = (AudioAc3Info)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		frameLength = tmpSrc.frameLength;
		samplerate = tmpSrc.samplerate;
		bitrate = tmpSrc.bitrate;
		audioCodingMode = tmpSrc.audioCodingMode;
	}

	@Override
	public @NonNull AudioAc3Info clone() {
		try {
			return (AudioAc3Info)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", frameLength=" + Integer.toUnsignedString(frameLength) +
				", samplerate=" + samplerate +
				", bitrate=" + bitrate +
				", audioCodingMode=" + audioCodingMode +
				"]";
	}

	@Override
	public @NonNull String toString(boolean shortOutput) {
		return toString();
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(samplesOffset);
		baos.write(samplesLength);
		baos.write(frameLength);
		baos.write(samplerate.ordinal());
		baos.write(bitrate.ordinal());
		baos.write(audioCodingMode.ordinal());

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}
