package org.tsitle.lib_xrtxp.avdata.codec_a_mpeg;

import org.jspecify.annotations.NonNull;

/** Bitrates in kbit per second for MPEG-1 Layer I according to ISO/IEC 11172-3 */
enum AudioMpegBitrateV1L1 {
	KBPS_FREE(0),
	KBPS032(1),
	KBPS064(2),
	KBPS096(3),
	KBPS128(4),
	KBPS160(5),
	KBPS192(6),
	KBPS224(7),
	KBPS256(8),
	KBPS288(9),
	KBPS320(10),
	KBPS352(11),
	KBPS384(12),
	KBPS416(13),
	KBPS448(14),
	UNKNOWN(255);

	public final int index;
	AudioMpegBitrateV1L1(int index) {
		this.index = index;
	}

	public static @NonNull AudioMpegBitrateV1L1 of(int index) {
		for (AudioMpegBitrateV1L1 rate : AudioMpegBitrateV1L1.values()) {
			if (rate.index == index) {
				return rate;
			}
		}
		return UNKNOWN;
	}

	public int getKbps() {
		return switch (this) {
				case KBPS_FREE -> 0;  // ?
				case KBPS032 -> 32;
				case KBPS064 -> 64;
				case KBPS096 -> 96;
				case KBPS128 -> 128;
				case KBPS160 -> 160;
				case KBPS192 -> 192;
				case KBPS224 -> 224;
				case KBPS256 -> 256;
				case KBPS288 -> 288;
				case KBPS320 -> 320;
				case KBPS352 -> 352;
				case KBPS384 -> 384;
				case KBPS416 -> 416;
				case KBPS448 -> 448;
				case UNKNOWN -> -1;
			};
	}

}
