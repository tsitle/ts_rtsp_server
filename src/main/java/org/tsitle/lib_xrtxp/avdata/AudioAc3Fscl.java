package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

final class AudioAc3Fscl {

	static {
		MAP_FSCL = new HashMap<>();
		int ix = 0;
		addEntry(ix, AudioAc3Info.Bitrate.BR032, 96, 69, 64); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR040, 120, 87, 80); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR048, 144, 104, 96); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR056, 168, 121, 112); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR064, 192, 139, 128); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR080, 240, 174, 160); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR096, 288, 208, 192); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR112, 336, 243, 224); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR128, 384, 278, 256); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR160, 480, 348, 320); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR192, 576, 417, 384); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR224, 672, 487, 448); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR256, 768, 557, 512); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR320, 960, 696, 640); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR384, 1152, 835, 768); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR448, 1344, 975, 896); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR512, 1536, 1114, 1024); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR576, 1728, 1253, 1152); ix += 2;
		addEntry(ix, AudioAc3Info.Bitrate.BR640, 1920, 1393, 1280);
	}

	private static void addEntry(
				int frmsizecod,
				AudioAc3Info.@NonNull Bitrate bitrate,
				int words16bitPerSyncframe32k,
				int words16bitPerSyncframe41k,
				int words16bitPerSyncframe48k
			) {
		MAP_FSCL.put(frmsizecod, new FrameSizeCodLookupEntry(bitrate, new HashMap<>() {{
				put(AudioAc3Info.Samplerate.SR32000, words16bitPerSyncframe32k * 2);
				put(AudioAc3Info.Samplerate.SR44100, words16bitPerSyncframe41k * 2);
				put(AudioAc3Info.Samplerate.SR48000, words16bitPerSyncframe48k * 2);
			}} ));
		MAP_FSCL.put(frmsizecod + 1, new FrameSizeCodLookupEntry(bitrate, new HashMap<>() {{
				put(AudioAc3Info.Samplerate.SR32000, words16bitPerSyncframe32k * 2);
				put(AudioAc3Info.Samplerate.SR44100, (words16bitPerSyncframe41k + 1) * 2);
				put(AudioAc3Info.Samplerate.SR48000, words16bitPerSyncframe48k * 2);
			}} ));
	}

	private AudioAc3Fscl() { }

	record FrameSizeCodLookupEntry(
			AudioAc3Info.@NonNull Bitrate bitrate,
			@NonNull Map<AudioAc3Info.@NonNull Samplerate, @NonNull Integer> subEntriesPerSr
		) { }

	static final @NonNull Map<@NonNull Integer, @NonNull FrameSizeCodLookupEntry> MAP_FSCL;

}
