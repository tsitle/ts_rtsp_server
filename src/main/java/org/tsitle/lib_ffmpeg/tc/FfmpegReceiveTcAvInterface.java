package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;

public interface FfmpegReceiveTcAvInterface {

	void cbSetEncoderCtxForAudioParams(@NonNull AVCodecContext encoderCtx);

	void cbReceiveTranscodedVideoFrame(
				@NonNull BufferExt videoFrame,
				long pts,
				long dts,
				@NonNull RationalNumber timeBase
			) throws FfmpegGenericException;

	void cbReceiveTranscodedAudioSamples(
				@NonNull BufferExt audioSamples,
				long pts,
				long dts,
				@NonNull RationalNumber timeBase
			) throws FfmpegGenericException;

}
