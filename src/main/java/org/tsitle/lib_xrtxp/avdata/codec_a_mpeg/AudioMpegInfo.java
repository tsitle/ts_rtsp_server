package org.tsitle.lib_xrtxp.avdata.codec_a_mpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class AudioMpegInfo implements CodecInfoInterface<AudioMpegInfo>, Cloneable {

	/** MPEG Audio Version according to ISO/IEC 11172-3 */
	public enum MpegAudioVersion {
		/** MPEG Version 2.5 (later extension of MPEG 2) */
		V2_5(0),
		RESERVED(1),
		/** MPEG Version 2 (ISO/IEC 13818-3) */
		V2(2),
		/** MPEG Version 1 (ISO/IEC 11172-3) */
		V1(3),
		UNKNOWN(255);

		public final int index;
		MpegAudioVersion(int index) {
			this.index = index;
		}
		public static @NonNull MpegAudioVersion of(int index) {
			for (MpegAudioVersion mav : MpegAudioVersion.values()) {
				if (mav != UNKNOWN && mav.index == index) {
					return mav;
				}
			}
			return UNKNOWN;
		}
	}

	/** MPEG Layer according to ISO/IEC 11172-3 */
	public enum MpegLayer {
		RESERVED(0),
		L3(1),
		L2(2),
		L1(3),
		UNKNOWN(255);

		public final int index;
		MpegLayer(int index) {
			this.index = index;
		}
		public static @NonNull MpegLayer of(int index) {
			for (MpegLayer rate : MpegLayer.values()) {
				if (rate != UNKNOWN && rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
	}

	/** Channel Modes according to ISO/IEC 11172-3 */
	public enum ChannelMode {
		STEREO(0),
		JOINT_STEREO(1),
		/** 2 mono channels */
		DUAL_CHANNEL(2),
		SINGLE_CHANNEL(3),
		UNKNOWN(255);

		public final int index;
		ChannelMode(int index) {
			this.index = index;
		}
		public static @NonNull ChannelMode of(int index) {
			for (ChannelMode cm : ChannelMode.values()) {
				if (cm != UNKNOWN && cm.index == index) {
					return cm;
				}
			}
			return UNKNOWN;
		}
		public int getChannelCount() {
			if (this == STEREO || this == JOINT_STEREO || this == DUAL_CHANNEL) {
				return 2;
			}
			if (this == SINGLE_CHANNEL) {
				return 1;
			}
			return 0;
		}
	}

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Length of the complete MPEG Audio frame (including header) */
	public int frameLength;
	public @NonNull MpegAudioVersion mpegAudioVersion;
	public @NonNull MpegLayer mpegLayer;
	int bitRateIx;
	int sampleRateIx;
	public @NonNull ChannelMode channelMode;

	public AudioMpegInfo() {
		reset();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public @NonNull String getValidationErrorMsg() {
		return "";
	}

	@Override
	public int getPayloadOffset() {
		return 0;  // we need to include the MPEG frame header in the RTP packet
	}

	@Override
	public int getPayloadLength() {
		return frameLength;
	}

	public int getBitRateKbps() {
		int resI = -1;
		if (mpegAudioVersion == MpegAudioVersion.V1 && mpegLayer == MpegLayer.L1) {
			resI = AudioMpegBitrateV1L1.of(bitRateIx).getKbps();
		} else if (mpegAudioVersion == MpegAudioVersion.V1 && mpegLayer == MpegLayer.L2) {
			resI = AudioMpegBitrateV1L2.of(bitRateIx).getKbps();
		} else if (mpegAudioVersion == MpegAudioVersion.V1 && mpegLayer == MpegLayer.L3) {
			resI = AudioMpegBitrateV1L3.of(bitRateIx).getKbps();
		} else if ((mpegAudioVersion == MpegAudioVersion.V2 || mpegAudioVersion == MpegAudioVersion.V2_5) &&
				mpegLayer == MpegLayer.L1) {
			resI = AudioMpegBitrateV2L1.of(bitRateIx).getKbps();
		} else if ((mpegAudioVersion == MpegAudioVersion.V2 || mpegAudioVersion == MpegAudioVersion.V2_5) &&
				(mpegLayer == MpegLayer.L2 || mpegLayer == MpegLayer.L3)) {
			resI = AudioMpegBitrateV2L23.of(bitRateIx).getKbps();
		}
		return resI;
	}

	public int getSampleRateHz() {
		return switch (mpegAudioVersion) {
				case V1 -> switch (sampleRateIx) {
						case 0 -> 44100;
						case 1 -> 48000;
						case 2 -> 32000;
						default -> -1;
					};
				case V2 -> switch (sampleRateIx) {
						case 0 -> 22050;
						case 1 -> 24000;
						case 2 -> 16000;
						default -> -1;
					};
				case V2_5 -> switch (sampleRateIx) {
						case 0 -> 11025;
						case 1 -> 12000;
						case 2 -> 8000;
						default -> -1;
					};
				default -> -1;
			};
	}

	@SuppressWarnings("unused")
	public int getSamplesPerFrame() {
		if ((mpegAudioVersion == MpegAudioVersion.V1 && mpegLayer == MpegLayer.L3) ||
				mpegLayer == MpegLayer.L2) {
			return 1152;
		}
		if (mpegLayer == MpegLayer.L3) {
			return 576;
		}
		if (mpegLayer == MpegLayer.L1) {
			return 384;
		}
		return -1;
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		frameLength = 0;

		mpegAudioVersion = MpegAudioVersion.UNKNOWN;
		mpegLayer = MpegLayer.UNKNOWN;
		bitRateIx = -1;
		sampleRateIx = -1;
		channelMode = ChannelMode.UNKNOWN;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<AudioMpegInfo> src) {
		reset();

		AudioMpegInfo tmpSrc = (AudioMpegInfo)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		frameLength = tmpSrc.frameLength;

		mpegAudioVersion = tmpSrc.mpegAudioVersion;
		mpegLayer = tmpSrc.mpegLayer;
		bitRateIx = tmpSrc.bitRateIx;
		sampleRateIx = tmpSrc.sampleRateIx;
		channelMode = tmpSrc.channelMode;
	}

	@Override
	public @NonNull AudioMpegInfo clone() {
		try {
			return  (AudioMpegInfo)super.clone();
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
				", mpegAudioVersion=" + mpegAudioVersion +
				", mpegLayer=" + mpegLayer +
				", bitRate=" + getBitRateKbps() +
				", sampleRate=" + getSampleRateHz() +
				", channelMode=" + channelMode +
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

		baos.write(mpegAudioVersion.ordinal());
		baos.write(mpegLayer.ordinal());
		baos.write(bitRateIx);
		baos.write(sampleRateIx);
		baos.write(channelMode.ordinal());

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}
