package org.tsitle.lib_xrtxp.avdata.codec_a_mp3;

import org.jspecify.annotations.NonNull;

/** Bitrates in kbit per second for MPEG-2 Layer I according to ISO/IEC 11172-3 */
enum AudioMp3BitrateV2L1 {
	KBPS_FREE(0),
	KBPS032(1),
	KBPS048(2),
	KBPS056(3),
	KBPS064(4),
	KBPS080(5),
	KBPS096(6),
	KBPS112(7),
	KBPS128(8),
	KBPS144(9),
	KBPS160(10),
	KBPS176(11),
	KBPS192(12),
	KBPS224(13),
	KBPS256(14),
	UNKNOWN(255);

	public final int index;
	AudioMp3BitrateV2L1(int index) {
		this.index = index;
	}

	public static @NonNull AudioMp3BitrateV2L1 of(int index) {
		for (AudioMp3BitrateV2L1 rate : AudioMp3BitrateV2L1.values()) {
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
				case KBPS144 -> 144;
				case KBPS160 -> 160;
				case KBPS176 -> 176;
				case KBPS192 -> 192;
				case KBPS224 -> 224;
				case KBPS256 -> 256;
				case UNKNOWN -> -1;
			};
	}

}
