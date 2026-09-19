package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedList;

public final class FfmpegDmxStats implements Cloneable {

	public static class PktsAndData implements Cloneable {
		public int countPkt = 0;
		public int lastCountPkt = 0;
		public long countData = 0L;
		public double currentPktsPerSec = -1.0;
		public @NonNull LinkedList<Double> pktsPerSecsList = new LinkedList<>();
		public double currentPtsSecs = -1.0;

		@Override
		public @NonNull PktsAndData clone() {
			try {
				PktsAndData clone = (PktsAndData)super.clone();
				clone.pktsPerSecsList = new LinkedList<>(pktsPerSecsList);
				return clone;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	public @Nullable Instant startTime = null;
	public @Nullable Instant lastFpsMeasureTime = null;

	public @NonNull PktsAndData pktsAndDataVid = new PktsAndData();
	public @NonNull PktsAndData pktsAndDataAud = new PktsAndData();

	public double currentMaxPtsSecs = -1.0;

	@Override
	public @NonNull FfmpegDmxStats clone() {
		try {
			FfmpegDmxStats clone = (FfmpegDmxStats)super.clone();
			clone.startTime = (startTime == null ? null : Instant.ofEpochMilli(startTime.toEpochMilli()));
			clone.lastFpsMeasureTime = (lastFpsMeasureTime == null ? null : Instant.ofEpochMilli(lastFpsMeasureTime.toEpochMilli()));
			clone.pktsAndDataVid = pktsAndDataVid.clone();
			clone.pktsAndDataAud = pktsAndDataAud.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
