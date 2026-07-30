package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.jspecify.annotations.NonNull;

import java.util.HexFormat;

/**
 * Helper for adding ADTS headers to AAC packets.
 */
public final class FfmpegHelperBsfAacWithAdts implements FfmpegHelperBsfAacInterface {

	/** ADTS profile: 0=Main,1=LC,2=SSR,3=reserved */
	private final int profile;
	/** 0..12 (per MPEG-4 table) */
	private final int samplingFreqIndex;
	/** 1..7 typically */
	private final int channelConfig;

	private FfmpegHelperBsfAacWithAdts(int profile, int samplingFreqIndex, int channelConfig) {
		this.profile = profile;
		this.samplingFreqIndex = samplingFreqIndex;
		this.channelConfig = channelConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull FfmpegHelperBsfAacWithAdts fromAsc(@NonNull ExtradataContainerHex audioSpecificConfigHex) {
		final String FNC_NAME = FfmpegHelperBsfAacWithAdts.class.getSimpleName() + ".fromAsc()";

		if (audioSpecificConfigHex.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": ASC must be set");
		}
		if (! audioSpecificConfigHex.isCodecAac()) {
			throw new IllegalArgumentException(FNC_NAME + ": ASC must be for AAC codec");
		}
		byte[] asc = HexFormat.of().parseHex(audioSpecificConfigHex.getEd());
		if (asc == null || asc.length < 2) {
			throw new IllegalArgumentException(FNC_NAME + ": ASC must contain at least 2 bytes");
		}

		int b0 = asc[0] & 0xFF;
		int b1 = asc[1] & 0xFF;

		int audioObjectType = (b0 >> 3) & 0x1F;  // e.g. 2 for AAC-LC
		int samplingFreqIndex = ((b0 & 0x07) << 1) | (b1 >> 7);
		int channelConfig = (b1 >> 3) & 0x0F;

		if (audioObjectType < 1 || audioObjectType > 4) {
			/*
			 * ADTS header supports profile field of 2 bits (Main/LC/SSR/LTP-ish handling in practice).
			 * For most common AAC-LC, audioObjectType=2.
			 */
			throw new IllegalArgumentException(FNC_NAME + ": Unsupported audioObjectType for simple ADTS writer: " + audioObjectType);
		}
		if (samplingFreqIndex > 12) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid samplingFreqIndex: " + samplingFreqIndex);
		}
		if (channelConfig > 7) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid channelConfig: " + channelConfig);
		}

		int adtsProfile = audioObjectType - 1; // ADTS stores profile = AOT - 1
		return new FfmpegHelperBsfAacWithAdts(adtsProfile, samplingFreqIndex, channelConfig);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Prepend the AAC Access Unit (AU) with an ADTS header.
	 * @param inputAu Input packet
	 * @param outputAu Output packet
	 */
	public void processPkt(@NonNull AVPacket inputAu, @NonNull BufferExt outputAu) {
		final String FNC_NAME = getClass().getSimpleName() + ".processPkt()";

		if (inputAu.size() < 1) {
			throw new IllegalArgumentException(FNC_NAME + ": AAC AU is empty");
		}

		int adtsHeaderLen = 7;  // no CRC
		int fullFrameLen = adtsHeaderLen + inputAu.size();
		if (fullFrameLen > 0x1FFF) { // 13-bit frame length
			throw new IllegalArgumentException(FNC_NAME + ": AAC frame too large for ADTS: " + fullFrameLen);
		}

		outputAu.clear();
		outputAu.increaseSize(fullFrameLen);
		outputAu.setUsed(adtsHeaderLen);

		// ADTS fixed + variable header
		outputAu.set(0, (byte)0xFF);  // syncword 0xFFF (12 bits)
		outputAu.set(1, (byte)0xF1);  // 1111 0001: MPEG-4, layer=00, protection_absent=1
		outputAu.set(2, (byte)(
				((profile & 0x03) << 6)
				| ((samplingFreqIndex & 0x0F) << 2)
				| ((channelConfig >> 2) & 0x01)
			));
		outputAu.set(3, (byte)(
				((channelConfig & 0x03) << 6)
				| ((fullFrameLen >> 11) & 0x03)
			));
		outputAu.set(4, (byte)((fullFrameLen >> 3) & 0xFF));
		outputAu.set(5, (byte)(((fullFrameLen & 0x07) << 5) | 0x1F));
		outputAu.set(6, (byte)0xFC);  // buffer fullness 0x7FF, num_raw_data_blocks=0

		inputAu.data().get(outputAu.getBaPtr(), adtsHeaderLen, inputAu.size());
		outputAu.setUsed(fullFrameLen);
	}

}
