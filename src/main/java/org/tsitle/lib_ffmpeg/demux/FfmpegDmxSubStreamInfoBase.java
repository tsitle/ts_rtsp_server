package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FfmpegDmxSubStreamInfoBase {

	public static final String META_KEY_ALBUM = "album";
	public static final String META_KEY_ARTIST = "artist";
	public static final String META_KEY_TITLE = "title";
	public static final String META_KEY_DATE = "date";
	public static final String META_KEY_TRACK = "track";
	public static final String META_KEY_DISC = "disc";
	public static final String META_KEY_GENRE = "genre";
	public static final Set<@NonNull String> META_KEYS = Set.of(
			META_KEY_ALBUM, META_KEY_ARTIST, META_KEY_TITLE, META_KEY_DATE,
			META_KEY_TRACK, META_KEY_DISC, META_KEY_GENRE
		);

	public int subStreamIx;
	public @NonNull FfmpegCodec ffmpegCodec;
	public @NonNull RationalNumber timeBasePts;
	public double durationSecs;
	/**
	 * Audio: works for AC3 and PCM, sometimes AAC, doesn't work for Opus<br />
	 * Video: works sometimes for H26x
	 */
	public long bitRate;
	public final ExtradataContainerHex extradataHex = ExtradataContainerHex.ofEmpty();
	public final Map<@NonNull String, @NonNull String> metaMap = new HashMap<>();

	protected FfmpegDmxSubStreamInfoBase() {
		baseClear();
	}

	protected void baseClear() {
		subStreamIx = -1;
		ffmpegCodec = FfmpegCodec.UNKNOWN;
		timeBasePts = RationalNumber.ofEmpty();
		durationSecs = -1.0;
		bitRate = -1L;
		extradataHex.clear();
		metaMap.clear();
	}

	protected void baseCopyFrom(@NonNull FfmpegDmxSubStreamInfoBase other) {
		if (this == other) {
			return;
		}
		subStreamIx = other.subStreamIx;
		ffmpegCodec = other.ffmpegCodec;
		timeBasePts = RationalNumber.of(other.timeBasePts);
		durationSecs = other.durationSecs;
		bitRate = other.bitRate;
		extradataHex.copyFrom(other.extradataHex);
		metaMap.clear();
		metaMap.putAll(other.metaMap);
	}

}
