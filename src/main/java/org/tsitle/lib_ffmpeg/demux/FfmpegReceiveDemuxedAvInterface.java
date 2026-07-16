package org.tsitle.lib_ffmpeg.demux;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;

public interface FfmpegReceiveDemuxedAvInterface {

	void cbReceiveDemuxedVideoFrame(
				@NonNull AVFormatContext inputAvFmtCtx,
				int streamIx,
				@NonNull AVPacket inputPkt
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException;

	void cbReceiveDemuxedAudioSamples(
				@NonNull AVFormatContext inputAvFmtCtx,
				int streamIx,
				@NonNull AVPacket inputPkt
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException;

	void cbReceiveDemuxerStats(@NonNull FfmpegDemuxerStats statsPtr);

}
