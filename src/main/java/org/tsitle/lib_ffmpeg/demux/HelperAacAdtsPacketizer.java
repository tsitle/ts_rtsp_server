package org.tsitle.lib_ffmpeg.demux;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.util.HexFormat;

public final class HelperAacAdtsPacketizer {

	/** ADTS profile: 0=Main,1=LC,2=SSR,3=reserved */
	private final int profile;
	/** 0..12 (per MPEG-4 table) */
	private final int samplingFreqIndex;
	/** 1..7 typically */
	private final int channelConfig;

	private HelperAacAdtsPacketizer(int profile, int samplingFreqIndex, int channelConfig) {
		this.profile = profile;
		this.samplingFreqIndex = samplingFreqIndex;
		this.channelConfig = channelConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull HelperAacAdtsPacketizer fromAsc(@NonNull String audioSpecificConfigHex) {
		byte[] asc = HexFormat.of().parseHex(audioSpecificConfigHex);
		if (asc == null || asc.length < 2) {
			throw new IllegalArgumentException("ASC must contain at least 2 bytes");
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
			throw new IllegalArgumentException("Unsupported audioObjectType for simple ADTS writer: " + audioObjectType);
		}
		if (samplingFreqIndex > 12) {
			throw new IllegalArgumentException("Invalid samplingFreqIndex: " + samplingFreqIndex);
		}
		if (channelConfig > 7) {
			throw new IllegalArgumentException("Invalid channelConfig: " + channelConfig);
		}

		int adtsProfile = audioObjectType - 1; // ADTS stores profile = AOT - 1
		return new HelperAacAdtsPacketizer(adtsProfile, samplingFreqIndex, channelConfig);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void wrapAuWithAdts(@NonNull AVPacket inputAacAu, @NonNull BufferExt outputAdtsAu) {
		if (inputAacAu.size() < 1) {
			throw new IllegalArgumentException("AAC AU is empty");
		}

		int adtsHeaderLen = 7;  // no CRC
		int fullFrameLen = adtsHeaderLen + inputAacAu.size();
		if (fullFrameLen > 0x1FFF) { // 13-bit frame length
			throw new IllegalArgumentException("AAC frame too large for ADTS: " + fullFrameLen);
		}

		outputAdtsAu.clear();
		outputAdtsAu.increaseSize(fullFrameLen);
		outputAdtsAu.setUsed(adtsHeaderLen);

		// ADTS fixed + variable header
		outputAdtsAu.set(0, (byte)0xFF);  // syncword 0xFFF (12 bits)
		outputAdtsAu.set(1, (byte)0xF1);  // 1111 0001: MPEG-4, layer=00, protection_absent=1
		outputAdtsAu.set(2, (byte)(
				((profile & 0x03) << 6)
				| ((samplingFreqIndex & 0x0F) << 2)
				| ((channelConfig >> 2) & 0x01)
			));
		outputAdtsAu.set(3, (byte)(
				((channelConfig & 0x03) << 6)
				| ((fullFrameLen >> 11) & 0x03)
			));
		outputAdtsAu.set(4, (byte)((fullFrameLen >> 3) & 0xFF));
		outputAdtsAu.set(5, (byte)(((fullFrameLen & 0x07) << 5) | 0x1F));
		outputAdtsAu.set(6, (byte)0xFC);  // buffer fullness 0x7FF, num_raw_data_blocks=0

		inputAacAu.data().get(outputAdtsAu.getBaPtr(), adtsHeaderLen, inputAacAu.size());
		outputAdtsAu.setUsed(fullFrameLen);
	}

}
