package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.FfmpegErrorHelper;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Base class for transcoders.
 */
public abstract class FfmpegTranscoderBase {

	protected final @Nullable LogMsgInterface logMsgInterface;
	private final boolean isTranscoderForVideo;
	protected final @Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface;
	protected final @NonNull FfmpegCodec sourceFfmpegCodec;
	protected final @NonNull RationalNumber sourceTimeBase;
	private final @NonNull FfmpegTcSettingsBase tcSettingsBase;

	private @Nullable AVPacket cacheInputPkt = null;

	protected boolean isTranscoderReady = false;
	protected @Nullable AVCodecContext encoderCtx = null;
	protected @Nullable AVPacket encodedPacket = null;
	protected @Nullable AVCodecContext decoderCtx = null;
	protected @Nullable AVFrame decodedFrame = null;
	protected @Nullable AVFrame convertedFrame = null;

	private final @NonNull FfmpegAvPktBasics cacheAvPktBasics = new FfmpegAvPktBasics();

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param isTranscoderForVideo Is this a transcoder for video?
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceFfmpegCodec Source codec
	 * @param sourceTimeBase Source time base
	 * @param transcoderSettings Transcoder settings
	 */
	protected FfmpegTranscoderBase(
				@Nullable LogMsgInterface logMsgInterface,
				boolean isTranscoderForVideo,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegCodec sourceFfmpegCodec,
				@NonNull RationalNumber sourceTimeBase,
				@NonNull FfmpegTcSettingsBase transcoderSettings
			) {
		this.logMsgInterface = logMsgInterface;
		this.isTranscoderForVideo = isTranscoderForVideo;
		this.ffmpegReceiveTcAvInterface = ffmpegReceiveTcAvInterface;
		this.sourceFfmpegCodec = sourceFfmpegCodec;
		this.sourceTimeBase = sourceTimeBase;
		this.tcSettingsBase = transcoderSettings;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public final void transcodePacketFromBuffer(@NonNull FfmpegAvPktBasics inputFrameData)
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (inputFrameData.pktBe.isEmpty()) {
			return;
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsBase.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		if (! isTranscoderReady) {
			// initialize transcoder once from input stream parameters
			initTranscoder(false, null, null);
		}

		convertBufferExtToAvPacket(inputFrameData);
		if (cacheInputPkt != null) {
			internalTranscodePacket(cacheInputPkt);
		}
	}

	@SuppressWarnings("unused")
	public final void transcodePacketFromAvPkt(
				@NonNull AVFormatContext inputAvFmtCtx,
				int streamIx,
				@NonNull FfmpegAvPktBasics inputFrameData
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (inputFrameData.pktBe.isEmpty()) {
			return;
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsBase.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		if (! isTranscoderReady) {
			// initialize transcoder once from input stream parameters
			initTranscoder(true, inputAvFmtCtx, streamIx);
		}

		convertBufferExtToAvPacket(inputFrameData);
		if (cacheInputPkt != null) {
			internalTranscodePacket(cacheInputPkt);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void initTranscoder(
			boolean isFromFile,
			@Nullable AVFormatContext inputAvFmtCtx,
			@Nullable Integer streamIx
		) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException;

	protected final void closeTranscoder() {
		if (cacheInputPkt != null) { avcodec.av_packet_free(cacheInputPkt); cacheInputPkt = null; }

		//
		if (encodedPacket != null) { avcodec.av_packet_free(encodedPacket); encodedPacket = null; }
		if (encoderCtx != null) { avcodec.avcodec_free_context(encoderCtx); encoderCtx = null; }

		if (decoderCtx != null) { avcodec.avcodec_free_context(decoderCtx); decoderCtx = null; }
		if (decodedFrame != null) { avutil.av_frame_free(decodedFrame); decodedFrame = null; }

		if (convertedFrame != null) { avutil.av_frame_free(convertedFrame); convertedFrame = null; }

		isTranscoderReady = false;
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void internalTranscodePacket(@NonNull AVPacket inPkt) throws FfmpegGenericException;

	protected final void drainEncoderPackets() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".drainEncoderPackets()";

		if (encoderCtx == null || encodedPacket == null) {
			throw new IllegalStateException(FNC_NAME + ": Encoder context or encodedPacket is null");
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsBase.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		while (true) {
			int r = avcodec.avcodec_receive_packet(encoderCtx, encodedPacket);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_receive_packet()", r);

			if (ffmpegReceiveTcAvInterface != null && encodedPacket.data() != null) {
				cacheAvPktBasics.pktBe.increaseSize(encodedPacket.size());
				encodedPacket.data().get(cacheAvPktBasics.pktBe.getBaPtr(), 0, encodedPacket.size());
				cacheAvPktBasics.pktBe.setUsed(encodedPacket.size());

				cacheAvPktBasics.ptsUnits = (encodedPacket.pts() == avutil.AV_NOPTS_VALUE ? null : encodedPacket.pts());
				cacheAvPktBasics.dtsUnits = (encodedPacket.dts() == avutil.AV_NOPTS_VALUE ? null : encodedPacket.dts());
				cacheAvPktBasics.timeBase.copyFrom(RationalNumber.of(encoderCtx.time_base().num(), encoderCtx.time_base().den()));

				if (isTranscoderForVideo) {
					ffmpegReceiveTcAvInterface.cbReceiveTranscodedVideoFrame(cacheAvPktBasics);
				} else {
					ffmpegReceiveTcAvInterface.cbReceiveTranscodedAudioSamples(cacheAvPktBasics);
				}
			}

			avcodec.av_packet_unref(encodedPacket);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void flushEverythingBeforeClosing() throws FfmpegGenericException;

	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void convertBufferExtToAvPacket(@NonNull FfmpegAvPktBasics inputFrameData) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".convertBufferExtToAvPacket()";

		if (cacheInputPkt == null) {
			cacheInputPkt = avcodec.av_packet_alloc();
			if (cacheInputPkt == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot allocate Cache Input Packet");
			}
		}
		int r = avcodec.av_new_packet(cacheInputPkt, inputFrameData.pktBe.getUsed());
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_new_packet()", r);
		cacheInputPkt.data().put(inputFrameData.pktBe.getBaPtr(), 0, inputFrameData.pktBe.getUsed());

		cacheInputPkt.pts(inputFrameData.ptsUnits == null ? avutil.AV_NOPTS_VALUE : inputFrameData.ptsUnits);
		cacheInputPkt.dts(inputFrameData.dtsUnits == null ? avutil.AV_NOPTS_VALUE : inputFrameData.dtsUnits);
		cacheInputPkt.time_base().num(inputFrameData.timeBase.getNumerator());
		cacheInputPkt.time_base().den(inputFrameData.timeBase.getDenominator());
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
