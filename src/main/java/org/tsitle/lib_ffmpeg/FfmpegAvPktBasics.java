package org.tsitle.lib_ffmpeg;

import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class FfmpegAvPktBasics {

	public final @NonNull BufferExt pktBe = new BufferExt();
	public @Nullable Long ptsUnits = null;
	public @Nullable Long dtsUnits = null;
	public final @NonNull RationalNumber timeBase = RationalNumber.ofEmpty();
	public boolean isVideo = false;

	public void clear() {
		pktBe.clear();
		ptsUnits = null;
		dtsUnits = null;
		timeBase.copyFrom(RationalNumber.ofEmpty());
		isVideo = false;
	}

	public double ptsUnitsToSeconds() {
		if (ptsUnits == null) {
			return -1.0;
		}
		return ((double)ptsUnits * (double)timeBase.getNumerator()) / (double)timeBase.getDenominator();
	}

	public void copyFrom(@NonNull FfmpegAvPktBasics other) {
		if (this == other) {
			return;
		}
		pktBe.copyOf(other.pktBe);
		copyOnlyMetadata(other);
	}

	public void copyOnlyMetadata(@NonNull FfmpegAvPktBasics other) {
		if (this == other) {
			return;
		}
		ptsUnits = (other.ptsUnits == null ? null : other.ptsUnits);
		dtsUnits = (other.dtsUnits == null ? null : other.dtsUnits);
		timeBase.copyFrom(other.timeBase);
		isVideo = other.isVideo;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"pktBe.sz=" + pktBe.getUsed() +
				", ptsUnits=" + ptsUnits +
				", dtsUnits=" + dtsUnits +
				", timeBase=" + timeBase +
				", isVideo=" + (isVideo ? "T" : "F") +
				"]";
	}

}
