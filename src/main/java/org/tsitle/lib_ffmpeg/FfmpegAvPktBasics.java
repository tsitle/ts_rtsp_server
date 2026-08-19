package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

public final class FfmpegAvPktBasics {

	public final @NonNull BufferExt pktBe = new BufferExt();
	public @Nullable Long ptsUnits;
	public @Nullable Long dtsUnits;
	public final @NonNull RationalNumber timeBase = RationalNumber.ofEmpty();
	public int flags;
	public boolean isVideo;
	public boolean isVidKeyFrame;
	public int subStreamIndex;
	public long duration;
	public long pos;

	public FfmpegAvPktBasics() {
		clear();
	}

	public void clear() {
		pktBe.clear();
		ptsUnits = null;
		dtsUnits = null;
		timeBase.copyFrom(RationalNumber.ofEmpty());
		flags = 0;
		isVideo = false;
		isVidKeyFrame = false;
		subStreamIndex = -1;
		duration = 0;
		pos = -1L;
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
		flags = other.flags;
		isVideo = other.isVideo;
		isVidKeyFrame = other.isVidKeyFrame;
		subStreamIndex = other.subStreamIndex;
		duration = other.duration;
		pos = other.pos;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"pktBe.sz=" + pktBe.getUsed() +
				", ptsUnits=" + ptsUnits +
				", dtsUnits=" + dtsUnits +
				", timeBase=" + timeBase +
				", flags=" + flags +
				", isVideo=" + (isVideo ? "T" : "F") +
				", isVidKeyFrame=" + (isVidKeyFrame ? "T" : "F") +
				", subStreamIndex=" + subStreamIndex +
				", duration=" + duration +
				", pos=" + pos +
				"]";
	}

}
