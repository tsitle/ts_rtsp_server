package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.FfmpegCdcParamsBase;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_ffmpeg.helpers.FfmpegHelperBsfH26xToLengthPrefixed;
import org.tsitle.lib_ffmpeg.helpers.FfmpegHelperFfError;
import org.tsitle.lib_ffmpeg.helpers.FfmpegHelperPktConverter;
import org.tsitle.lib_ffmpeg.helpers.FfmpegHelperValidateExtradata;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataConvHexFmtHelper;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

import java.util.HexFormat;

/**
 * Base class for transcoders.
 */
abstract class FfmpegTcTranscoderBase {

	protected final @Nullable LogMsgInterface logMsgInterface;
	private final boolean isTranscoderForVideo;
	protected final @Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface;
	protected @NonNull FfmpegCodec sourceFfmpegCodec;
	protected final @NonNull RationalNumber sourceTimeBase = RationalNumber.ofEmpty();
	protected final @NonNull ExtradataContainerHex sourceExtradataHex = ExtradataContainerHex.ofEmpty();
	private final @NonNull FfmpegTcSettingsOutBase tcSettingsOut;

	private @Nullable AVPacket cacheInputPkt = null;

	protected boolean isTranscoderReady = false;
	protected boolean isSourceFromFile = false;
	protected @Nullable AVCodecContext encoderCtx = null;
	protected @Nullable AVPacket encodedPacket = null;
	protected @Nullable AVCodecContext decoderCtx = null;
	protected @Nullable AVFrame decodedFrame = null;
	protected @Nullable AVFrame convertedFrame = null;

	private final @NonNull FfmpegAvPktBasics cacheAvPktBasics = new FfmpegAvPktBasics();

	protected boolean haveCheckedInputFrameForH26xAnnexB = false;
	protected boolean isInputFmtH26xAnnexB = false;

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param isTranscoderForVideo Is this a transcoder for video?
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceFfmpegCodec Source codec
	 * @param sourceTimeBase Source time base
	 * @param sourceExtradataHex Source 'extradata'
	 * @param tcSettingsOut Output settings
	 */
	protected FfmpegTcTranscoderBase(
				@Nullable LogMsgInterface logMsgInterface,
				boolean isTranscoderForVideo,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegCodec sourceFfmpegCodec,
				@NonNull RationalNumber sourceTimeBase,
				@NonNull ExtradataContainerHex sourceExtradataHex,
				@NonNull FfmpegTcSettingsOutBase tcSettingsOut
			) {
		this.logMsgInterface = logMsgInterface;
		this.isTranscoderForVideo = isTranscoderForVideo;
		this.ffmpegReceiveTcAvInterface = ffmpegReceiveTcAvInterface;
		this.sourceFfmpegCodec = sourceFfmpegCodec;
		this.sourceTimeBase.copyFrom(sourceTimeBase);
		this.sourceExtradataHex.copyFrom(sourceExtradataHex);
		this.tcSettingsOut = tcSettingsOut;

		//
		this.cacheAvPktBasics.isVideo = isTranscoderForVideo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull FfmpegCodec getDestFfmpegCodec() {
		return tcSettingsOut.cfgFfmpegCodec;
	}

	@SuppressWarnings("unused")
	public final void transcodePacketFromBuffer(@NonNull FfmpegAvPktBasics inputFrameData)
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (inputFrameData.pktBe.isEmpty()) {
			return;
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsOut.cfgFfmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		if (! isTranscoderReady) {
			if (isTranscoderForVideo &&
					(sourceFfmpegCodec == FfmpegCodec.V_H264 || sourceFfmpegCodec == FfmpegCodec.V_H265)) {
				isInputFmtH26xAnnexB = FfmpegHelperBsfH26xToLengthPrefixed.isAnnexB(inputFrameData.pktBe);
				/*logDebug(getClass().getSimpleName() + ".transcodePacketFromBuffer()",
						"**************************** isInputFmtH26xAnnexB=" + isInputFmtH26xAnnexB);*/
				haveCheckedInputFrameForH26xAnnexB = true;
			}

			// initialize transcoder once from input stream parameters
			initTranscoder(false, null, null);
		} else if (isSourceFromFile) {
			throw new FfmpegGenericException(getClass().getSimpleName() + ".transcodePacketFromBuffer(): " +
					"Transcoder was already initialized for source=file");
		}

		convertBufferExtToAvPacket(inputFrameData);
		if (cacheInputPkt != null) {
			internalSetNextInputPacket(cacheInputPkt);
		}
	}

	@SuppressWarnings("unused")
	public final void transcodePacketFromAvPkt(
				@NonNull AVFormatContext inputAvFmtCtx,
				int subStreamIx,
				@NonNull FfmpegAvPktBasics inputFrameData
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (inputFrameData.pktBe.isEmpty()) {
			return;
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsOut.cfgFfmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		if (! isTranscoderReady) {
			// initialize transcoder once from input stream parameters
			initTranscoder(true, inputAvFmtCtx, subStreamIx);
		} else if (! isSourceFromFile) {
			throw new FfmpegGenericException(getClass().getSimpleName() + ".transcodePacketFromAvPkt(): " +
					"Transcoder was already initialized for source=buffer");
		}

		convertBufferExtToAvPacket(inputFrameData);
		if (cacheInputPkt != null) {
			internalSetNextInputPacket(cacheInputPkt);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void initTranscoder(
			boolean isFromFile,
			@Nullable AVFormatContext inputAvFmtCtx,
			@Nullable Integer subStreamIx
		) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException;

	protected static void internalSetDecoderExtradata(
				@NonNull AVCodecContext decoderCtx,
				@NonNull FfmpegCodec inputFfmpegCodec,
				boolean isInputFmtH26xAnnexB,
				int audioChannelCount,
				@NonNull ExtradataContainerHex extradataHex
			) throws FfmpegGenericException {
		FfmpegHelperValidateExtradata.validateForCodec(
				FfmpegTcTranscoderBase.class.getSimpleName() + ".internalSetDecoderExtradata()",
				inputFfmpegCodec,
				audioChannelCount,
				extradataHex
			);

		/*
		 * The 'extradata' is critical for many container codecs (e.g., AAC/AV1/H264/H265/Opus)
		 */
		ExtradataContainerHex tmpEch = switch (inputFfmpegCodec) {  // @CODEC
				case FfmpegCodec.A_AAC,
						FfmpegCodec.A_ALAC,
						FfmpegCodec.A_FLAC,
						FfmpegCodec.A_OPUS,
						FfmpegCodec.A_VORBIS,
						FfmpegCodec.V_AV1,
						FfmpegCodec.V_MPEG2,
						FfmpegCodec.V_MPEG4,
						FfmpegCodec.V_THEORA -> extradataHex;
				case FfmpegCodec.V_H264 -> ExtradataConvHexFmtHelper.convertH264EncoderExtradata(
						isInputFmtH26xAnnexB,
						extradataHex
					);
				case FfmpegCodec.V_H265 -> ExtradataConvHexFmtHelper.convertH265EncoderExtradata(
						isInputFmtH26xAnnexB,
						extradataHex
					);
				default -> null;
			};
		if (tmpEch == null) {
			return;
		}
		byte[] tmpEdBa = HexFormat.of().parseHex(tmpEch.getEd());
		int size = tmpEdBa.length;
		BytePointer extraBp = new BytePointer(avutil.av_mallocz(size + avcodec.AV_INPUT_BUFFER_PADDING_SIZE));
		extraBp.put(tmpEdBa, 0, size);
		decoderCtx.extradata(extraBp);
		decoderCtx.extradata_size(size);
	}

	protected static void internalGetEncoderExtradata(
				@NonNull AVCodecContext encoderCtx,
				boolean isOutputFmtH26xAnnexB,
				@NonNull FfmpegPktConvModeH26x pktConvModeH26x,
				@NonNull FfmpegCdcParamsBase cdcParams,
				int audioChannelCount
			) throws FfmpegGenericException {
		if (encoderCtx.extradata() == null || encoderCtx.extradata_size() < 1) {
			cdcParams.extradataHex.clear();
			FfmpegHelperValidateExtradata.validateForCodec(
					FfmpegTcTranscoderBase.class.getSimpleName() + ".internalGetEncoderExtradata()",
					cdcParams.ffmpegCodec,
					audioChannelCount,
					cdcParams.extradataHex
				);
			return;
		}
		String tmpEdStr;
		try (BytePointer tmpBp = encoderCtx.extradata()) {
			int extradataSize = encoderCtx.extradata_size();
			byte[] ascBytes = new byte[extradataSize];
			tmpBp.position(0).get(ascBytes, 0, extradataSize);
			tmpEdStr = HexFormat.of().withUpperCase().formatHex(ascBytes);
		}
		//
		boolean tmpOutputH26xAsAnnexB = (
				pktConvModeH26x == FfmpegPktConvModeH26x.ANNEXB ||
						(isOutputFmtH26xAnnexB && pktConvModeH26x == FfmpegPktConvModeH26x.PASSTHROUGH)
			);
		ExtradataContainerHex tmpEch = switch (cdcParams.ffmpegCodec) {  // @CODEC
				case FfmpegCodec.A_AAC -> ExtradataContainerHex.ofAac(tmpEdStr);
				case FfmpegCodec.A_ALAC -> ExtradataContainerHex.ofAlac(tmpEdStr);
				case FfmpegCodec.A_FLAC -> ExtradataContainerHex.ofFlac(tmpEdStr);
				case FfmpegCodec.A_OPUS -> ExtradataContainerHex.ofOpus(tmpEdStr);
				case FfmpegCodec.A_VORBIS -> ExtradataContainerHex.ofVorbis(tmpEdStr);
				case FfmpegCodec.V_AV1 -> ExtradataContainerHex.ofAv1(tmpEdStr);
				case FfmpegCodec.V_H264 -> ExtradataConvHexFmtHelper.convertH264EncoderExtradata(
						tmpOutputH26xAsAnnexB,
						tmpEdStr
					);
				case FfmpegCodec.V_H265 -> ExtradataConvHexFmtHelper.convertH265EncoderExtradata(
						tmpOutputH26xAsAnnexB,
						tmpEdStr
					);
				case FfmpegCodec.V_MPEG2 -> ExtradataContainerHex.ofMpeg2(tmpEdStr);
				case FfmpegCodec.V_MPEG4 -> ExtradataContainerHex.ofMpeg4(tmpEdStr);
				case FfmpegCodec.V_THEORA -> ExtradataContainerHex.ofTheora(tmpEdStr);
				default -> null;
			};
		if (tmpEch != null) {
			cdcParams.extradataHex.copyFrom(tmpEch);
		}
	}

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

	protected abstract void internalSetNextInputPacket(@NonNull AVPacket inPkt) throws FfmpegGenericException;

	protected final void drainEncoderPackets() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".drainEncoderPackets()";

		if (encoderCtx == null || encodedPacket == null) {
			throw new IllegalStateException(FNC_NAME + ": Encoder context or encodedPacket is null");
		}
		if (sourceFfmpegCodec == FfmpegCodec.UNKNOWN || tcSettingsOut.cfgFfmpegCodec == FfmpegCodec.UNKNOWN) {
			return;
		}

		while (true) {
			int r = avcodec.avcodec_receive_packet(encoderCtx, encodedPacket);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_receive_packet()", r);

			//
			if (ffmpegReceiveTcAvInterface != null && encodedPacket.data() != null) {
				if (convertEncodedPacketFormat(encodedPacket)) {
					avcodec.av_packet_unref(encodedPacket);
					continue;
				}

				//
				FfmpegHelperPktConverter.convertFfToBasics(
						true,
						isTranscoderForVideo,
						encodedPacket,
						RationalNumber.of(encoderCtx.time_base().num(), encoderCtx.time_base().den()),
						cacheAvPktBasics
					);

				//
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

	protected abstract boolean convertEncodedPacketFormat(@NonNull AVPacket encodedPacket)
			throws FfmpegGenericException;

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

	private void convertBufferExtToAvPacket(@NonNull FfmpegAvPktBasics inputPktBasics) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".convertBufferExtToAvPacket()";

		if (cacheInputPkt == null) {
			cacheInputPkt = avcodec.av_packet_alloc();
			if (cacheInputPkt == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot allocate Cache Input Packet");
			}
		} else {
			avcodec.av_packet_unref(cacheInputPkt);
		}
		final int requiredSize = inputPktBasics.pktBe.getUsed();
		int r = avcodec.av_new_packet(cacheInputPkt, requiredSize);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_new_packet()", r);

		BufferView inpPktPayloadBv = new BufferView(inputPktBasics.pktBe);
		FfmpegHelperPktConverter.convertBasicsToFf(
				inputPktBasics,
				inpPktPayloadBv,
				inputPktBasics.subStreamIndex,
				cacheInputPkt
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
