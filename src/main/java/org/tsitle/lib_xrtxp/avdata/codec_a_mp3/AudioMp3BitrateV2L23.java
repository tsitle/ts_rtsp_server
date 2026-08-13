package org.tsitle.lib_xrtxp.avdata.codec_a_mp3;

import org.jspecify.annotations.NonNull;

/** Bitrates in kbit per second for MPEG-2 Layer II+III according to ISO/IEC 11172-3 */
enum AudioMp3BitrateV2L23 {
	KBPS_FREE(0),
	KBPS008(1),
	KBPS016(2),
	KBPS024(3),
	KBPS032(4),
	KBPS040(5),
	KBPS048(6),
	KBPS056(7),
	KBPS064(8),
	KBPS080(9),
	KBPS096(10),
	KBPS112(11),
	KBPS128(12),
	KBPS144(13),
	KBPS160(14),
	UNKNOWN(255);

	public final int index;
	AudioMp3BitrateV2L23(int index) {
		this.index = index;
	}

	public static @NonNull AudioMp3BitrateV2L23 of(int index) {
		for (AudioMp3BitrateV2L23 rate : AudioMp3BitrateV2L23.values()) {
			if (rate.index == index) {
				return rate;
			}
		}
		return UNKNOWN;
	}

	public int getKbps() {
		return switch (this) {
				case KBPS_FREE -> 0;  // ?
				case KBPS008 -> 8;
				case KBPS016 -> 16;
				case KBPS024 -> 24;
				case KBPS032 -> 32;
				case KBPS040 -> 40;
				case KBPS048 -> 48;
				case KBPS056 -> 56;
				case KBPS064 -> 64;
				case KBPS080 -> 80;
				case KBPS096 -> 96;
				case KBPS112 -> 112;
				case KBPS128 -> 128;
				case KBPS144 -> 144;
				case KBPS160 -> 160;
				case UNKNOWN -> -1;
			};
	}

}
