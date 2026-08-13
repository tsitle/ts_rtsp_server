package org.tsitle.lib_xrtxp.packets.rtp;

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
	 * We leave 96 and 97 out since they are used by default by any other implementation for any kind of payload.
	 */

	/** Unknown */
	UNKNOWN((byte)255),
	/** Audio: AAC (clock rate 90000 Hz; samplerate variable; channels variable) */
	A_AAC((byte)(96 + 2)),  // dynamic
	/** Audio: AC-3 (clock rate and samplerate variable; channels variable) */
	A_AC3((byte)(96 + 3)),  // dynamic
	/** Audio: E-AC-3 (clock rate and samplerate variable; channels variable) */
	A_EAC3_UNSUPPORTED((byte)(96 + 4)),  // dynamic
	/** Audio: MPEG1-LayerIII aka MP3 (clock rate 90000 Hz, samplerate variable; channels mono or stereo) */
	A_MP3((byte)(14)),  // fixed
	/** Audio: Opus (clock rate and samplerate variable; channels mono or stereo) */
	A_OPUS((byte)(96 + 5)),  // dynamic
	/** Audio: PCMA (8 kHz clock rate / samplerate; mono; 8 bits per sample; G.711; a-law scaling) */
	A_PCMA_8KHZ_MONO((byte)8),  // fixed
	/** Audio: PCMA (clock rate and samplerate variable; channels variable; 8 bits per sample; G.711; a-law scaling) */
	A_PCMA_VAR((byte)(96 + 6)),  // dynamic
	/** Audio: PCMU (8 kHz clock rate / samplerate; mono; 8 bits per sample; G.711; mu-law scaling) */
	A_PCMU_8KHZ_MONO((byte)0),  // fixed
	/** Audio: PCMU (clock rate and samplerate variable; channels variable; 8 bits per sample; G.711; mu-law scaling) */
	A_PCMU_VAR((byte)(96 + 7)),  // dynamic
	/** Audio: Linear PCM (clock rate and samplerate variable; channels variable; unsigned 8 bits per sample only) */
	A_LINEAR_PCM_U08_VAR((byte)(96 + 8)),  // dynamic, RFC-3551 Section 4.5.10 + 6
	/** Audio: Linear PCM (44.1 kHz clock rate / samplerate; 1 channel; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_441K_MONO((byte)10),  // fixed, RFC-3551 Section 4.5.11 + 6
	/** Audio: Linear PCM (44.1 kHz clock rate / samplerate; 2 channels; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_441K_STEREO((byte)11),  // fixed, RFC-3551 Section 4.5.11 + 6
	/** Audio: Linear PCM (clock rate and samplerate variable; channels variable; signed 16 bits per sample; Big-Endian) */
	A_LINEAR_PCM_S16_VAR((byte)(96 + 9)),  // dynamic, RFC-3551 Section 4.5.11
	/** Video: MJPEG or JPEG (clock rate 90000 Hz) */
	V_MJPEG((byte)26),  // fixed, RFC-3551 Section 5.2 + 6
	/** Video: H261 (clock rate 90000 Hz) */
	V_H261_UNSUPPORTED((byte)31),  // fixed, RFC-3551 Section 5.3 + 6
	/** Video: H263 as defined 1996 by ITU-T (clock rate 90000 Hz) */
	V_H263_1996_UNSUPPORTED((byte)34),  // fixed, RFC-3551 Section 5.4 + 6
	/** Video: H263 as defined 1998 by ITU-T (clock rate 90000 Hz) */
	V_H263_1998_UNSUPPORTED((byte)(96 + 15)),  // dynamic, RFC-3551 Section 5.5 + 6
	/** Video: H264 (clock rate 90000 Hz) */
	V_H264((byte)(96 + 16)),  // dynamic, custom payload type
	/** Video: H265 (clock rate 90000 Hz) */
	V_H265((byte)(96 + 17)),  // dynamic, custom payload type
	/** Video: H265 (clock rate 90000 Hz) */
	V_VP8((byte)(96 + 18));  // dynamic, custom payload type

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
			if (type != UNKNOWN && type.getValue() == value) {
				return type;
			}
		}
		return UNKNOWN;
	}

	public boolean isVideo() {
		return switch(this) {
				case
						V_H261_UNSUPPORTED,
						V_H263_1996_UNSUPPORTED,
						V_H263_1998_UNSUPPORTED,
						V_H264,
						V_H265,
						V_MJPEG,
						V_VP8
					-> true;
				default -> false;
			};
	}

	public boolean isPcmAudio() {
		return switch(this) {
				case
						A_PCMA_8KHZ_MONO,
						A_PCMA_VAR,
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
		return (isPcmAudio() ||
				this == A_AAC ||
				this == A_AC3 ||
				this == A_EAC3_UNSUPPORTED ||
				this == A_MP3 ||
				this == A_OPUS
			);
	}

	/**
	 * Check if this codec needs to have a single channel.
	 * @return True if mono, false otherwise
	 */
	public boolean isPcmMonoAudio() {
		return switch(this) {
				case
						A_PCMA_8KHZ_MONO,
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
	public boolean isPcmStereoAudio() {
		return (this == A_LINEAR_PCM_S16_441K_STEREO);
	}

	public Optional<Integer> getPcmAudioBitsPerSample() {
		return switch (this) {
				case
						A_PCMA_8KHZ_MONO,
						A_PCMA_VAR,
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
				case A_AC3 -> "AC3";
				case A_EAC3_UNSUPPORTED -> "EAC3";
				case A_MP3 -> "MPA";
				case A_OPUS -> "OPUS";
				case A_PCMA_8KHZ_MONO, A_PCMA_VAR -> "PCMA";
				case A_PCMU_8KHZ_MONO, A_PCMU_VAR -> "PCMU";
				case A_LINEAR_PCM_U08_VAR -> "L8";
				case A_LINEAR_PCM_S16_441K_MONO, A_LINEAR_PCM_S16_441K_STEREO, A_LINEAR_PCM_S16_VAR -> "L16";
				case V_H264 -> "H264";
				case V_H265 -> "H265";
				case V_MJPEG -> "JPEG";
				case V_VP8 -> "VP8";
				default -> throw new IllegalStateException(getClass().getSimpleName() + ".getSdpCodecName(): " +
						"Unsupported codec: " + this);
			};
	}

	/** Get RTP Clock rate for the packet type (or codec) */
	public int getVideoCodecRtpClockrate() {
		return switch(this) {
				case V_H264, V_H265, V_MJPEG, V_VP8 -> 90000;
				default -> throw new IllegalStateException(getClass().getSimpleName() + ".getVideoCodecRtpClockrate(): " +
						"Unsupported video codec: " + this);
			};
	}

}
