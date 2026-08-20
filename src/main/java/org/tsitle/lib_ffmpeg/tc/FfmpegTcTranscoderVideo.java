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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_ffmpeg.helpers.*;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

import java.util.ArrayList;
import java.util.List;

/**
 * Transcoder for video streams.
 */
final class FfmpegTcTranscoderVideo extends FfmpegTcTranscoderBase implements AutoCloseable {

	private static final FfmpegPixelFmt OUTPUT_PIXEL_FORMAT_DEFAULT = FfmpegPixelFmt.YUV420P;

	/** Maximum width and height of an JPEG image for MJPEG in RTP-compatibility mode */
	private static final int MJPEG_RTP_IMAGE_MAX_WIDTH_HEIGHT = 2040;

	private final @NonNull FfmpegTcParamsInpVideo sourceParamsVideo = new FfmpegTcParamsInpVideo();
	private final @NonNull FfmpegTcSettingsOutVideo tcSettingsVid = new FfmpegTcSettingsOutVideo();

	private @NonNull FfmpegPixelFmt outputPixelFormat = OUTPUT_PIXEL_FORMAT_DEFAULT;
	private final @NonNull RationalNumber outputFrameRate;
	private @NonNull ImageDimensions outputImgDims;

	private @Nullable SwsContext swsCtx = null;
	private boolean doneCheckingForSwScaler = false;
	private boolean needsSwScaler = false;
	private int swsLastSrcW = -2;
	private int swsLastSrcH = -2;
	private int swsLastSrcFmt = -2;

	private long nextEncoderPts = 0L;
	private long lastSentEncoderPts = Long.MIN_VALUE;

	private long droppedFrameCount = 0L;

	private boolean haveSentCdcParamsVideo = false;
	private boolean haveCheckedOutputFrameForH26xAnnexB = false;
	private boolean isOutputFmtH26xAnnexB = false;
	private @Nullable FfmpegHelperBsfH26xInterface bsfH26x = null;
	private final @NonNull FfmpegAvPktBasics cacheBsfAvPktBasics = new FfmpegAvPktBasics();

	private final @NonNull FfmpegCdcParamsVideo cdcParamsVideo = new FfmpegCdcParamsVideo();

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceParamsVideo Video source parameters
	 * @param tcSettingsVid Transcoder settings for video
	 */
	FfmpegTcTranscoderVideo(
				@Nullable LogMsgInterface logMsgInterface,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegTcParamsInpVideo sourceParamsVideo,
				@NonNull FfmpegTcSettingsOutVideo tcSettingsVid
			) {
		super(
				logMsgInterface,
				true,
				ffmpegReceiveTcAvInterface,
				sourceParamsVideo.ffmpegCodec,
				sourceParamsVideo.timeBase,
				sourceParamsVideo.extradataHex,
				tcSettingsVid
			);

		this.sourceParamsVideo.copyFrom(sourceParamsVideo);
		this.tcSettingsVid.copyFrom(tcSettingsVid);
		// libaom-av1 doesn't work all that well as is extremely slow
		this.tcSettingsVid.cfgDisabledEncodersList.add(FfmpegTcEncoderLists.ENCODER_NAME_AV1_LIBAOMAV1);

		//
		if (tcSettingsVid.cfgFrCm == FfmpegTcSettingsOutVideo.FrameRateConversionMode.FIXED) {
			this.outputFrameRate = RationalNumber.ofFps(tcSettingsVid.cfgFrameRateFixed.getFrDbl());
		} else if (tcSettingsVid.cfgFrCm == FfmpegTcSettingsOutVideo.FrameRateConversionMode.LIMITED) {
			RationalNumber tmpMaxFrRn = RationalNumber.ofFps(tcSettingsVid.cfgFrameRateMax.getFrDbl());
			if (sourceParamsVideo.frameRate.cmp(tmpMaxFrRn) > 0) {
				this.outputFrameRate = RationalNumber.ofFps(tcSettingsVid.cfgFrameRateMax.getFrDbl());
			} else {
				this.outputFrameRate = RationalNumber.of(sourceParamsVideo.frameRate);
			}
		} else {
			this.outputFrameRate = RationalNumber.of(sourceParamsVideo.frameRate);
		}
		if (this.outputFrameRate.toDouble() < 1.0 || this.outputFrameRate.toDouble() > 120.0) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
					"Invalid output frame rate: " + this.outputFrameRate);
		}

		//
		if (tcSettingsVid.cfgImgScalingMode == FfmpegTcSettingsOutVideo.ImageScalingMode.FIXED) {
			final int tmpScaleVal = tcSettingsVid.cfgImgDimsFixed;
			if (tmpScaleVal < 2) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
						"Invalid image dimensions for fixed mode: " + tmpScaleVal);
			}
			this.outputImgDims = sourceParamsVideo.imgDims.scaleToFixed(tmpScaleVal);
		} else if (tcSettingsVid.cfgImgScalingMode == FfmpegTcSettingsOutVideo.ImageScalingMode.LIMITED) {
			final int tmpScaleVal = tcSettingsVid.cfgImgDimsMax;
			if (tmpScaleVal < 2) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
						"Invalid image dimensions for limited mode: " + tmpScaleVal);
			}
			this.outputImgDims = sourceParamsVideo.imgDims.scaleWithLimiter(tmpScaleVal);
		} else {
			this.outputImgDims = ImageDimensions.of(sourceParamsVideo.imgDims);
		}
		if (this.outputImgDims.imgWidth() < 2 || this.outputImgDims.imgHeight() < 2) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
					"Invalid output image dimensions: " + this.outputImgDims);
		}

		//
		if (sourceParamsVideo.pixelFmt == FfmpegPixelFmt.UNKNOWN) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
					"Pixel format must be set");
		}
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_MJPEG &&
				tcSettingsVid.cfgOutputModeMjpeg == FfmpegTcSettingsOutVideo.OutputModeMjpeg.RTP) {
			// MJPEG with standard huffman tables and YUV422P doesn't work with RTP for reasons unknown
			this.outputPixelFormat = FfmpegPixelFmt.YUV420P;
			//
			if (this.outputImgDims.imgWidth() > MJPEG_RTP_IMAGE_MAX_WIDTH_HEIGHT ||
					this.outputImgDims.imgHeight() > MJPEG_RTP_IMAGE_MAX_WIDTH_HEIGHT) {
				this.outputImgDims = this.outputImgDims.scaleWithLimiter(MJPEG_RTP_IMAGE_MAX_WIDTH_HEIGHT);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public long getDroppedFrameCount() {
		return droppedFrameCount;
	}

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

	@Override
	protected void initTranscoder(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer subStreamIx
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		isSourceFromFile = isFromFile;

		//
		openDecoderCtx(isFromFile, inputAvFmtCtx, subStreamIx);

		//
		openEncoderCtx();

		//
		cdcParamsVideo.clear();
		cdcParamsVideo.ffmpegCodec = tcSettingsVid.cfgFfmpegCodec;
		cdcParamsVideo.imgDims = ImageDimensions.of(outputImgDims);
		cdcParamsVideo.fps = RationalNumber.of(sourceParamsVideo.frameRate);
		/*
		 * Note: the 'extradata' will be set later
		 */

		//
		isTranscoderReady = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void internalSetNextInputPacket(@NonNull AVPacket inPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalSetNextInputPacket()";

		if (! isTranscoderReady) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder not initialized. Call initTranscoder() first.");
		}
		if (decoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx is null");
		}

		//
		int r = avcodec.avcodec_send_packet(decoderCtx, inPkt);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);

		recvAllFrames();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean convertEncodedPacketFormat(@NonNull AVPacket encodedPacket) throws FfmpegGenericException {
		if (encoderCtx == null ||
				! (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H264 ||
						tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H265)) {
			if (!haveSentCdcParamsVideo && ffmpegReceiveTcAvInterface != null && encoderCtx != null) {
				internalGetEncoderExtradata_video(
						encoderCtx,
						false,
						FfmpegPktConvModeH26x.PASSTHROUGH,
						cdcParamsVideo
					);
				ffmpegReceiveTcAvInterface.cbReceiveCdcParamsVideo(cdcParamsVideo);
				haveSentCdcParamsVideo = true;
			}
			return false;
		}

		if (! haveCheckedOutputFrameForH26xAnnexB) {
			isOutputFmtH26xAnnexB = FfmpegHelperBsfH26xToLengthPrefixed.isAnnexB(encodedPacket);
			/*logDebug(getClass().getSimpleName() + ".convertEncodedPacketFormat()",
					"**************************** isOutputFmtH26xAnnexB=" + isOutputFmtH26xAnnexB);*/
			internalGetEncoderExtradata_video(
					encoderCtx,
					isOutputFmtH26xAnnexB,
					tcSettingsVid.cfgOutputModeH26x,
					cdcParamsVideo
				);
			if (ffmpegReceiveTcAvInterface != null && ! cdcParamsVideo.extradataHex.isEmpty()) {
				ffmpegReceiveTcAvInterface.cbReceiveCdcParamsVideo(cdcParamsVideo);
				haveSentCdcParamsVideo = true;
			}
			haveCheckedOutputFrameForH26xAnnexB = true;
		}

		if (tcSettingsVid.cfgOutputModeH26x == FfmpegPktConvModeH26x.PASSTHROUGH ||
				(tcSettingsVid.cfgOutputModeH26x == FfmpegPktConvModeH26x.ANNEXB && isOutputFmtH26xAnnexB) ||
				(tcSettingsVid.cfgOutputModeH26x == FfmpegPktConvModeH26x.LENGTH_PREFIXED && ! isOutputFmtH26xAnnexB)) {
			return false;
		}

		if (ffmpegReceiveTcAvInterface == null) {
			return false;
		}

		//
		if (bsfH26x == null) {
			boolean needBsfAnnexB = (! isOutputFmtH26xAnnexB && tcSettingsVid.cfgOutputModeH26x == FfmpegPktConvModeH26x.ANNEXB);
			boolean needBsfLp = (isOutputFmtH26xAnnexB && tcSettingsVid.cfgOutputModeH26x == FfmpegPktConvModeH26x.LENGTH_PREFIXED);
			if (needBsfAnnexB) {
				AVCodecParameters outputCodecPar = avcodec.avcodec_parameters_alloc();
				int r = avcodec.avcodec_parameters_from_context(outputCodecPar, encoderCtx);
				FfmpegHelperFfError.checkFfmpegResult(
						getClass().getSimpleName() + ".convertEncodedPacketFormat()",
						"avcodec_parameters_to_context()",
						r
					);
				bsfH26x = new FfmpegHelperBsfH26xToAnnexB(
						tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H264,
						outputCodecPar,
						encoderCtx.time_base()
					);
				avcodec.avcodec_parameters_free(outputCodecPar);
			} else if (needBsfLp) {
				bsfH26x = new FfmpegHelperBsfH26xToLengthPrefixed();
			}
		}

		bsfH26x.setInputPacket(encodedPacket);
		while (true) {
			AVPacket tmpAvPkt = avcodec.av_packet_alloc();
			if (tmpAvPkt == null) {
				throw new RuntimeException(getClass().getSimpleName() + ".convertEncodedPacketFormat(): " +
						"Cannot allocate AVPacket");
			}
			if (! bsfH26x.receiveOneConvertedPacket(tmpAvPkt)) {
				avcodec.av_packet_unref(tmpAvPkt);
				break;
			}
			FfmpegHelperPktConverter.convertFfToBasics(
					true,
					true,
					tmpAvPkt,
					RationalNumber.of(encoderCtx.time_base().num(), encoderCtx.time_base().den()),
					cacheBsfAvPktBasics
				);
			avcodec.av_packet_unref(tmpAvPkt);

			ffmpegReceiveTcAvInterface.cbReceiveTranscodedVideoFrame(cacheBsfAvPktBasics);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void flushEverythingBeforeClosing() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".flushEverythingBeforeClosing()";

		if (! isTranscoderReady) {
			return;
		}
		if (decoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx is null");
		}
		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		// flush decoder: send null packet, pull remaining decoded frames
		//noinspection RedundantCast
		int r = avcodec.avcodec_send_packet(decoderCtx, (AVPacket)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);
		}

		// receive all remaining decoded frames
		recvAllFrames();

		// flush encoder: send null frame, pull delayed re-encoded video packets
		//noinspection RedundantCast
		r = avcodec.avcodec_send_frame(encoderCtx, (AVFrame)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_frame(null)", r);
		}
		drainEncoderPackets();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void setEnDeCoderBasics(
				@NonNull String fncName,
				@NonNull AVCodecContext enDeCoderCtx,
				@NonNull ImageDimensions imgDims,
				@NonNull FfmpegPixelFmt pixelFmt,
				@NonNull RationalNumber timeBase,
				@NonNull RationalNumber videoFps
			) throws FfmpegGenericException {
		final int FF_AUTH_THREAD_COUNT = 0;
		final int FF_THREAD_FRAME = 1;
		final int FF_THREAD_SLICE = 2;
		enDeCoderCtx.thread_count(FF_AUTH_THREAD_COUNT);
		enDeCoderCtx.thread_type(FF_THREAD_FRAME | FF_THREAD_SLICE);

		//
		enDeCoderCtx.width(imgDims.imgWidth());
		enDeCoderCtx.height(imgDims.imgHeight());
		enDeCoderCtx.pix_fmt(
				FfmpegHelperPixelFmtConv.convertPixelFmtToInt(fncName, pixelFmt)
			);

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

	private void prepareDecoderCtxFromFile(@NonNull AVFormatContext inputAvFmtCtx, int subStreamIx)
			throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".prepareDecoderCtxFromFile()";

		AVCodecParameters inputCodecPar = inputAvFmtCtx.streams(subStreamIx).codecpar();

		if (inputCodecPar.codec_id() != sourceFfmpegCodec.getFfmpegId()) {
			throw new IllegalStateException(FNC_NAME + ": Input codec_id=" + inputCodecPar.codec_id() +
					" does not match expected codec_id=" + sourceFfmpegCodec.getFfmpegId());
		}

		int r = avcodec.avcodec_parameters_to_context(decoderCtx, inputCodecPar);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_parameters_to_context()", r);
	}

	private void prepareDecoderCtxFromStream() throws FfmpegDecoderNotFoundException, FfmpegGenericException {
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
		setEnDeCoderBasics(
				FNC_NAME,
				decoderCtx,
				sourceParamsVideo.imgDims,
				sourceParamsVideo.pixelFmt,
				sourceTimeBase,
				sourceParamsVideo.frameRate
			);

		//
		if (! haveCheckedInputFrameForH26xAnnexB &&
				(sourceFfmpegCodec == FfmpegCodec.V_H264 || sourceFfmpegCodec == FfmpegCodec.V_H265)) {
			throw new IllegalStateException(FNC_NAME + ": have not checked input frame for H26x AnnexB");
		}
		internalSetDecoderExtradata_video(
				decoderCtx,
				sourceFfmpegCodec,
				isInputFmtH26xAnnexB,
				sourceExtradataHex
			);
	}

	private void openDecoderCtx(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer subStreamIx
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
			if (inputAvFmtCtx == null || subStreamIx == null) {
				throw new IllegalArgumentException(FNC_NAME + ": inputAvFmtCtx and subStreamIx must be non-null");
			}
			prepareDecoderCtxFromFile(inputAvFmtCtx, subStreamIx);
		} else {
			prepareDecoderCtxFromStream();
		}

		int r = avcodec.avcodec_open2(decoderCtx, avCodecDecoder, (AVDictionary)null);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_open2()", r);

		//
		decodedFrame = avutil.av_frame_alloc();
		if (decodedFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoded Frame");
		}
	}

	private @Nullable AVCodec chooseEncoderForCodec() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".chooseEncoderForCodec()";

		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.UNKNOWN) {
			return null;
		}
		List<String> tmpEncoderNamesToTest;
		if (FfmpegTcEncoderLists.ENC_BY_CODEC.containsKey(tcSettingsVid.cfgFfmpegCodec)) {
			tmpEncoderNamesToTest = FfmpegTcEncoderLists.ENC_BY_CODEC.get(tcSettingsVid.cfgFfmpegCodec);
		} else {
			tmpEncoderNamesToTest = new ArrayList<>();
		}
		for (String tmpEncName : tmpEncoderNamesToTest) {
			if (tcSettingsVid.cfgDisabledEncodersList.contains(tmpEncName.toLowerCase())) {
				continue;
			}
			AVCodec tmpCod = avcodec.avcodec_find_encoder_by_name(tmpEncName);
			if (tmpCod != null) {
				logDebug(FNC_NAME, "using '" + tmpEncName + "'");
				return tmpCod;
			}
		}

		// try default encoder
		AVCodec resCod = avcodec.avcodec_find_encoder(tcSettingsVid.cfgFfmpegCodec.getFfmpegId());
		String tmpEncName = (resCod != null && resCod.name() != null ?
				resCod.name().getString() : "-unknown-");
		if (tcSettingsVid.cfgDisabledEncodersList.contains(tmpEncName.toLowerCase())) {
			throw new FfmpegGenericException(FNC_NAME + ": default encoder '" + tmpEncName + "' is disabled");
		}
		logDebug(FNC_NAME, "using default (" + tmpEncName + ")");
		return resCod;
	}

	private void openEncoderCtx() throws FfmpegEncoderNotFoundException, FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openEncoderCtx()";

		AVCodec avCodecEncoder = chooseEncoderForCodec();
		if (avCodecEncoder == null) {
			throw new FfmpegEncoderNotFoundException(FNC_NAME + ": Encoder not found (id=" + tcSettingsVid.cfgFfmpegCodec + ")");
		}
		final String avEncName = (avCodecEncoder.name() != null ? avCodecEncoder.name().getString() : "-unknown-");

		encoderCtx = avcodec.avcodec_alloc_context3(avCodecEncoder);
		if (encoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoder Context for '" + avEncName + "'");
		}

		// make sure it is divisible by 8
		final long tmpBrBps = ((((tcSettingsVid.cfgBitRateKbps > 0 ? tcSettingsVid.cfgBitRateKbps : 4096) * 1000L) / 8L) * 8L);

		// the Time-Base is the inverse of the Frame-Rate
		RationalNumber tmpDestTb = RationalNumber.of(outputFrameRate.getDenominator(), outputFrameRate.getNumerator());

		logDebug(FNC_NAME, "Video Encoder Codec    : " + tcSettingsVid.cfgFfmpegCodec);
		logDebug(FNC_NAME, "Video Encoder TimeBase : " + tmpDestTb.toString(5));
		logDebug(FNC_NAME, "Video Encoder FPS      : " + outputFrameRate.toString(3));
		logDebug(FNC_NAME, "Video Encoder ImageDims: " + outputImgDims);
		logDebug(FNC_NAME, "Video Encoder BitRate  : " + (tmpBrBps / 1024L) + " kb/s");

		//
		setEnDeCoderBasics(
				FNC_NAME,
				encoderCtx,
				outputImgDims,
				outputPixelFormat,
				tmpDestTb,
				outputFrameRate
			);
		/*
		 * GOP size (Group of Pictures) is the number of frames between two full keyframes (I-frames)
		 */
		final int tmpGop = switch (tcSettingsVid.cfgGopSize) {
				case EVERY_30_FRAMES -> 30;
				case EVERY_60_FRAMES -> 60;
			};
		encoderCtx.gop_size(tmpGop);
		/*
		 * The maximum number of consecutive B-frames the encoder is allowed to place between non-B-frames (I or P frames).
		 * 0 means let the encoder decide.
		 * B-frames (Bi-predictive frames) heavily improve compression and are excellent for static or slow-moving scenes,
		 * but they degrade quality and introduce visual artifacts in high-motion content (like gaming or sports) if overused.
		 */
		encoderCtx.max_b_frames(0);

		/*
		 * Important for AV1/H.264/H.265 when muxers expect codec config in extradata.
		 * Must be set before avcodec_open2().
		 */
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_AV1 ||  // @CODEC
				tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H264 ||
				tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H265 ||
				tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_MPEG2 ||
				tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_MPEG4 ||
				tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_THEORA) {
			encoderCtx.flags(encoderCtx.flags() | avcodec.AV_CODEC_FLAG_GLOBAL_HEADER);
			//logDebug(FNC_NAME, "Enabled AV_CODEC_FLAG_GLOBAL_HEADER for " + tcSettingsVid.ffmpegCodec);
		}

		setCodecSpecificEncoderOptions(avEncName, tmpBrBps);

		AVDictionary opts = new AVDictionary();
		int r = avcodec.avcodec_open2(encoderCtx, avCodecEncoder, opts);
		avutil.av_dict_free(opts);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_open2()", r);

		//
		encodedPacket = avcodec.av_packet_alloc();
		if (encodedPacket == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoded Packet");
		}
	}

	private void setCodecSpecificEncoderOptions(@NonNull String avEncName, long bitRateBps) {
		if (encoderCtx == null) {
			return;
		}
		// bit rate
		if (avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_AV1_LIBSVTAV1) ||
				avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_AV1_NVENC)) {
			encoderCtx.rc_min_rate((long)((double)bitRateBps * 0.75));
			encoderCtx.rc_max_rate((long)((double)bitRateBps * 1.25));
		} else {
			encoderCtx.bit_rate(bitRateBps);
		}

		// ---------------------------------------------------------

		// AV1
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_AV1) {
			/*
			 * libsvtav1: preset: 0-2 archival, 3-5 high quality, 6-8 balanced, 9-11 realtime-ish, 12-13 fastest possible
			 *            crf: 18 visually lossless, 24 very high quality, 28 default-ish, 32 good streaming, 36 lower bitrate, 40+ heavily compressed
			 * libaom-av1: cpu-used: 0 best quality, 2 very high quality, 4 balanced, 6 fast, 8 fastest
			 *            crf: ...
			 * av1_nvenc: rc: 0 constqp, 1 VBR, 2 CBR
			 *                --> for constqp and CBR the CQ/PRESET don't matter
			 */
			String tmpValCu;
			String tmpValCrf;
			if (avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_AV1_NVENC)) {
				//noinspection EnhancedSwitchMigration
				switch (tcSettingsVid.cfgQualitySpeedRatioAv1) {
					case FASTEST: tmpValCu = "14"; tmpValCrf = "40"; break;
					case BALANCED: tmpValCu = "7"; tmpValCrf = "32"; break;
					default: tmpValCu = "1"; tmpValCrf = "24"; break;
				}
			} else if (avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_AV1_LIBSVTAV1)) {
				//noinspection EnhancedSwitchMigration
				switch (tcSettingsVid.cfgQualitySpeedRatioAv1) {
					case FASTEST: tmpValCu = "11"; tmpValCrf = "32"; break;
					case BALANCED: tmpValCu = "6"; tmpValCrf = "28"; break;
					default: tmpValCu = "5"; tmpValCrf = "24"; break;  // lower preset values don't work that well
				}
			} else {
				//noinspection EnhancedSwitchMigration
				switch (tcSettingsVid.cfgQualitySpeedRatioVpX) {
					case FASTEST: tmpValCu = "8"; tmpValCrf = "32"; break;
					case BALANCED: tmpValCu = "4"; tmpValCrf = "28"; break;
					default: tmpValCu = "1"; tmpValCrf = "24"; break;
				}
			}
			avutil.av_opt_set(encoderCtx, "cpu-used", tmpValCu, 0);  // (libaom-av1) Quality/Speed ratio modifier
			avutil.av_opt_set(encoderCtx, "usage", tmpValCu, 0);  // (libaom-av1) Quality/Speed ratio modifier (alternative mapping)
			avutil.av_opt_set(encoderCtx, "preset", tmpValCu, 0);  // (av1_nvenc|libsvtav1) Encoding preset
			avutil.av_opt_set(encoderCtx, "crf", tmpValCrf, 0);  // (libaom-av1|libsvtav1) Constant Rate Factor
			avutil.av_opt_set(encoderCtx, "cq", tmpValCrf, 0);  // (av1_nvenc) target quality level (0 to 63, 0 means automatic) for constant quality mode in VBR rate control
			avutil.av_opt_set(encoderCtx, "rc", "1", 0);  // (av1_nvenc) preset rate-control
			return;
		}
		// H264
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_H264) {
			/*if (avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_H264_NVENC)) {
				avutil.av_opt_set(encoderCtx, "profile", "high", avutil.AV_OPT_SEARCH_CHILDREN);  // (h264_nvenc)
			} else if (avEncName.equalsIgnoreCase(FfmpegTcEncoderLists.ENCODER_NAME_H264_LIBOPENH264)) {
				avutil.av_opt_set(encoderCtx, "profile", "100", 0);  // (libopenh264) main=77, high=100
			}*/
			avutil.av_opt_set(encoderCtx, "profile", "high", avutil.AV_OPT_SEARCH_CHILDREN);  // (h264_nvenc|libopenh264)
			if (tcSettingsVid.cfgBitRateKbps > 0) {
				avutil.av_opt_set(encoderCtx, "allow_skip_frames", "1", 0);  // (libopenh264) removes the skip-frame warning
				avutil.av_opt_set(encoderCtx, "rc_mode", "bitrate", 0);  // (libopenh264) selects bitrate control mode
			}
			return;
		}
		// MJPEG
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_MJPEG &&
				tcSettingsVid.cfgOutputModeMjpeg == FfmpegTcSettingsOutVideo.OutputModeMjpeg.RTP) {
			encoderCtx.color_range(avutil.AVCOL_RANGE_JPEG);  // full color range
			avutil.av_opt_set(encoderCtx, "huffman", "default", 0);  // (mjpeg) Huffman table strategy
			return;
		}
		// VP8/VP9
		if (tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_VP8 || tcSettingsVid.cfgFfmpegCodec == FfmpegCodec.V_VP9) {
			String tmpValCu;
			String tmpValDl;
			//noinspection EnhancedSwitchMigration
			switch (tcSettingsVid.cfgQualitySpeedRatioVpX) {
				case FASTEST: tmpValCu = "7"; tmpValDl = "realtime"; break;  // cpu-used range 5-8, higher value means faster encoding
				case BALANCED: tmpValCu = "2"; tmpValDl = "good"; break;  // cpu-used range 0-5
				default: tmpValCu = "1"; tmpValDl = "best"; break;  // cpu-used range 0-1
			}
			avutil.av_opt_set(encoderCtx, "cpu-used", tmpValCu, 0);  // (libvpx|libvpx-vp9) Quality/Speed ratio modifier
			avutil.av_opt_set(encoderCtx, "deadline", tmpValDl, 0);  // (libvpx|libvpx-vp9) Time to spend encoding, in microseconds
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
				sourceImgDims.imgWidth() != outputImgDims.imgWidth() ||
				sourceImgDims.imgHeight() != outputImgDims.imgHeight()
			);
		final int tmpTargetPixelFmtInt = FfmpegHelperPixelFmtConv.convertPixelFmtToInt(FNC_NAME, outputPixelFormat);
		boolean needsPixelFormatConversion = (decoderCtx.pix_fmt() != tmpTargetPixelFmtInt);
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
				outputImgDims.imgWidth(), outputImgDims.imgHeight(),
				tmpTargetPixelFmtInt,
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
		convertedFrame.format(tmpTargetPixelFmtInt);
		convertedFrame.width(outputImgDims.imgWidth());
		convertedFrame.height(outputImgDims.imgHeight());

		int r = avutil.av_frame_get_buffer(convertedFrame, 32);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);
	}

	private static void internalSetDecoderExtradata_video(
				@NonNull AVCodecContext decoderCtx,
				@NonNull FfmpegCodec inputFfmpegCodec,
				boolean isInputFmtH26xAnnexB,
				@NonNull ExtradataContainerHex extradataHex
			) throws FfmpegGenericException {
		internalSetDecoderExtradata(
				decoderCtx,
				inputFfmpegCodec,
				isInputFmtH26xAnnexB,
				-1,
				extradataHex
			);
	}

	private static void internalGetEncoderExtradata_video(
				@NonNull AVCodecContext encoderCtx,
				boolean isOutputFmtH26xAnnexB,
				@NonNull FfmpegPktConvModeH26x pktConvModeH26x,
				@NonNull FfmpegCdcParamsBase cdcParams
			) throws FfmpegGenericException {
		internalGetEncoderExtradata(
				encoderCtx,
				isOutputFmtH26xAnnexB,
				pktConvModeH26x,
				cdcParams,
				-1
			);
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

	private void recvAllFrames() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".recvAllFrames()";

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

		while (true) {
			int r = avcodec.avcodec_receive_frame(decoderCtx, decodedFrame);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_receive_frame()", r);

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
				FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_frame_make_writable()", r);

				r = swscale.sws_scale_frame(swsCtx, convertedFrame, decodedFrame);
				FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "sws_scale_frame()", r);

				convertedFrame.pts(decodedFrame.pts());
				frameForEncoder = convertedFrame;
			}

			assignFramePtsToEncoderTimeBase(frameForEncoder, sourceTimeBase);

			if (frameForEncoder.pts() == avutil.AV_NOPTS_VALUE) {
				frameForEncoder.pts(nextEncoderPts++);
				lastSentEncoderPts = frameForEncoder.pts();
			}
			if (lastSentEncoderPts == Long.MIN_VALUE || frameForEncoder.pts() > lastSentEncoderPts) {
				lastSentEncoderPts = frameForEncoder.pts();

				r = avcodec.avcodec_send_frame(encoderCtx, frameForEncoder);
				FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);

				//
				drainEncoderPackets();
			} else {
				++droppedFrameCount;
			}

			avutil.av_frame_unref(decodedFrame);
		}
	}

}
