package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_ffmpeg.helpers.*;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * Transcoder for audio streams.
 */
final class FfmpegTcTranscoderAudio extends FfmpegTcTranscoderBase implements AutoCloseable {

	private final @NonNull FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();
	private final @NonNull FfmpegTcSettingsOutAudio tcSettingsAud = new FfmpegTcSettingsOutAudio();

	private int outputChannelCount = -1;
	private @NonNull SampleRateEnum outputSampleRate = SampleRateEnum.UNKNOWN;

	private @Nullable SwrContext swrCtx = null;
	private boolean needsAudioConversion = false;
	private @Nullable AVAudioFifo audioFifo = null;
	private @Nullable AVFrame fifoReadFrame = null;

	// cache actual SWR input config (from decoded AVFrame)
	private int swrInSampleFmt = avutil.AV_SAMPLE_FMT_NONE;
	private int swrInSampleRate = 0;
	private final @NonNull AVChannelLayout swrInChLayoutObj = new AVChannelLayout();
	private boolean isSwrInChLayoutSet = false;
	private final @NonNull AVChannelLayout tmpNormalizedInLayoutObj = new AVChannelLayout();
	private boolean isTmpNormalizedInLayoutSet = false;

	// audio PTS in encoder timebase units (samples when tb=1/sample_rate)
	private long nextAudioPts = 0L;

	private boolean haveCheckedOutputFrameForAacAdts = false;
	private boolean isOutputFmtAacWithAdts = false;
	private @Nullable FfmpegHelperBsfAacInterface bsfAac = null;
	private final @NonNull FfmpegAvPktBasics cacheBsfAvPktBasics = new FfmpegAvPktBasics();

	private final @NonNull FfmpegCdcParamsAudio cdcParamsAudio = new FfmpegCdcParamsAudio();

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceParamsAudio Audio source parameters
	 * @param tcSettingsAud Transcoder settings for audio
	 */
	FfmpegTcTranscoderAudio(
				@Nullable LogMsgInterface logMsgInterface,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegTcParamsInpAudio sourceParamsAudio,
				@NonNull FfmpegTcSettingsOutAudio tcSettingsAud
			) {
		super(
				logMsgInterface,
				false,
				ffmpegReceiveTcAvInterface,
				sourceParamsAudio.ffmpegCodec,
				sourceParamsAudio.timeBase,
				sourceParamsAudio.extradataHex,
				tcSettingsAud
			);

		//
		this.tcSettingsAud.copyFrom(tcSettingsAud);
		//
		this.cacheBsfAvPktBasics.isVideo = false;
		//
		try {
			updateAudioDecoder(true, sourceParamsAudio, null, null);
		} catch (FfmpegDecoderNotFoundException | FfmpegGenericException e) {
			// this will never happen
		}
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
			if (swrCtx != null) { swresample.swr_free(swrCtx); swrCtx = null; }
			if (audioFifo != null) { avutil.av_audio_fifo_free(audioFifo); audioFifo = null; }
			if (fifoReadFrame != null) { avutil.av_frame_free(fifoReadFrame); fifoReadFrame = null; }

			if (isSwrInChLayoutSet) { avutil.av_channel_layout_uninit(swrInChLayoutObj); isSwrInChLayoutSet = false; }
			if (isTmpNormalizedInLayoutSet) { avutil.av_channel_layout_uninit(tmpNormalizedInLayoutObj); isTmpNormalizedInLayoutSet = false; }

			needsAudioConversion = false;

			super.closeTranscoder();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void updateAudioDecoder(
				boolean isCallFromCtor,
				@NonNull FfmpegTcParamsInpAudio sourceParamsAudio,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer subStreamIx
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".updateAudioDecoder()";

		int prevOpChannelCount = this.outputChannelCount;
		SampleRateEnum prevOpSampleRate = this.outputSampleRate;

		//
		if (tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.UNKNOWN && sourceParamsAudio.channelCount < 1) {
			throw new IllegalArgumentException(FNC_NAME + ": sourceParamsAudio.channelCount must be set");
		}
		if (sourceParamsAudio.channelCount > 1 &&
				tcSettingsAud.cfgChannelCm == FfmpegTcSettingsOutAudio.ChannelConversionMode.DOWNMIX_MONO) {
			this.outputChannelCount = 1;
		} else if (tcSettingsAud.cfgChannelCm == FfmpegTcSettingsOutAudio.ChannelConversionMode.FIXED_STEREO ||
				(sourceParamsAudio.channelCount > 2 &&
					(tcSettingsAud.cfgChannelCm == FfmpegTcSettingsOutAudio.ChannelConversionMode.DOWNMIX_STEREO ||
							tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_MP2 ||  // MP2 only supports mono and stereo
							tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_MP3 ||  // MP3 only supports mono and stereo
							tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_VORBIS ||  // Vorbis only supports mono and stereo
							(tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_OPUS &&
									tcSettingsAud.cfgOutputModeOpus == FfmpegTcSettingsOutAudio.OutputModeOpus.RTP)
						))) {
			this.outputChannelCount = 2;
		} else {
			this.outputChannelCount = sourceParamsAudio.channelCount;
		}

		//
		if (tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.UNKNOWN && sourceParamsAudio.sampleRate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException(FNC_NAME + ": sourceParamsAudio.sampleRate must be set");
		}
		if (tcSettingsAud.cfgSrCm == FfmpegTcSettingsOutAudio.SampleRateConversionMode.PASSTHROUGH &&
				(tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.A_OPUS ||
						tcSettingsAud.cfgOutputModeOpus != FfmpegTcSettingsOutAudio.OutputModeOpus.RTP)) {
			this.outputSampleRate = sourceParamsAudio.sampleRate;
		} else if (tcSettingsAud.cfgSrCm == FfmpegTcSettingsOutAudio.SampleRateConversionMode.FIXED &&
				(tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.A_OPUS ||
						tcSettingsAud.cfgOutputModeOpus != FfmpegTcSettingsOutAudio.OutputModeOpus.RTP)) {
			if (tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.UNKNOWN &&
					tcSettingsAud.cfgSampleRateFixed == SampleRateEnum.UNKNOWN) {
				throw new IllegalArgumentException(FNC_NAME + ": tcSettingsAud.cfgFixedSampleRate must be set");
			}
			this.outputSampleRate = tcSettingsAud.cfgSampleRateFixed;
		} else {
			final int tmpMaxSrHz = tcSettingsAud.cfgSampleRateMax.getSrHz();
			int tmpSourceSrHz = sourceParamsAudio.sampleRate.getSrHz();
			if (tmpMaxSrHz > 0 && tmpSourceSrHz > tmpMaxSrHz) {
				tmpSourceSrHz = tmpMaxSrHz;
			}
			if (tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_OPUS) {
				if (tmpSourceSrHz > SampleRateEnum.SR_024000.getSrHz()) {
					this.outputSampleRate = SampleRateEnum.SR_048000;
				} else if (tmpSourceSrHz > SampleRateEnum.SR_016000.getSrHz()) {
					this.outputSampleRate = SampleRateEnum.SR_024000;
				} else if (tmpSourceSrHz > SampleRateEnum.SR_012000.getSrHz()) {
					this.outputSampleRate = SampleRateEnum.SR_016000;
				} else if (tmpSourceSrHz > SampleRateEnum.SR_008000.getSrHz()) {
					this.outputSampleRate = SampleRateEnum.SR_012000;
				} else {
					this.outputSampleRate = SampleRateEnum.SR_008000;
				}
			} else {
				if (tmpSourceSrHz >= SampleRateEnum.SR_048000.getSrHz()) {
					this.outputSampleRate = SampleRateEnum.SR_048000;
				} else {
					this.outputSampleRate = sourceParamsAudio.sampleRate;
				}
			}
		}

		if (tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_OPUS &&
				! (this.outputSampleRate == SampleRateEnum.SR_008000 ||
						this.outputSampleRate == SampleRateEnum.SR_012000 ||
						this.outputSampleRate == SampleRateEnum.SR_016000 ||
						this.outputSampleRate == SampleRateEnum.SR_024000 ||
						this.outputSampleRate == SampleRateEnum.SR_048000)) {
			throw new IllegalArgumentException(FNC_NAME + ": Opus only supports 8/12/16/24/48 kHz as sample rate");
		}

		//
		if (tcSettingsAud.cfgBitRateKbps == FfmpegAudioBitRate.UNKNOWN) {
			throw new IllegalArgumentException(FNC_NAME + ": Bitrate must be set");
		}

		//
		this.sourceParamsAudio.copyFrom(sourceParamsAudio);
		//
		this.sourceFfmpegCodec = sourceParamsAudio.ffmpegCodec;
		this.sourceTimeBase.copyFrom(sourceParamsAudio.timeBase);
		this.sourceExtradataHex.copyFrom(sourceParamsAudio.extradataHex);

		//
		if (isCallFromCtor) {
			return;
		}
		boolean needOnlyUpdate = (prevOpChannelCount == this.outputChannelCount && prevOpSampleRate == this.outputSampleRate);
		if (! needOnlyUpdate) {
			throw new IllegalArgumentException(FNC_NAME + ": Audio encoder parameters changed - need re-init");
		}

		//
		if (decoderCtx != null) {
			flushOnlyDecoder();
			avcodec.avcodec_free_context(decoderCtx);
			decoderCtx = null;
		}
		if (decodedFrame != null) { avutil.av_frame_free(decodedFrame); decodedFrame = null; }
		//
		openDecoderCtx(isSourceFromFile, inputAvFmtCtx, subStreamIx);
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
		openSwResamplerCtx();

		//
		openFifoCtx();

		//
		cdcParamsAudio.clear();
		cdcParamsAudio.ffmpegCodec = tcSettingsAud.cfgFfmpegCodec;
		cdcParamsAudio.sampleRate = outputSampleRate;
		cdcParamsAudio.channelCount = outputChannelCount;
		cdcParamsAudio.samplesPerFrame = (encoderCtx != null ? encoderCtx.frame_size() : -1);
		if (ffmpegReceiveTcAvInterface != null && encoderCtx != null) {
			internalGetEncoderExtradata_audio(
					encoderCtx,
					cdcParamsAudio,
					cdcParamsAudio.channelCount
				);
			ffmpegReceiveTcAvInterface.cbReceiveCdcParamsAudio(cdcParamsAudio);
		}

		//
		nextAudioPts = 0L;
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

		int r = avcodec.avcodec_send_packet(decoderCtx, inPkt);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);

		recvAllFrames();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean convertEncodedPacketFormat(@NonNull AVPacket encodedPacket) throws FfmpegGenericException {
		if (encoderCtx == null ||
				tcSettingsAud.cfgFfmpegCodec != FfmpegCodec.A_AAC) {
			return false;
		}

		if (! haveCheckedOutputFrameForAacAdts) {
			isOutputFmtAacWithAdts = FfmpegHelperBsfAacNoAdts.hasAdtsHeader(encodedPacket);
			/*logDebug(getClass().getSimpleName() + ".convertEncodedPacketFormat()",
					"**************************** isOutputFmtAacWithAdts=" + isOutputFmtAacWithAdts);*/
			haveCheckedOutputFrameForAacAdts = true;
		}

		if (tcSettingsAud.cfgOutputModeAac == FfmpegPktConvModeAac.PASSTHROUGH ||
				(tcSettingsAud.cfgOutputModeAac == FfmpegPktConvModeAac.WITH_ADTS && isOutputFmtAacWithAdts) ||
				(tcSettingsAud.cfgOutputModeAac == FfmpegPktConvModeAac.NO_ADTS && ! isOutputFmtAacWithAdts)) {
			return false;
		}

		if (ffmpegReceiveTcAvInterface == null) {
			return false;
		}

		if (bsfAac == null) {
			boolean needAacAddAdts = (! isOutputFmtAacWithAdts && tcSettingsAud.cfgOutputModeAac == FfmpegPktConvModeAac.WITH_ADTS);
			boolean needAacRemoveAdts = (isOutputFmtAacWithAdts && tcSettingsAud.cfgOutputModeAac == FfmpegPktConvModeAac.NO_ADTS);
			if (needAacAddAdts) {
				bsfAac = FfmpegHelperBsfAacWithAdts.fromAsc(cdcParamsAudio.extradataHex);
			} else if (needAacRemoveAdts) {
				bsfAac = new FfmpegHelperBsfAacNoAdts();
			}
		}

		//
		FfmpegHelperPktConverter.convertFfToBasics(
				false,  // output packet buffer will be cleared
				false,
				encodedPacket,
				RationalNumber.of(encoderCtx.time_base().num(), encoderCtx.time_base().den()),
				cacheBsfAvPktBasics
			);

		//
		bsfAac.processPkt(encodedPacket, cacheBsfAvPktBasics.pktBe);

		ffmpegReceiveTcAvInterface.cbReceiveTranscodedAudioSamples(cacheBsfAvPktBasics);
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

		// flush decoder
		flushOnlyDecoder();

		// flush resampler (samples still buffered inside the SwrContext)
		flushOnlySwr();

		// flush encoder
		flushAudioPipelineToEncoder();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void prepareDecoderCtxFromFile(
				@NonNull AVCodec avCodecDecoder,
				@NonNull AVFormatContext inputAvFmtCtx,
				int subStreamIx
			) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".prepareDecoderCtxFromFile()";

		AVCodecParameters inputCodecPar = inputAvFmtCtx.streams(subStreamIx).codecpar();

		if (inputCodecPar.codec_id() != sourceFfmpegCodec.getFfmpegId()) {
			throw new IllegalStateException(FNC_NAME + ": Input codec_id=" + inputCodecPar.codec_id() +
					" does not match expected codec_id=" + sourceFfmpegCodec.getFfmpegId());
		}

		decoderCtx = avcodec.avcodec_alloc_context3(avCodecDecoder);
		if (decoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoder Context");
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
		decoderCtx.sample_rate(sourceParamsAudio.sampleRate.getSrHz());
		if (sourceFfmpegCodec.isPcmAudio()) {
			// Channel layout is required for raw PCM codecs (e.g. PCM_MULAW, PCM_ALAW, PCM_S16LE, ...)
			try (AVChannelLayout tmpChLayout = new AVChannelLayout()) {
				avutil.av_channel_layout_default(tmpChLayout, sourceParamsAudio.channelCount);
				int r = avutil.av_channel_layout_copy(decoderCtx.ch_layout(), tmpChLayout);
				FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(decoderCtx)", r);
			}
		}

		// Optional but often helpful if known
		try (AVRational tmpTb = new AVRational()) {
			tmpTb.num(sourceTimeBase.getNumerator());
			tmpTb.den(sourceTimeBase.getDenominator());
			decoderCtx.time_base(tmpTb);
		}

		//
		internalSetDecoderExtradata_audio(
				decoderCtx,
				sourceFfmpegCodec,
				sourceParamsAudio.channelCount,
				sourceExtradataHex
			);
	}

	private void openDecoderCtx(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer subStreamIx
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openDecoderCtx()";

		AVCodec avCodecDecoder = avcodec.avcodec_find_decoder(sourceFfmpegCodec.getFfmpegId());
		if (avCodecDecoder == null) {
			throw new FfmpegDecoderNotFoundException(FNC_NAME + ": Input decoder not found for codec_id=" +
					sourceFfmpegCodec.getFfmpegId());
		}

		if (isFromFile) {
			if (inputAvFmtCtx == null || subStreamIx == null) {
				throw new IllegalArgumentException(FNC_NAME + ": inputAvFmtCtx and subStreamIx must be non-null");
			}
			prepareDecoderCtxFromFile(avCodecDecoder, inputAvFmtCtx, subStreamIx);
		} else {
			prepareDecoderCtxFromStream();
		}
		if (decoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": no Decoder Context allocated");
		}

		decoderCtx.flags(decoderCtx.flags() | avcodec.AV_CODEC_FLAG_LOW_DELAY);

		int r = avcodec.avcodec_open2(decoderCtx, avCodecDecoder, (AVDictionary)null);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_open2()", r);

		//
		decodedFrame = avutil.av_frame_alloc();
		if (decodedFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Decoded Frame");
		}
	}

	private void applyEncoderAudioBaseParams(int sampleFmt, long bitRateBps) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".applyEncoderAudioBaseParams()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		try (AVRational tmpTb = new AVRational()) {
			tmpTb.num(1);
			tmpTb.den(outputSampleRate.getSrHz());
			encoderCtx.time_base(tmpTb);
		}
		encoderCtx.sample_rate(outputSampleRate.getSrHz());

		try (AVChannelLayout tmpChLayout = new AVChannelLayout()) {
			avutil.av_channel_layout_default(tmpChLayout, outputChannelCount);

			int r = avutil.av_channel_layout_copy(encoderCtx.ch_layout(), tmpChLayout);
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);
		}

		encoderCtx.sample_fmt(sampleFmt);
		encoderCtx.bit_rate(bitRateBps);
	}

	private int openEncoderWithSampleFmt(@NonNull AVCodec avCodecEncoder, long bitRateBps) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openEncoderWithSampleFmt()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		List<Integer> tmpSampleFmtsToTest = new ArrayList<>();
		switch (tcSettingsAud.cfgFfmpegCodec) {
			case A_AAC, A_AC3, A_EAC3, A_MP3, A_VORBIS ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLTP);
			case A_ALAC ->  // ALAC supports 16 and 32(24) bits
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S32P);
			case A_FLAC, A_PCM_S24LE, A_PCM_S24BE, A_PCM_S32LE, A_PCM_S32BE ->  // FLAC supports 16 and 32(24) bits
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S32);
			case A_OPUS, A_PCM_F32LE, A_PCM_F32BE ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLT);
			case A_MP2, A_PCM_ALAW, A_PCM_MULAW, A_PCM_S16LE, A_PCM_S16BE ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S16);
			case A_PCM_U8 ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_U8);
		}
		tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_NONE);
		tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLT);
		tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLTP);
		tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S16);

		int r = -1;
		for (int tmpSampleFmt : tmpSampleFmtsToTest) {
			applyEncoderAudioBaseParams(tmpSampleFmt, bitRateBps);
			try (AVDictionary opts = new AVDictionary()) {
				if (tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_VORBIS) {
					// enable experimental codecs
					avutil.av_dict_set(opts, "strict", "-2", 0);
				}
				r = avcodec.avcodec_open2(encoderCtx, avCodecEncoder, opts);
			}
			if (r >= 0) {
				String tmpSampleFmtStr = (tmpSampleFmt == avutil.AV_SAMPLE_FMT_NONE ? "DEFAULT" : "");
				if (encoderCtx.sample_fmt() != avutil.AV_SAMPLE_FMT_NONE) {
					try (BytePointer tmpBp = avutil.av_get_sample_fmt_name(encoderCtx.sample_fmt())) {
						tmpSampleFmtStr = (tmpSampleFmt == avutil.AV_SAMPLE_FMT_NONE ? tmpSampleFmtStr + "(" : "") +
								tmpBp.getString() +
								(tmpSampleFmt == avutil.AV_SAMPLE_FMT_NONE ? tmpSampleFmtStr + ")" : "");
					}
				}
				logDebug(FNC_NAME, "using sample_fmt=" + tmpSampleFmtStr);
				return r;
			}
		}
		return r;
	}

	private void openEncoderCtx() throws FfmpegGenericException, FfmpegEncoderNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".openEncoderCtx()";

		AVCodec avCodecEncoder = avcodec.avcodec_find_encoder(tcSettingsAud.cfgFfmpegCodec.getFfmpegId());
		if (avCodecEncoder == null) {
			throw new FfmpegEncoderNotFoundException(FNC_NAME + ": Encoder not found (id=" + tcSettingsAud.cfgFfmpegCodec + ")");
		}

		encoderCtx = avcodec.avcodec_alloc_context3(avCodecEncoder);
		if (encoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoder Context");
		}

		// make sure it is divisible by 8
		final long tmpBrBps = (((tcSettingsAud.cfgBitRateKbps.getBrInt() * 1000L) / 8L) * 8L);

		//
		logDebug(FNC_NAME, "Audio Encoder Codec     : " + tcSettingsAud.cfgFfmpegCodec);
		logDebug(FNC_NAME, "Audio Encoder TimeBase  : " + sourceTimeBase.toString(5));
		logDebug(FNC_NAME, "Audio Encoder SampleRate: " + outputSampleRate);
		logDebug(FNC_NAME, "Audio Encoder Channels  : " + outputChannelCount);
		logDebug(FNC_NAME, "Audio Encoder BitRate   : " + (tmpBrBps / 1000L) + " kb/s");

		/*
		 * Important for AAC/Opus when muxers expect codec config in extradata.
		 * Must be set before avcodec_open2().
		 */
		if (tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_AAC ||  // @CODEC
				tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_ALAC ||
				tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_FLAC ||
				(tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_OPUS && outputChannelCount > 2) ||
				tcSettingsAud.cfgFfmpegCodec == FfmpegCodec.A_VORBIS) {
			encoderCtx.flags(encoderCtx.flags() | avcodec.AV_CODEC_FLAG_GLOBAL_HEADER);
			//logDebug(FNC_NAME, "Enabled AV_CODEC_FLAG_GLOBAL_HEADER for " + tcSettingsAud.ffmpegCodec);
		}

		//
		int r = openEncoderWithSampleFmt(avCodecEncoder, tmpBrBps);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "openEncoderWithSampleFmtFallback()", r);

		//
		encodedPacket = avcodec.av_packet_alloc();
		if (encodedPacket == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoded Packet");
		}
	}

	private void openSwResamplerCtx() {
		final String FNC_NAME = getClass().getSimpleName() + ".openSwResamplerCtx()";

		if (decoderCtx == null || encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx or encoderCtx is null");
		}

		int chLayoutCmp = avutil.av_channel_layout_compare(decoderCtx.ch_layout(), encoderCtx.ch_layout());
		needsAudioConversion = (
				decoderCtx.sample_fmt() != encoderCtx.sample_fmt()
						|| decoderCtx.sample_rate() != encoderCtx.sample_rate()
						|| chLayoutCmp != 0
			);

		if (! needsAudioConversion) {
			return;
		}

		/*
		 * SWR will be configured lazily from decoded AVFrame input,
		 * because actual frame format/layout/rate can differ at runtime.
		 */
		swrCtx = null;
		swrInSampleFmt = avutil.AV_SAMPLE_FMT_NONE;
		swrInSampleRate = 0;
		if (isSwrInChLayoutSet) {
			avutil.av_channel_layout_uninit(swrInChLayoutObj);
			isSwrInChLayoutSet = false;
		}

		convertedFrame = avutil.av_frame_alloc();
		if (convertedFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Converted Audio Frame");
		}
	}

	private void openFifoCtx() {
		final String FNC_NAME = getClass().getSimpleName() + ".openFifoCtx()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		int channels = encoderCtx.ch_layout().nb_channels();
		audioFifo = avutil.av_audio_fifo_alloc(encoderCtx.sample_fmt(), channels, 1);
		if (audioFifo == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Audio FIFO");
		}

		//
		fifoReadFrame = avutil.av_frame_alloc();
		if (fifoReadFrame == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate FIFO Read Frame");
		}
	}

	private static void internalSetDecoderExtradata_audio(
				@NonNull AVCodecContext decoderCtx,
				@NonNull FfmpegCodec inputFfmpegCodec,
				int audioChannelCount,
				@NonNull ExtradataContainerHex extradataHex
			) throws FfmpegGenericException {
		internalSetDecoderExtradata(
				decoderCtx,
				inputFfmpegCodec,
				false,
				audioChannelCount,
				extradataHex
			);
	}

	private static void internalGetEncoderExtradata_audio(
				@NonNull AVCodecContext encoderCtx,
				@NonNull FfmpegCdcParamsBase cdcParams,
				int audioChannelCount
			) throws FfmpegGenericException {
		internalGetEncoderExtradata(
				encoderCtx,
				false,
				FfmpegPktConvModeH26x.PASSTHROUGH,
				cdcParams,
				audioChannelCount
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void flushOnlyDecoder() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".flushOnlyDecoder()";

		// flush decoder: send null packet, pull remaining decoded frames
		//noinspection RedundantCast
		int r = avcodec.avcodec_send_packet(decoderCtx, (AVPacket)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);
		}

		// receive all remaining decoded frames
		recvAllFrames();
	}

	private void flushOnlySwr() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".flushOnlySwr()";

		if (! needsAudioConversion || swrCtx == null || convertedFrame == null || encoderCtx == null) {
			return;
		}

		int remaining = swresample.swr_get_out_samples(swrCtx, 0);
		if (remaining <= 0) {
			return;
		}

		avutil.av_frame_unref(convertedFrame);
		convertedFrame.format(encoderCtx.sample_fmt());
		convertedFrame.sample_rate(encoderCtx.sample_rate());
		int r = avutil.av_channel_layout_copy(convertedFrame.ch_layout(), encoderCtx.ch_layout());
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);
		convertedFrame.nb_samples(remaining);
		r = avutil.av_frame_get_buffer(convertedFrame, 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);

		// in=null drains the resampler's internal FIFO/delay
		//noinspection RedundantCast
		r = swresample.swr_convert_frame(swrCtx, convertedFrame, (AVFrame)null);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "swr_convert_frame(flush)", r);

		if (convertedFrame.nb_samples() > 0) {
			pushFrameToFifo(convertedFrame);
		}
		avutil.av_frame_unref(convertedFrame);
	}

	private void pushFrameToFifo(@NonNull AVFrame src) {
		final String FNC_NAME = getClass().getSimpleName() + ".pushFrameToFifo()";

		int srcSamples = src.nb_samples();
		/*int oldSize = avutil.av_audio_fifo_size(audioFifo);
		int r = avutil.av_audio_fifo_realloc(audioFifo, oldSize + srcSamples);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_audio_fifo_realloc()", r);*/

		int writeRes = avutil.av_audio_fifo_write(audioFifo, src.extended_data(), srcSamples);
		if (writeRes < srcSamples) {
			throw new RuntimeException(FNC_NAME + ": av_audio_fifo_write failed/short write: wrote=" +
					writeRes + ", expected=" + srcSamples);
		}
	}

	private void encodeOneFrameFromFifo(int nbSamples) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".encodeOneFrameFromFifo()";

		if (fifoReadFrame == null || encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": fifoReadFrame or encoderCtx is null");
		}

		avutil.av_frame_unref(fifoReadFrame);

		fifoReadFrame.nb_samples(nbSamples);
		fifoReadFrame.format(encoderCtx.sample_fmt());
		fifoReadFrame.sample_rate(encoderCtx.sample_rate());
		int r = avutil.av_channel_layout_copy(fifoReadFrame.ch_layout(), encoderCtx.ch_layout());
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);

		r = avutil.av_frame_get_buffer(fifoReadFrame, 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);

		int readRes = avutil.av_audio_fifo_read(audioFifo, fifoReadFrame.extended_data(), nbSamples);
		if (readRes < nbSamples) {
			throw new RuntimeException(FNC_NAME + ": av_audio_fifo_read failed/short read: read=" +
					readRes + ", expected=" + nbSamples);
		}

		fifoReadFrame.pts(nextAudioPts);
		nextAudioPts += nbSamples;

		r = avcodec.avcodec_send_frame(encoderCtx, fifoReadFrame);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);

		avutil.av_frame_unref(fifoReadFrame);

		drainEncoderPackets();
	}

	private void encodeAvailableFullFrames() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".encodeAvailableFullFrames()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}
		int frameSize = encoderCtx.frame_size();
		if (frameSize <= 0) {
			frameSize = 1024;  // AAC default safety fallback
		}

		while (avutil.av_audio_fifo_size(audioFifo) >= frameSize) {
			encodeOneFrameFromFifo(frameSize);
		}
	}

	private void flushAudioPipelineToEncoder() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".flushAudioPipelineToEncoder()";

		encodeAvailableFullFrames();

		int remaining = avutil.av_audio_fifo_size(audioFifo);
		if (remaining > 0) {
			// last frame may be short
			encodeOneFrameFromFifo(remaining);
		}

		//noinspection RedundantCast
		int r = avcodec.avcodec_send_frame(encoderCtx, (AVFrame)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);
		}
		drainEncoderPackets();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull AVChannelLayout getNormalizedInputLayout(@NonNull AVFrame inputFrame) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNormalizedInputLayout()";

		// Reuse object; reset old content first
		if (isTmpNormalizedInLayoutSet) {
			avutil.av_channel_layout_uninit(tmpNormalizedInLayoutObj);
			isTmpNormalizedInLayoutSet = false;
		}

		int chCount = inputFrame.ch_layout().nb_channels();
		if (chCount <= 0) {
			chCount = (decoderCtx != null ? decoderCtx.ch_layout().nb_channels() : 0);
		}
		if (chCount <= 0) {
			chCount = 2;  // safe fallback
		}

		// If input frame has unspecified/invalid order, synthesize a default layout from channel count
		// AV_CHANNEL_ORDER_UNSPEC = 0
		if (inputFrame.ch_layout().order() == 0 || inputFrame.ch_layout().nb_channels() <= 0) {
			avutil.av_channel_layout_default(tmpNormalizedInLayoutObj, chCount);
			isTmpNormalizedInLayoutSet = true;
			return tmpNormalizedInLayoutObj;
		}

		int r = avutil.av_channel_layout_copy(tmpNormalizedInLayoutObj, inputFrame.ch_layout());
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(inputFrame.ch_layout)", r);
		isTmpNormalizedInLayoutSet = true;
		return tmpNormalizedInLayoutObj;
	}

	private void normalizeInputFrameLayoutInPlace(@NonNull AVFrame inputFrame) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".normalizeInputFrameLayoutInPlace()";

		AVChannelLayout normalized = getNormalizedInputLayout(inputFrame);

		int cmp = avutil.av_channel_layout_compare(inputFrame.ch_layout(), normalized);
		if (cmp == 0) {
			return;
		}

		avutil.av_channel_layout_uninit(inputFrame.ch_layout());
		int r = avutil.av_channel_layout_copy(inputFrame.ch_layout(), normalized);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(inputFrame.ch_layout <- normalized)", r);
	}

	private void ensureSwrForInputFrame(@NonNull AVFrame inputFrame) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".ensureSwrForInputFrame()";

		AVChannelLayout normalizedInLayout = getNormalizedInputLayout(inputFrame);

		boolean mustRecreate = (swrCtx == null);

		if (! mustRecreate) {
			if (inputFrame.format() != swrInSampleFmt || inputFrame.sample_rate() != swrInSampleRate) {
				mustRecreate = true;
			} else if (!isSwrInChLayoutSet) {
				mustRecreate = true;
			} else {
				int cmp = avutil.av_channel_layout_compare(normalizedInLayout, swrInChLayoutObj);
				if (cmp != 0) {
					mustRecreate = true;
				}
			}
		}

		if (! mustRecreate) {
			return;
		}

		if (swrCtx != null) {
			swresample.swr_free(swrCtx);
			swrCtx = null;
		}
		swrCtx = swresample.swr_alloc();
		if (swrCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate SWR Context");
		}

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		int r = avutil.av_opt_set_chlayout(swrCtx, "in_chlayout", normalizedInLayout, 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_chlayout(in_chlayout)", r);

		r = avutil.av_opt_set_int(swrCtx, "in_sample_rate", inputFrame.sample_rate(), 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_int(in_sample_rate)", r);

		r = avutil.av_opt_set_sample_fmt(swrCtx, "in_sample_fmt", inputFrame.format(), 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_sample_fmt(in_sample_fmt)", r);

		r = avutil.av_opt_set_chlayout(swrCtx, "out_chlayout", encoderCtx.ch_layout(), 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_chlayout(out_chlayout)", r);

		r = avutil.av_opt_set_int(swrCtx, "out_sample_rate", encoderCtx.sample_rate(), 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_int(out_sample_rate)", r);

		r = avutil.av_opt_set_sample_fmt(swrCtx, "out_sample_fmt", encoderCtx.sample_fmt(), 0);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_opt_set_sample_fmt(out_sample_fmt)", r);

		r = swresample.swr_init(swrCtx);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "swr_init()", r);

		swrInSampleFmt = inputFrame.format();
		swrInSampleRate = inputFrame.sample_rate();

		if (isSwrInChLayoutSet) {
			avutil.av_channel_layout_uninit(swrInChLayoutObj);
			isSwrInChLayoutSet = false;
		}
		r = avutil.av_channel_layout_copy(swrInChLayoutObj, normalizedInLayout);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(cache)", r);
		isSwrInChLayoutSet = true;
	}

	private void convertWithSwrRetry(@NonNull AVFrame inFrame, @NonNull AVFrame outFrame) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".convertWithSwrRetry()";

		// Make sure frame metadata matches what SWR will be configured with
		normalizeInputFrameLayoutInPlace(inFrame);

		// 1st attempt with current/rebuilt SWR for this input frame
		ensureSwrForInputFrame(inFrame);
		int r = swresample.swr_convert_frame(swrCtx, outFrame, inFrame);
		if (r >= 0) {
			return;
		}

		// Input changed dynamically in stream -> force SWR rebuild and retry once
		if (swrCtx != null) {
			swresample.swr_free(swrCtx);
			swrCtx = null;
		}
		swrInSampleFmt = avutil.AV_SAMPLE_FMT_NONE;
		swrInSampleRate = 0;
		if (isSwrInChLayoutSet) {
			avutil.av_channel_layout_uninit(swrInChLayoutObj);
			isSwrInChLayoutSet = false;
		}

		// normalize again before rebuilding SWR
		normalizeInputFrameLayoutInPlace(inFrame);
		ensureSwrForInputFrame(inFrame);

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		avutil.av_frame_unref(outFrame);
		outFrame.format(encoderCtx.sample_fmt());
		outFrame.sample_rate(encoderCtx.sample_rate());
		r = avutil.av_channel_layout_copy(outFrame.ch_layout(), encoderCtx.ch_layout());
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(retry)", r);
		/*
		 * NOTE: no nb_samples()/av_frame_get_buffer() here on purpose:
		 * swr_convert_frame() sizes the output via swr_get_out_samples() (incl. its internal FIFO)
		 * and allocates the buffer itself. Pre-sizing it with the *input* sample count caps the
		 * output and makes swr buffer the surplus forever when upsampling.
		 */

		r = swresample.swr_convert_frame(swrCtx, outFrame, inFrame);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "swr_convert_frame(retry)", r);
	}

	// -----------------------------------------------------------------------------------------------------------------

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
		if (needsAudioConversion && convertedFrame == null) {
			throw new IllegalStateException(FNC_NAME + ": convertedFrame is null");
		}

		while (true) {
			int r = avcodec.avcodec_receive_frame(decoderCtx, decodedFrame);
			if (r == avutil.AVERROR_EAGAIN() || r == avutil.AVERROR_EOF) {
				break;
			}
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avcodec_receive_frame()", r);

			AVFrame frameForFifo = decodedFrame;
			if (needsAudioConversion && convertedFrame != null) {
				avutil.av_frame_unref(convertedFrame);
				convertedFrame.format(encoderCtx.sample_fmt());
				convertedFrame.sample_rate(encoderCtx.sample_rate());
				r = avutil.av_channel_layout_copy(convertedFrame.ch_layout(), encoderCtx.ch_layout());
				FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);
				// buffer is intentionally NOT allocated here - see convertWithSwrRetry()

				convertWithSwrRetry(decodedFrame, convertedFrame);
				frameForFifo = convertedFrame;
			}

			if (frameForFifo != null) {
				pushFrameToFifo(frameForFifo);
				encodeAvailableFullFrames();
			}

			avutil.av_frame_unref(decodedFrame);
			if (needsAudioConversion && convertedFrame != null) {
				avutil.av_frame_unref(convertedFrame);
			}
		}
	}

}
