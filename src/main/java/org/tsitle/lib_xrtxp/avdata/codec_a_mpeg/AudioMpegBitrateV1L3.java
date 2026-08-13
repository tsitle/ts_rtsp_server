package org.tsitle.lib_xrtxp.avdata.codec_a_mpeg;

import org.jspecify.annotations.NonNull;

/** Bitrates in kbit per second for MPEG-1 Layer III according to ISO/IEC 11172-3 */
enum AudioMpegBitrateV1L3 {
	KBPS_FREE(0),
	KBPS032(1),
	KBPS040(2),
	KBPS048(3),
	KBPS056(4),
	KBPS064(5),
	KBPS080(6),
	KBPS096(7),
	KBPS112(8),
	KBPS128(9),
	KBPS160(10),
	KBPS192(11),
	KBPS224(12),
	KBPS256(13),
	KBPS320(14),
	UNKNOWN(255);

	public final int index;
	AudioMpegBitrateV1L3(int index) {
		this.index = index;
	}

	public static @NonNull AudioMpegBitrateV1L3 of(int index) {
		for (AudioMpegBitrateV1L3 rate : AudioMpegBitrateV1L3.values()) {
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
				case KBPS040 -> 40;
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
				case UNKNOWN -> -1;
			};
	}

}
