package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;

public final class FfmpegAvPktBasics {

	public final @NonNull BufferExt pktBe = new BufferExt();
	public long pts = -1L;
	public long dts = -1L;
	public final @NonNull RationalNumber timeBase = RationalNumber.ofEmpty();

	public void clear() {
		pktBe.clear();
		pts = -1L;
		dts = -1L;
		timeBase.copyFrom(RationalNumber.ofEmpty());
	}

}
