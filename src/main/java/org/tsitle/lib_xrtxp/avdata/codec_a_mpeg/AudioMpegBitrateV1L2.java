package org.tsitle.lib_xrtxp.avdata.codec_a_mpeg;

import org.jspecify.annotations.NonNull;

/** Bitrates in kbit per second for MPEG-1 Layer II according to ISO/IEC 11172-3 */
enum AudioMpegBitrateV1L2 {
	KBPS_FREE(0),
	KBPS032(1),
	KBPS048(2),
	KBPS056(3),
	KBPS064(4),
	KBPS080(5),
	KBPS096(6),
	KBPS112(7),
	KBPS128(8),
	KBPS160(9),
	KBPS192(10),
	KBPS224(11),
	KBPS256(12),
	KBPS320(13),
	KBPS384(14),
	UNKNOWN(255);

	public final int index;
	AudioMpegBitrateV1L2(int index) {
		this.index = index;
	}

	public static @NonNull AudioMpegBitrateV1L2 of(int index) {
		for (AudioMpegBitrateV1L2 rate : AudioMpegBitrateV1L2.values()) {
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
				case KBPS048 -> 48;
				case KBPS056 -> 56;
				case KBPS064 -> 64;
				case KBPS080 -> 80;
				case KBPS096 -> 96;
				case KBPS112 -> 112;
				case KBPS128 -> 128;
				case KBPS160 -> 160;
				case KBPS192 -> 192;
				case KBPS224 -> 224;
				case KBPS256 -> 256;
				case KBPS320 -> 320;
				case KBPS384 -> 384;
				case UNKNOWN -> -1;
			};
	}

}
