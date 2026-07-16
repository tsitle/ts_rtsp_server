package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.FfmpegErrorHelper;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Transcoder for video streams.
 */
public final class FfmpegTranscoderVideo extends FfmpegTranscoderBase implements AutoCloseable {

	private static final int DEST_PIXEL_FORMAT = avutil.AV_PIX_FMT_YUV420P;

	private final @NonNull Set<@NonNull String> cfgDisableEnc;
	private final @NonNull RationalNumber sourceVideoFrameRate;
	private final @NonNull ImageDimensions sourceImgDims;
	private final @NonNull FfmpegTcSettingsVideo tcSettingsVid;

	private @NonNull ImageDimensions destImgDims = ImageDimensions.ofEmpty();
	private @Nullable SwsContext swsCtx = null;
	private boolean doneCheckingForSwScaler = false;
	private boolean needsSwScaler = false;
	private int swsLastSrcW = -2;
	private int swsLastSrcH = -2;
	private int swsLastSrcFmt = -2;

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param cfgDisableEnc Configuration: list of Encoders to disable
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceFfmpegCodec Source codec
	 * @param sourceVideoTimeBase Source time base
	 * @param sourceVideoFrameRate Source frame rate
	 * @param sourceImgDims Source image dimensions
	 * @param tcSettingsVid Transcoder settings for video
	 */
	public FfmpegTranscoderVideo(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull Set<@NonNull String> cfgDisableEnc,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegCodec sourceFfmpegCodec,
				@NonNull RationalNumber sourceVideoTimeBase,
				@NonNull RationalNumber sourceVideoFrameRate,
				@NonNull ImageDimensions sourceImgDims,
				@NonNull FfmpegTcSettingsVideo tcSettingsVid
			) {
		super(
				logMsgInterface,
				true,
				ffmpegReceiveTcAvInterface,
				sourceFfmpegCodec,
				sourceVideoTimeBase,
				tcSettingsVid
			);

		this.cfgDisableEnc = cfgDisableEnc;
		this.sourceVideoFrameRate = sourceVideoFrameRate;
		this.sourceImgDims = sourceImgDims;
		this.tcSettingsVid = tcSettingsVid;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		final String FNC_NAME = getClass().getSimpleName() + ".close()";

		try {
			flushEverythingBeforeClosing();
		} catch (FfmpegGenericException e) {
			logError(FNC_NAME, "FfmpegGenericException caught: " + e.getMessage());
		} finally {
			if (swsCtx != null) { swscale.sws_freeContext(swsCtx); swsCtx = null; }

			needsSwScaler = false;

			super.closeTranscoder();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void setEnDeCoderBasics(
				@NonNull AVCodecContext enDeCoderCtx,
				@NonNull ImageDimensions imgDims,
				int pixelFmt,
				@NonNull RationalNumber timeBase,
				@NonNull RationalNumber videoFps
			) {
		final int FF_AUTH_THREAD_COUNT = 0;
		final int FF_THREAD_FRAME = 1;
		final int FF_THREAD_SLICE = 2;
		enDeCoderCtx.thread_count(FF_AUTH_THREAD_COUNT);
		enDeCoderCtx.thread_type(FF_THREAD_FRAME | FF_THREAD_SLICE);

		//
		enDeCoderCtx.width(imgDims.imgWidth());
		enDeCoderCtx.height(imgDims.imgHeight());
		enDeCoderCtx.pix_fmt(pixelFmt);

		// Optional but often helpful if known
		try (AVRational tmpVideoTb = new AVRational()) {
			tmpVideoTb.num(timeBase.getNumerator());
			tmpVideoTb.den(timeBase.getDenominator());
			enDeCoderCtx.time_base(tmpVideoTb);
		}
		try (AVRational tmpVideoFps = new AVRational()) {
			tmpVideoFps.num(videoFps.getNumerator());
			tmpVideoFps.den(videoFps.getDenominator());
			enDeCoderCtx.framerate(tmpVideoFps);
		}
	}

	private void prepareDecoderCtxFromFile(@NonNull AVFormatContext inputAvFmtCtx, int streamIx)
			throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".prepareDecoderCtxFromFile()";

		AVCodecParameters inputCodecPar = inputAvFmtCtx.streams(streamIx).codecpar();

		if (inputCodecPar.codec_id() != sourceFfmpegCodec.getFfmpegId()) {
			throw new IllegalStateException(FNC_NAME + ": Input codec_id=" + inputCodecPar.codec_id() +
					" does not match expected codec_id=" + sourceFfmpegCodec.getFfmpegId());
		}

		int r = avcodec.avcodec_parameters_to_context(decoderCtx, inputCodecPar);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_parameters_to_context()", r);
	}

	private void prepareDecoderCtxFromStream() throws FfmpegDecoderNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".prepareDecoderCtxFromStream()";

		AVCodec inCodec = avcodec.avcodec_find_decoder(sourceFfmpegCodec.getFfmpegId());
		if (inCodec == null) {
			throw new FfmpegDecoderNotFoundException(FNC_NAME + ": Decoder not found");
		}

		decoderCtx = avcodec.avcodec_alloc_context3(inCodec);
		if (decoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoder Context");
		}

		// Required basics
		int pixelFmt = avutil.AV_PIX_FMT_YUV420P;  // @TODO this must match the source
		setEnDeCoderBasics(
				decoderCtx,
				sourceImgDims,
				pixelFmt,
				sourceTimeBase,
				sourceVideoFrameRate
			);

		// CRITICAL for many container codecs (H264/H265 in MP4, etc.):
		// set extradata + extradata_size if available
		/*if (sourceExtradata != null && sourceExtradata.length > 0) {
			int size = sourceExtradata.length;
			BytePointer extra = new BytePointer(avutil.av_mallocz(size + avcodec.AV_INPUT_BUFFER_PADDING_SIZE));
			if (extra == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot allocate Extradata");
			}
			extra.put(sourceExtradata, 0, size);
			decoderCtx.extradata(extra);
			decoderCtx.extradata_size(size);
		}*/
	}

	private void openDecoderCtx(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer streamIx
			) throws FfmpegGenericException, FfmpegDecoderNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".openDecoderCtx()";

		AVCodec avCodecDecoder = avcodec.avcodec_find_decoder(sourceFfmpegCodec.getFfmpegId());
		if (avCodecDecoder == null) {
			throw new FfmpegDecoderNotFoundException(FNC_NAME + ": Input decoder not found for codec_id=" + sourceFfmpegCodec.getFfmpegId());
		}

		decoderCtx = avcodec.avcodec_alloc_context3(avCodecDecoder);
		if (decoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoder Context");
		}

		if (isFromFile) {
			if (inputAvFmtCtx == null || streamIx == null) {
				throw new IllegalArgumentException(FNC_NAME + ": inputAvFmtCtx and streamIx must be non-null");
			}
			prepareDecoderCtxFromFile(inputAvFmtCtx, streamIx);
		} else {
			prepareDecoderCtxFromStream();
		}

		int r = avcodec.avcodec_open2(decoderCtx, avCodecDecoder, (AVDictionary)null);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_open2()", r);

		//
		decodedFrame = avutil.av_frame_alloc();
		if (decodedFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoded Frame");
		}
	}

	private @Nullable AVCodec chooseEncoderForCodec() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".chooseEncoderForCodec()";

		if (tcSettingsVid.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			return null;
		}
		List<String> tmpEncoderNamesToTest = new ArrayList<>();
		switch (tcSettingsVid.ffmpegCodec) {
			case V_AV1 -> {
				tmpEncoderNamesToTest.add("av1_nvenc");
				tmpEncoderNamesToTest.add("libsvtav1");
				tmpEncoderNamesToTest.add("av1_qsv");  // untested
				tmpEncoderNamesToTest.add("av1_amf");  // untested
				tmpEncoderNamesToTest.add("av1_vaapi");  // untested
				tmpEncoderNamesToTest.add("libaom-av1");  // extremely slow
			}
			case V_H264 -> {
				tmpEncoderNamesToTest.add("h264_nvenc");
				tmpEncoderNamesToTest.add("libx264");  // untested
				tmpEncoderNamesToTest.add("libopenh264");
				tmpEncoderNamesToTest.add("h264_qsv");  // untested
				tmpEncoderNamesToTest.add("h264_amf");  // untested
				tmpEncoderNamesToTest.add("h264_vaapi");  // untested
			}
			case V_H265 -> {
				tmpEncoderNamesToTest.add("hevc_nvenc");
				tmpEncoderNamesToTest.add("libx265");  // untested
				tmpEncoderNamesToTest.add("hevc_qsv");  // untested
				tmpEncoderNamesToTest.add("hevc_amf");  // untested
				tmpEncoderNamesToTest.add("hevc_vaapi");  // untested
			}
			case V_MPEG2 -> {
				tmpEncoderNamesToTest.add("mpeg2video");
				tmpEncoderNamesToTest.add("mpeg2_qsv");  // untested
				tmpEncoderNamesToTest.add("mpeg2_vaapi");  // untested
			}
			case V_MPEG4 -> {
				tmpEncoderNamesToTest.add("mpeg4");
				tmpEncoderNamesToTest.add("libxvid");  // untested
			}
			case V_VP8 -> {
				tmpEncoderNamesToTest.add("libvpx");
				tmpEncoderNamesToTest.add("vp8_vaapi");  // untested
			}
			case V_VP9 -> {
				tmpEncoderNamesToTest.add("libvpx-vp9");
				tmpEncoderNamesToTest.add("vp9_vaapi");  // untested
				tmpEncoderNamesToTest.add("vp9_qsv");  // untested
			}
		}
		for (String tmpEncName : tmpEncoderNamesToTest) {
			if (cfgDisableEnc.contains(tmpEncName.toLowerCase())) {
				continue;
			}
			AVCodec tmpCod = avcodec.avcodec_find_encoder_by_name(tmpEncName);
			if (tmpCod != null) {
				logDebug(FNC_NAME, "using '" + tmpEncName + "'");
				return tmpCod;
			}
		}

		// try default encoder
		AVCodec resCod = avcodec.avcodec_find_encoder(tcSettingsVid.ffmpegCodec.getFfmpegId());
		String tmpEncName = (resCod != null && resCod.name() != null ?
				resCod.name().getString() : "-unknown-");
		if (cfgDisableEnc.contains(tmpEncName.toLowerCase())) {
			throw new FfmpegGenericException(FNC_NAME + ": default encoder '" + tmpEncName + "' is disabled");
		}
		logDebug(FNC_NAME, "using default (" + tmpEncName + ")");
		return resCod;
	}

	private void openEncoderCtx() throws FfmpegEncoderNotFoundException, FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openEncoderCtx()";

		AVCodec avCodecEncoder = chooseEncoderForCodec();
		if (avCodecEncoder == null) {
			throw new FfmpegEncoderNotFoundException(FNC_NAME + ": Encoder not found (id=" + tcSettingsVid.ffmpegCodec + ")");
		}

		encoderCtx = avcodec.avcodec_alloc_context3(avCodecEncoder);
		if (encoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoder Context");
		}

		//
		RationalNumber tmpDestFr = sourceVideoFrameRate;
		if (tcSettingsVid.frameRateFixed != FrameRateEnum.UNKNOWN) {
			tmpDestFr = RationalNumber.ofFps(tcSettingsVid.frameRateFixed.getFrDbl());
		} else if (tcSettingsVid.frameRateMax != FrameRateEnum.UNKNOWN) {
			RationalNumber tmpMaxFrRn = RationalNumber.ofFps(tcSettingsVid.frameRateMax.getFrDbl());
			if (tmpDestFr.cmp(tmpMaxFrRn) > 0) {
				tmpDestFr = RationalNumber.ofFps(tcSettingsVid.frameRateMax.getFrDbl());
			}
		}
		// the Time-Base is the inverse of the Frame-Rate
		RationalNumber tmpDestTb = RationalNumber.of(tmpDestFr.getDenominator(), tmpDestFr.getNumerator());

		logDebug(FNC_NAME, "Video Encoder Codec    : " + tcSettingsVid.ffmpegCodec);
		logDebug(FNC_NAME, "Video Encoder TimeBase : " + tmpDestTb.toString(5));
		logDebug(FNC_NAME, "Video Encoder FPS      : " + tmpDestFr.toString(3));
		logDebug(FNC_NAME, "Video Encoder ImageDims: " + destImgDims);
		logDebug(FNC_NAME, "Video Encoder BitRate  : " +
				(tcSettingsVid.bitRate > 0 ? tcSettingsVid.bitRate : "auto"));

		//
		setEnDeCoderBasics(
				encoderCtx,
				destImgDims,
				DEST_PIXEL_FORMAT,
				tmpDestTb,
				tmpDestFr
			);
		encoderCtx.gop_size(60);
		encoderCtx.max_b_frames(0);
		if (tcSettingsVid.bitRate > 0) {
			encoderCtx.bit_rate(tcSettingsVid.bitRate);
		}
		if (tcSettingsVid.ffmpegCodec == FfmpegCodec.V_MJPEG) {
			encoderCtx.color_range(avutil.AVCOL_RANGE_JPEG);  // full range
		}

		AVDictionary opts = new AVDictionary();
		avutil.av_dict_set(opts, "deadline", "good", 0);
		//avutil.av_dict_set(opts, "cpu-used", "4", 0);
		if (tcSettingsVid.ffmpegCodec == FfmpegCodec.V_H264) {
			avutil.av_dict_set(opts, "profile", "100", 0);  // main=77, high=100
		}
		if (tcSettingsVid.bitRate > 0) {
			avutil.av_dict_set(opts, "allow_skip_frames", "1", 0);  // removes the skip-frame warning
			avutil.av_dict_set(opts, "rc_mode", "bitrate", 0);
		}

		int r = avcodec.avcodec_open2(encoderCtx, avCodecEncoder, opts);
		avutil.av_dict_free(opts);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_open2()", r);

		//
		encodedPacket = avcodec.av_packet_alloc();
		if (encodedPacket == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoded Packet");
		}
	}

	private void openSwScalerCtx(@NonNull ImageDimensions sourceImgDims) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openSwScalerCtx()";

		if (decoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx is null");
		}

		if (decoderCtx.pix_fmt() < 0 || sourceImgDims.imgWidth() < 1 || sourceImgDims.imgHeight() < 1) {
			return;
		}

		//
		boolean needsScale = (
				sourceImgDims.imgWidth() != destImgDims.imgWidth() ||
				sourceImgDims.imgHeight() != destImgDims.imgHeight()
			);
		boolean needsPixelFormatConversion = (decoderCtx.pix_fmt() != DEST_PIXEL_FORMAT);
		needsSwScaler = (needsScale || needsPixelFormatConversion);

		if (swsCtx != null) { swscale.sws_freeContext(swsCtx); swsCtx = null; }
		if (convertedFrame != null) { avutil.av_frame_free(convertedFrame); convertedFrame = null; }

		if (! needsSwScaler) {
			return;
		}

		//
		swsCtx = swscale.sws_getContext(
				sourceImgDims.imgWidth(), sourceImgDims.imgHeight(),
				decoderCtx.pix_fmt(),
				destImgDims.imgWidth(), destImgDims.imgHeight(),
				DEST_PIXEL_FORMAT,
				swscale.SWS_BILINEAR,
				null, null,
				(double[])null
			);
		if (swsCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot create SWS Context");
		}

		//
		convertedFrame = avutil.av_frame_alloc();
		if (convertedFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Converted Frame");
		}
		convertedFrame.format(DEST_PIXEL_FORMAT);
		convertedFrame.width(destImgDims.imgWidth());
		convertedFrame.height(destImgDims.imgHeight());

		int r = avutil.av_frame_get_buffer(convertedFrame, 32);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);
	}

	@Override
	protected void initTranscoder(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer streamIx
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		destImgDims = sourceImgDims.scale(tcSettingsVid.imgDimsMax);

		//
		openDecoderCtx(isFromFile, inputAvFmtCtx, streamIx);

		//
		openEncoderCtx();

		//
		isTranscoderReady = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void internalTranscodePacket(@NonNull AVPacket inPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalTranscodePacket()";

		if (! isTranscoderReady) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder not initialized. Call initTranscoder() first.");
		}
		if (decoderCtx == null || decodedFrame == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx or decodedFrame is null");
		}
		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}
		if (needsSwScaler && convertedFrame == null) {
			throw new IllegalStateException(FNC_NAME + ": convertedFrame is null");
		}

		int r = avcodec.avcodec_send_packet(decoderCtx, inPkt);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);

		while (true) {
			r = avcodec.avcodec_receive_frame(decoderCtx, decodedFrame);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_receive_frame()", r);

			//
			if (! doneCheckingForSwScaler && (
						swsCtx == null ||
						swsLastSrcW != decodedFrame.width() || swsLastSrcH != decodedFrame.height() ||
						swsLastSrcFmt != decodedFrame.format()
					)) {
				openSwScalerCtx(ImageDimensions.of(decodedFrame.width(), decodedFrame.height()));

				swsLastSrcW = decodedFrame.width();
				swsLastSrcH = decodedFrame.height();
				swsLastSrcFmt = decodedFrame.format();
				doneCheckingForSwScaler = (swsLastSrcW > 0 && swsLastSrcH > 0 && swsLastSrcFmt >= 0);

				if (doneCheckingForSwScaler) {
					logDebug(FNC_NAME, "using SwScaler: " + (needsSwScaler ? "yes" : "no"));
				}
			}

			//
			AVFrame frameForEncoder = decodedFrame;
			if (needsSwScaler && convertedFrame != null) {
				r = avutil.av_frame_make_writable(convertedFrame);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_make_writable()", r);

				r = swscale.sws_scale_frame(swsCtx, convertedFrame, decodedFrame);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "sws_scale_frame()", r);

				convertedFrame.pts(decodedFrame.pts());
				frameForEncoder = convertedFrame;
			}

			assignFramePtsToEncoderTimeBase(frameForEncoder, sourceTimeBase);

			r = avcodec.avcodec_send_frame(encoderCtx, frameForEncoder);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);

			drainEncoderPackets();

			avutil.av_frame_unref(decodedFrame);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void flushEverythingBeforeClosing() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".flushEverythingBeforeClosing()";

		if (!isTranscoderReady) {
			return;
		}

		// 1) Flush decoder: send null packet, pull remaining decoded frames
		int r = avcodec.avcodec_send_packet(decoderCtx, (AVPacket)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);
		}

		while (true) {
			r = avcodec.avcodec_receive_frame(decoderCtx, decodedFrame);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_receive_frame()", r);

			AVFrame frameForEncoder = decodedFrame;
			if (needsSwScaler) {
				r = avutil.av_frame_make_writable(convertedFrame);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_make_writable()", r);

				r = swscale.sws_scale_frame(swsCtx, convertedFrame, decodedFrame);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "sws_scale_frame()", r);

				if (convertedFrame != null && decodedFrame != null) {
					convertedFrame.pts(decodedFrame.pts());
					frameForEncoder = convertedFrame;
				}
			}

			if (frameForEncoder != null) {
				assignFramePtsToEncoderTimeBase(frameForEncoder, sourceTimeBase);
			}

			r = avcodec.avcodec_send_frame(encoderCtx, frameForEncoder);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_frame(frameForEncoder)", r);

			drainEncoderPackets();
			avutil.av_frame_unref(decodedFrame);
		}

		// 2) Flush encoder: send null frame, pull delayed re-encoded video packets
		r = avcodec.avcodec_send_frame(encoderCtx, (AVFrame)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_frame(null)", r);
		}
		drainEncoderPackets();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void assignFramePtsToEncoderTimeBase(@NonNull AVFrame frame, @NonNull RationalNumber srcTb) {
		if (encoderCtx == null) {
			return;
		}
		if (frame.pts() == avutil.AV_NOPTS_VALUE) {
			return;
		}
		RationalNumber encTb = RationalNumber.of(encoderCtx.time_base().num(), encoderCtx.time_base().den());
		long ptsRescaled;
		try (AVRational src = new AVRational(); AVRational dst = new AVRational()) {
			src.num(srcTb.getNumerator()).den(srcTb.getDenominator());
			dst.num(encTb.getNumerator()).den(encTb.getDenominator());
			ptsRescaled = avutil.av_rescale_q(frame.pts(), src, dst);
		}
		frame.pts(ptsRescaled);
	}

}
