package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;

public interface FfmpegReceiveTcAvInterface {

	void cbSetEncoderCtxForAudioParams(@NonNull AVCodecContext encoderCtx);

	void cbReceiveTranscodedVideoFrame(@NonNull FfmpegAvPktBasics videoFrame) throws FfmpegGenericException;

	void cbReceiveTranscodedAudioSamples(@NonNull FfmpegAvPktBasics audioSamples) throws FfmpegGenericException;

}
