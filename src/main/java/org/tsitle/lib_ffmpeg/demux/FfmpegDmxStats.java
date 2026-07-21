package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FfmpegDmxStats {

	public static class PktsAndData {
		public int countPkt = 0;
		public int lastCountPkt = 0;
		public long countData = 0L;
		public double currentPktsPerSec = -1.0;
		public @NonNull List<Double> pktsPerSecsList = new ArrayList<>();
		public double currentPtsSecs = -1.0;
	}

	public @Nullable Instant startTime = null;
	public @Nullable Instant lastFpsMeasureTime = null;

	public @NonNull PktsAndData pktsAndDataVid = new PktsAndData();
	public @NonNull PktsAndData pktsAndDataAud = new PktsAndData();

	public double currentMaxPtsSecs = -1.0;

}
