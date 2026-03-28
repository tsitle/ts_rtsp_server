package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * RTP packet types.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3551#section-6">RFC-3551 Section 6</a><br />
 * And <a href="https://datatracker.ietf.org/doc/html/rfc7798#section-7.2.1">RFC-7798 Section 7.2.1</a>
 */
public enum RtpPacketType {

	/*
	 * Payload type values in the range 96-127 MAY be defined dynamically.
	 * RFC-3551 Section 6
	 */

	/** Unknown */
	UNKNOWN((byte)255),
	/** Audio: AAC (clock rate 90000 Hz; samplerate variable; channels variable) */
	A_AAC((byte)105),  // dynamic
	/** Audio: PCMU (8 kHz clock rate / samplerate; mono; 8 bits per sample; G.711; mu-law scaling) */
	A_PCMU_8KHZ_MONO((byte)0),  // fixed
	/** Audio: PCMU (clock rate and samplerate variable; channels variable; 8 bits per sample; G.711; mu-law scaling) */
	A_PCMU_VAR((byte)106),  // dynamic
	/** Audio: Linear PCM (clock rate and samplerate variable; channels variable; unsigned 8 bits per sample only) */
	A_LINEAR_PCM_U08_VAR((byte)107),  // dynamic, RFC-3551 Section 4.5.10 + 6
	/** Audio: Linear PCM (44.1 kHz clock rate / samplerate; 1 channel; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_441K_MONO((byte)10),  // fixed, RFC-3551 Section 4.5.11 + 6
	/** Audio: Linear PCM (44.1 kHz clock rate / samplerate; 2 channels; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_441K_STEREO((byte)11),  // fixed, RFC-3551 Section 4.5.11 + 6
	/** Audio: Linear PCM (clock rate and samplerate variable; channels variable; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_VAR((byte)108),  // dynamic, RFC-3551 Section 4.5.11
	/** Video: MJPEG or JPEG (clock rate 90000 Hz) */
	V_JPEG((byte)26),  // fixed, RFC-3551 Section 5.2 + 6
	/** Video: H261 (clock rate 90000 Hz) */
	V_H261_UNSUPPORTED((byte)31),  // fixed, RFC-3551 Section 5.3 + 6
	/** Video: H263 as defined 1996 by ITU-T (clock rate 90000 Hz) */
	V_H263_1996_UNSUPPORTED((byte)34),  // fixed, RFC-3551 Section 5.4 + 6
	/** Video: H263 as defined 1998 by ITU-T (clock rate 90000 Hz) */
	V_H263_1998_UNSUPPORTED((byte)96),  // dynamic, RFC-3551 Section 5.5 + 6
	/** Video: H264 (clock rate 90000 Hz) */
	V_H264((byte)97),  // dynamic, custom payload type
	/** Video: H265 (clock rate 90000 Hz) */
	V_H265((byte)98);  // dynamic, custom payload type

	@SuppressWarnings("unused")
	public static final byte RTP_PAYLOAD_TYPE_CUSTOM_BOUNDARY_LOWER = 96;
	@SuppressWarnings("unused")
	public static final byte RTP_PAYLOAD_TYPE_CUSTOM_BOUNDARY_UPPER = 127;

	private final byte value;

	RtpPacketType(byte value) {
		this.value = value;
	}

	public byte getValue() {
		return value;
	}

	public static @NonNull RtpPacketType of(byte value) {
		for (RtpPacketType type : RtpPacketType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return UNKNOWN;
	}

	public boolean isVideo() {
		return switch(this) {
				case
						V_JPEG,
						V_H261_UNSUPPORTED,
						V_H263_1996_UNSUPPORTED,
						V_H263_1998_UNSUPPORTED,
						V_H264,
						V_H265
					-> true;
				default -> false;
			};
	}

	public boolean isPcmAudio() {
		return switch(this) {
				case
						A_PCMU_8KHZ_MONO,
						A_PCMU_VAR,
						A_LINEAR_PCM_U08_VAR,
						A_LINEAR_PCM_S16_441K_MONO,
						A_LINEAR_PCM_S16_441K_STEREO,
						A_LINEAR_PCM_S16_VAR
					-> true;
				default -> false;
			};
	}

	public boolean isAudio() {
		return (isPcmAudio() || this == A_AAC);
	}

	/**
	 * Check if this codec needs to have a single channel.
	 * @return True if mono, false otherwise
	 */
	public boolean isMonoAudio() {
		return switch(this) {
				case
						A_PCMU_8KHZ_MONO,
						A_LINEAR_PCM_S16_441K_MONO
					-> true;
				default -> false;
			};
	}

	/**
	 * Check if this codec needs to have two channels.
	 * @return True if stereo, false otherwise
	 */
	public boolean isStereoAudio() {
		return (this == A_LINEAR_PCM_S16_441K_STEREO);
	}

	public Optional<Integer> getPcmAudioBitsPerSample() {
		return switch (this) {
				case
						A_PCMU_8KHZ_MONO,
						A_PCMU_VAR,
						A_LINEAR_PCM_U08_VAR
					-> Optional.of(8);
				case
						A_LINEAR_PCM_S16_441K_MONO,
						A_LINEAR_PCM_S16_441K_STEREO,
						A_LINEAR_PCM_S16_VAR
					-> Optional.of(16);
				default -> Optional.empty();
			};
	}

	/** Get SDP codec name for the packet type (or codec) */
	public @NonNull String getSdpCodecName() {
		return switch (this) {
				case A_AAC -> "MPEG4-GENERIC";
				case A_PCMU_8KHZ_MONO, A_PCMU_VAR -> "PCMU";
				case A_LINEAR_PCM_U08_VAR -> "L8";
				case A_LINEAR_PCM_S16_441K_MONO, A_LINEAR_PCM_S16_441K_STEREO, A_LINEAR_PCM_S16_VAR -> "L16";
				case V_JPEG -> "JPEG";
				case V_H264 -> "H264";
				case V_H265 -> "H265";
				default -> throw new IllegalStateException("Unsupported codec: " + this);
			};
	}

	/** Get RTP Clock rate for the packet type (or codec) */
	public int getVideoCodecRtpClockrate() {
		return switch(this) {
				case V_JPEG, V_H264, V_H265 -> 90000;
				default -> throw new IllegalStateException("Unsupported video codec: " + this);
			};
	}

}
