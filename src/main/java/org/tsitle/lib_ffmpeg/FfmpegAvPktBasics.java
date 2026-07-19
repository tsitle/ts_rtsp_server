package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;

public final class FfmpegAvPktBasics {

	public final @NonNull BufferExt pktBe = new BufferExt();
	public @Nullable Long ptsUnits = null;
	public @Nullable Long dtsUnits = null;
	public final @NonNull RationalNumber timeBase = RationalNumber.ofEmpty();

	public void clear() {
		pktBe.clear();
		ptsUnits = null;
		dtsUnits = null;
		timeBase.copyFrom(RationalNumber.ofEmpty());
	}

	public double ptsUnitsToSeconds() {
		if (ptsUnits == null) {
			return -1.0;
		}
		return ((double)ptsUnits * (double)timeBase.getNumerator()) / (double)timeBase.getDenominator();
	}

}
