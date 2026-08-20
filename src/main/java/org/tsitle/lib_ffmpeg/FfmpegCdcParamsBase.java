package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;

/**
 * Base class for Codec Parameters.
 */
public class FfmpegCdcParamsBase {

	public @NonNull FfmpegCodec ffmpegCodec;
	public final @NonNull ExtradataContainerHex extradataHex = ExtradataContainerHex.ofEmpty();

	protected FfmpegCdcParamsBase() {
		baseClear();
	}

	protected void baseClear() {
		ffmpegCodec = FfmpegCodec.UNKNOWN;
		extradataHex.clear();
	}

	protected void baseCopyFrom(@NonNull FfmpegCdcParamsBase other) {
		ffmpegCodec = other.ffmpegCodec;
		extradataHex.copyFrom(other.extradataHex);
	}

}
