package org.tsitle.lib_ffmpeg.mux;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.FfmpegErrorHelper;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_ffmpeg.tc.FfmpegReceiveTcAvInterface;
import org.tsitle.lib_ffmpeg.tc.FfmpegTranscoderBase;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Muxer for A/V streams.
 */
public final class FfmpegMuxer implements FfmpegReceiveTcAvInterface, AutoCloseable {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull FfmpegCodec destVideoCodec;
	private final @NonNull ImageDimensions destVideoImgDims;
	private final @NonNull FfmpegCodec destAudioCodec;
	private final @NonNull SampleRateEnum destAudioSampleRate;
	private final int destAudioChannels;

	private @Nullable AVFormatContext outFmtCtx = null;
	private @Nullable AVStream outVideoStream = null;
	private @Nullable AVStream outAudioStream = null;
	private boolean isOpened = false;
	private boolean haveWrittenFileHeader = false;

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance
	 * @param destVideoCodec Output video codec (can be {@link FfmpegCodec#UNKNOWN})
	 * @param destVideoImgDims Output video image dimensions (can be empty if video codec is not used)
	 * @param destAudioCodec Output audio codec (can be {@link FfmpegCodec#UNKNOWN})
	 * @param destAudioSampleRate Output audio sample rate (can be {@link SampleRateEnum#UNKNOWN} if audio codec is not used)
	 * @param destAudioChannels Output audio channels (can be < 1 if audio codec is not used)
	 */
	public FfmpegMuxer(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull FfmpegCodec destVideoCodec,
				@NonNull ImageDimensions destVideoImgDims,
				@NonNull FfmpegCodec destAudioCodec,
				@NonNull SampleRateEnum destAudioSampleRate,
				int destAudioChannels
			) {
		if (destVideoCodec == FfmpegCodec.UNKNOWN && destAudioCodec == FfmpegCodec.UNKNOWN) {
			throw new IllegalArgumentException(FfmpegTranscoderBase.class.getSimpleName() + ".ctor(): " +
					"Need either Video or Audio FFmpeg Codec");
		}
		if (destVideoCodec != FfmpegCodec.UNKNOWN && destVideoImgDims.isEmpty()) {
			throw new IllegalArgumentException("Video Image Dimensions must be set");
		}
		if (destAudioCodec != FfmpegCodec.UNKNOWN && destAudioSampleRate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException("Audio Sample Rate must be set");
		}
		if (destAudioCodec != FfmpegCodec.UNKNOWN && destAudioChannels < 1) {
			throw new IllegalArgumentException("Audio Channels must be set");
		}

		this.logMsgInterface = logMsgInterface;
		this.destVideoCodec = destVideoCodec;
		this.destVideoImgDims = destVideoImgDims;
		this.destAudioCodec = destAudioCodec;
		this.destAudioSampleRate = destAudioSampleRate;
		this.destAudioChannels = destAudioChannels;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	public static @NonNull String getOutputFileExtension(
				@NonNull FfmpegCodec destFfmpegCodecVideo,
				@NonNull FfmpegCodec destFfmpegCodecAudio,
				boolean preferMkvOverMp4
			) {
		if (destFfmpegCodecVideo == FfmpegCodec.UNKNOWN && destFfmpegCodecAudio == FfmpegCodec.UNKNOWN) {
			throw new IllegalArgumentException("Need video and/or audio codec");
		}
		if (destFfmpegCodecAudio == FfmpegCodec.A_PCM_F32BE || destFfmpegCodecAudio == FfmpegCodec.A_PCM_S16BE ||
				destFfmpegCodecAudio == FfmpegCodec.A_PCM_S24BE || destFfmpegCodecAudio == FfmpegCodec.A_PCM_S32BE) {
			throw new IllegalArgumentException("Audio codec PCM_F32BE/PCM_S16BE/PCM_S24BE/PCM_S32BE not supported");
		}
		/*
		 * MOV:
		 *   H264/H265/MPEG2/MPEG4 + (AAC/AC3/EAC3/MP3/ALAC_16_24)/PCM_16_24
		 */
		if ((destFfmpegCodecVideo == FfmpegCodec.V_H264 || destFfmpegCodecVideo == FfmpegCodec.V_H265 ||
					destFfmpegCodecVideo == FfmpegCodec.V_MPEG2 || destFfmpegCodecVideo == FfmpegCodec.V_MPEG4) &&
				(destFfmpegCodecAudio == FfmpegCodec.A_PCM_S16LE || destFfmpegCodecAudio == FfmpegCodec.A_PCM_S24LE)) {
			return "mov";
		}
		if (destFfmpegCodecVideo != FfmpegCodec.UNKNOWN && destFfmpegCodecAudio.isPcmAudio()) {
			throw new IllegalArgumentException("PCM audio supported is limited to H264/H265/MPEG2/MPEG4 + PCM_S16LE/PCM_S24LE");
		}
		/*
		 * WEBM:
		 *   (AV1)/VP8/VP9 + OPUS/VORBIS
		 */
		if ((destFfmpegCodecVideo == FfmpegCodec.V_VP8 || destFfmpegCodecVideo == FfmpegCodec.V_VP9) &&
				(destFfmpegCodecAudio == FfmpegCodec.UNKNOWN ||
					destFfmpegCodecAudio == FfmpegCodec.A_OPUS || destFfmpegCodecAudio == FfmpegCodec.A_VORBIS)) {
			return "webm";
		}
		/*
		 * MP4:
		 *   AV1/H264/H265/MPEG2/MPEG4/(VP9) + AAC/AC3/ALAC_16_24/EAC3/FLAC_16_24/MP3/(OPUS/PCM_16_24)
		 */
		if ((destFfmpegCodecVideo == FfmpegCodec.V_AV1 ||
					destFfmpegCodecVideo == FfmpegCodec.V_H264 || destFfmpegCodecVideo == FfmpegCodec.V_H265 ||
					destFfmpegCodecVideo == FfmpegCodec.V_MPEG2 || destFfmpegCodecVideo == FfmpegCodec.V_MPEG4) &&
				(destFfmpegCodecAudio == FfmpegCodec.UNKNOWN ||
					destFfmpegCodecAudio == FfmpegCodec.A_AAC || destFfmpegCodecAudio == FfmpegCodec.A_AC3 ||
					destFfmpegCodecAudio == FfmpegCodec.A_ALAC || destFfmpegCodecAudio == FfmpegCodec.A_EAC3 ||
					destFfmpegCodecAudio == FfmpegCodec.A_FLAC || destFfmpegCodecAudio == FfmpegCodec.A_MP3)) {
			return (preferMkvOverMp4 && destFfmpegCodecAudio != FfmpegCodec.A_FLAC ? "mkv" : "mp4");
		}
		if (destFfmpegCodecVideo != FfmpegCodec.UNKNOWN) {
			return "mkv";
		}
		return switch (destFfmpegCodecAudio) {
				case FfmpegCodec.A_AAC -> "aac";
				case FfmpegCodec.A_AC3 -> "ac3";
				case FfmpegCodec.A_ALAC -> "m4a";
				case FfmpegCodec.A_EAC3 -> "eac3";
				case FfmpegCodec.A_FLAC -> "flac";
				case FfmpegCodec.A_MP3 -> "mp3";
				case FfmpegCodec.A_OPUS -> "opus";
				case FfmpegCodec.A_VORBIS -> "ogg";
				default -> "wav";
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void openOutput(@NonNull String outputPath) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openOutput()";

		logDebug(FNC_NAME, "Opening output file: '" + outputPath + "'");

		//
		outFmtCtx = avformat.avformat_alloc_context();
		if (outFmtCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Output Format Context");
		}

		AVOutputFormat guessedFmt = avformat.av_guess_format((String)null, outputPath, null);
		if (guessedFmt == null) {
			throw new IllegalArgumentException(FNC_NAME + ": Cannot guess output format from path: '" + outputPath + "'");
		}
		outFmtCtx.oformat(guessedFmt);

		if (destVideoCodec != FfmpegCodec.UNKNOWN) {
			outVideoStream = avformat.avformat_new_stream(outFmtCtx, null);
			if (outVideoStream == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot create output video stream");
			}
			outVideoStream.id(0);
			outVideoStream.codecpar().codec_type(avutil.AVMEDIA_TYPE_VIDEO);
			outVideoStream.codecpar().codec_id(destVideoCodec.getFfmpegId());
			outVideoStream.codecpar().width(destVideoImgDims.imgWidth());
			outVideoStream.codecpar().height(destVideoImgDims.imgHeight());

			try (AVRational vtb = new AVRational()) {
				vtb.num(1).den(1000);  // ms for video stream timeline
				outVideoStream.time_base(vtb);
			}
		}

		if (destAudioCodec != FfmpegCodec.UNKNOWN) {
			outAudioStream = avformat.avformat_new_stream(outFmtCtx, null);
			if (outAudioStream == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot create output audio stream");
			}
			outAudioStream.id(1);
			outAudioStream.codecpar().codec_type(avutil.AVMEDIA_TYPE_AUDIO);
			outAudioStream.codecpar().codec_id(destAudioCodec.getFfmpegId());
			outAudioStream.codecpar().sample_rate(destAudioSampleRate.getSrHz());

			try (AVChannelLayout tmpChLayout = createDefaultLayout(destAudioChannels)) {
				tmpChLayout.nb_channels(destAudioChannels);

				int r = avutil.av_channel_layout_copy(outAudioStream.codecpar().ch_layout(), tmpChLayout);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);
			}

			try (AVRational atb = new AVRational()) {
				atb.num(1).den(destAudioSampleRate.getSrHz());  // sample timeline for audio stream
				outAudioStream.time_base(atb);
			}
		}

		if ((outFmtCtx.oformat().flags() & avformat.AVFMT_NOFILE) == 0) {
			AVIOContext pb = new AVIOContext();
			int r = avformat.avio_open(pb, outputPath, avformat.AVIO_FLAG_WRITE);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avio_open()", r);
			outFmtCtx.pb(pb);
		}

		isOpened = true;
		haveWrittenFileHeader = false;
	}

	@Override
	public void close() {
		if (outFmtCtx == null) {
			return;
		}

		if (isOpened && haveWrittenFileHeader) {
			avformat.av_write_trailer(outFmtCtx);
		}

		if ((outFmtCtx.oformat().flags() & avformat.AVFMT_NOFILE) == 0 && outFmtCtx.pb() != null) {
			avformat.avio_closep(outFmtCtx.pb());
		}

		avformat.avformat_free_context(outFmtCtx);
		outFmtCtx = null;
		outVideoStream = null;
		outAudioStream = null;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void cbSetEncoderCtxForAudioParams(@NonNull AVCodecContext encoderCtx) {
		if (outAudioStream != null) {
			avcodec.avcodec_parameters_from_context(outAudioStream.codecpar(), encoderCtx);
		}
	}

	@Override
	public void cbReceiveTranscodedVideoFrame(@NonNull FfmpegAvPktBasics videoFrame) throws FfmpegGenericException {
		writePacket(videoFrame, outVideoStream);
	}

	@Override
	public void cbReceiveTranscodedAudioSamples(@NonNull FfmpegAvPktBasics audioSamples) throws FfmpegGenericException {
		writePacket(audioSamples, outAudioStream);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void writePacket(@NonNull FfmpegAvPktBasics payload, @Nullable AVStream outStream) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".writePacket()";

		if (! isOpened) {
			throw new IllegalStateException(FNC_NAME + ": Muxer has not been opened");
		}
		if (outFmtCtx == null || outStream == null || payload.pktBe.getUsed() == 0) {
			return;
		}

		//
		if (! haveWrittenFileHeader) {
			int r = avformat.avformat_write_header(outFmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary)null);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_write_header()", r);

			haveWrittenFileHeader = true;
		}

		//
		AVPacket pkt = avcodec.av_packet_alloc();
		if (pkt == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate AVPacket");
		}

		try (AVRational src = new AVRational()) {
			int r = avcodec.av_new_packet(pkt, payload.pktBe.getUsed());
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_new_packet()", r);
			pkt.data().put(payload.pktBe.getBaPtr(), 0, payload.pktBe.getUsed());

			pkt.stream_index(outStream.index());
			pkt.pts(payload.ptsUnits == null ? avutil.AV_NOPTS_VALUE : payload.ptsUnits);
			pkt.dts(payload.dtsUnits == null ? avutil.AV_NOPTS_VALUE : payload.dtsUnits);

			src.num(payload.timeBase.getNumerator());
			src.den(payload.timeBase.getDenominator());

			avcodec.av_packet_rescale_ts(pkt, src, outStream.time_base());

			r = avformat.av_interleaved_write_frame(outFmtCtx, pkt);
			if (r == avutil.AVERROR_EINVAL()) {
				logDebug(FNC_NAME, "av_interleaved_write_frame() rejected invalid data - ignoring");
			} else {
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_interleaved_write_frame()", r);
			}
		} finally {
			avcodec.av_packet_free(pkt);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull AVChannelLayout createDefaultLayout(int channelCount) {
		AVChannelLayout layout = new AVChannelLayout();
		avutil.av_channel_layout_default(layout, channelCount);
		return layout;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
