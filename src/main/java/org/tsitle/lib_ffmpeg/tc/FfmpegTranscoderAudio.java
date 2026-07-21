package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
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

/**
 * Transcoder for audio streams.
 */
public final class FfmpegTranscoderAudio extends FfmpegTranscoderBase implements AutoCloseable {

	private final @NonNull SampleRateEnum sourceAudioSampleRate;
	private final @NonNull FfmpegTcSettingsAudio tcSettingsAud;

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

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param ffmpegReceiveTcAvInterface 'Receive transcoded A/V packet' instance (can be null)
	 * @param sourceFfmpegCodec Source codec
	 * @param sourceAudioTimeBase Source time base
	 * @param sourceAudioSampleRate Source sample rate
	 * @param tcSettingsAud Transcoder settings for audio
	 */
	public FfmpegTranscoderAudio(
				@Nullable LogMsgInterface logMsgInterface,
				@Nullable FfmpegReceiveTcAvInterface ffmpegReceiveTcAvInterface,
				@NonNull FfmpegCodec sourceFfmpegCodec,
				@NonNull RationalNumber sourceAudioTimeBase,
				@NonNull SampleRateEnum sourceAudioSampleRate,
				@NonNull FfmpegTcSettingsAudio tcSettingsAud
			) {
		super(
				logMsgInterface,
				false,
				ffmpegReceiveTcAvInterface,
				sourceFfmpegCodec,
				sourceAudioTimeBase,
				tcSettingsAud
			);

		if (tcSettingsAud.ffmpegCodec != FfmpegCodec.UNKNOWN && sourceAudioSampleRate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException("sourceAudioSampleRate must be set");
		}
		if (tcSettingsAud.ffmpegCodec != FfmpegCodec.UNKNOWN && tcSettingsAud.sampleRate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException("tcSettingsAud.sampleRate must be set");
		}

		this.sourceAudioSampleRate = sourceAudioSampleRate;
		this.tcSettingsAud = tcSettingsAud;
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

	@Override
	protected void initTranscoder(
				boolean isFromFile,
				@Nullable AVFormatContext inputAvFmtCtx,
				@Nullable Integer streamIx
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		openDecoderCtx(isFromFile, inputAvFmtCtx, streamIx);

		//
		openEncoderCtx();

		//
		openSwResamplerCtx();

		//
		openFifoCtx();

		//
		if (ffmpegReceiveTcAvInterface != null && encoderCtx != null) {
			ffmpegReceiveTcAvInterface.cbSetEncoderCtxForAudioParams(encoderCtx);
		}

		//
		nextAudioPts = 0L;
		isTranscoderReady = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void internalTranscodePacket(@NonNull AVPacket inPkt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalTranscodePacket()";

		if (! isTranscoderReady) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder not initialized. Call initTranscoder() first.");
		}
		if (decoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": decoderCtx is null");
		}

		int r = avcodec.avcodec_send_packet(decoderCtx, inPkt);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);

		recvAllFrames();
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

		// flush decoder: send null packet, pull remaining decoded frames
		int r = avcodec.avcodec_send_packet(decoderCtx, (AVPacket)null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_packet()", r);
		}

		// receive all remaining decoded frames
		recvAllFrames();

		// flush encoder
		flushAudioPipelineToEncoder();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
		decoderCtx.sample_rate(sourceAudioSampleRate.getSrHz());
		// @TODO channel layout + sample format

		// Optional but often helpful if known
		try (AVRational tmpTb = new AVRational()) {
			tmpTb.num(sourceTimeBase.getNumerator());
			tmpTb.den(sourceTimeBase.getDenominator());
			decoderCtx.time_base(tmpTb);
		}

		// CRITICAL for many container codecs (H264/H265 in MP4, etc.):
		// set extradata + extradata_size if available
		/*if (sourceExtradata != null && sourceExtradata.length > 0) {
			int size = sourceExtradata.length;
			org.bytedeco.javacpp.BytePointer extra =
					new org.bytedeco.javacpp.BytePointer(avutil.av_mallocz(size + avcodec.AV_INPUT_BUFFER_PADDING_SIZE));
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
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openDecoderCtx()";

		AVCodec avCodecDecoder = avcodec.avcodec_find_decoder(sourceFfmpegCodec.getFfmpegId());
		if (avCodecDecoder == null) {
			throw new FfmpegDecoderNotFoundException(FNC_NAME + ": Input decoder not found for codec_id=" +
					sourceFfmpegCodec.getFfmpegId());
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

	private void applyEncoderAudioBaseParams(int sampleFmt) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".applyEncoderAudioBaseParams()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		try (AVRational tmpTb = new AVRational()) {
			tmpTb.num(1);
			tmpTb.den(tcSettingsAud.sampleRate.getSrHz());
			encoderCtx.time_base(tmpTb);
		}
		encoderCtx.sample_rate(tcSettingsAud.sampleRate.getSrHz());

		try (AVChannelLayout tmpChLayout = new AVChannelLayout()) {
			avutil.av_channel_layout_default(tmpChLayout, tcSettingsAud.channelCount);

			int r = avutil.av_channel_layout_copy(encoderCtx.ch_layout(), tmpChLayout);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);
		}

		encoderCtx.sample_fmt(sampleFmt);
		encoderCtx.bit_rate(tcSettingsAud.bitRate);
	}

	private int openEncoderWithSampleFmt(@NonNull AVCodec avCodecEncoder) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".openEncoderWithSampleFmt()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}

		List<Integer> tmpSampleFmtsToTest = new ArrayList<>();
		switch (tcSettingsAud.ffmpegCodec) {
			case A_AAC, A_AC3, A_EAC3, A_MP3, A_VORBIS ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLTP);
			case A_ALAC ->  // ALAC supports 16 and 32(24) bits
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S32P);
			case A_FLAC, A_PCM_S24LE, A_PCM_S24BE, A_PCM_S32LE, A_PCM_S32BE ->  // FLAC supports 16 and 32(24) bits
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_S32);
			case A_OPUS, A_PCM_F32LE, A_PCM_F32BE ->
				tmpSampleFmtsToTest.add(avutil.AV_SAMPLE_FMT_FLT);
			case A_PCM_S16LE, A_PCM_S16BE ->
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
			applyEncoderAudioBaseParams(tmpSampleFmt);
			try (AVDictionary opts = new AVDictionary()) {
				if (tcSettingsAud.ffmpegCodec == FfmpegCodec.A_VORBIS) {
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

		AVCodec avCodecEncoder = avcodec.avcodec_find_encoder(tcSettingsAud.ffmpegCodec.getFfmpegId());
		if (avCodecEncoder == null) {
			throw new FfmpegEncoderNotFoundException(FNC_NAME + ": Encoder not found (id=" + tcSettingsAud.ffmpegCodec + ")");
		}

		encoderCtx = avcodec.avcodec_alloc_context3(avCodecEncoder);
		if (encoderCtx == null) {
			throw new RuntimeException(FNC_NAME + ": Cannot allocate Encoder Context");
		}

		//
		logDebug(FNC_NAME, "Audio Encoder Codec     : " + tcSettingsAud.ffmpegCodec);
		logDebug(FNC_NAME, "Audio Encoder TimeBase  : " + sourceTimeBase.toString(5));
		logDebug(FNC_NAME, "Audio Encoder SampleRate: " + tcSettingsAud.sampleRate);
		logDebug(FNC_NAME, "Audio Encoder Channels  : " + tcSettingsAud.channelCount);
		logDebug(FNC_NAME, "Audio Encoder BitRate   : " +
				(tcSettingsAud.bitRate > 0 ? tcSettingsAud.bitRate : "auto"));

		//
		int r = openEncoderWithSampleFmt(avCodecEncoder);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "openEncoderWithSampleFmtFallback()", r);

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

	// -----------------------------------------------------------------------------------------------------------------

	private void pushFrameToFifo(@NonNull AVFrame src) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".pushFrameToFifo()";

		int srcSamples = src.nb_samples();
		int oldSize = avutil.av_audio_fifo_size(audioFifo);
		int r = avutil.av_audio_fifo_realloc(audioFifo, oldSize + srcSamples);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_audio_fifo_realloc()", r);

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
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);

		r = avutil.av_frame_get_buffer(fifoReadFrame, 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);

		int readRes = avutil.av_audio_fifo_read(audioFifo, fifoReadFrame.extended_data(), nbSamples);
		if (readRes < nbSamples) {
			throw new RuntimeException(FNC_NAME + ": av_audio_fifo_read failed/short read: read=" +
					readRes + ", expected=" + nbSamples);
		}

		fifoReadFrame.pts(nextAudioPts);
		nextAudioPts += nbSamples;

		r = avcodec.avcodec_send_frame(encoderCtx, fifoReadFrame);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);

		drainEncoderPackets();
	}

	private void encodeAvailableFullFrames() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".encodeAvailableFullFrames()";

		if (encoderCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": encoderCtx is null");
		}
		int frameSize = encoderCtx.frame_size();
		if (frameSize <= 0) {
			frameSize = 1024; // AAC default safety fallback
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

		int r = avcodec.avcodec_send_frame(encoderCtx, (AVFrame) null);
		if (r < 0 && r != avutil.AVERROR_EOF) {
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_send_frame()", r);
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
			chCount = 2; // safe fallback
		}

		// If input frame has unspecified/invalid order, synthesize a default layout from channel count
		// AV_CHANNEL_ORDER_UNSPEC = 0
		if (inputFrame.ch_layout().order() == 0 || inputFrame.ch_layout().nb_channels() <= 0) {
			avutil.av_channel_layout_default(tmpNormalizedInLayoutObj, chCount);
			isTmpNormalizedInLayoutSet = true;
			return tmpNormalizedInLayoutObj;
		}

		int r = avutil.av_channel_layout_copy(tmpNormalizedInLayoutObj, inputFrame.ch_layout());
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(inputFrame.ch_layout)", r);
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
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(inputFrame.ch_layout <- normalized)", r);
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
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_chlayout(in_chlayout)", r);

		r = avutil.av_opt_set_int(swrCtx, "in_sample_rate", inputFrame.sample_rate(), 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_int(in_sample_rate)", r);

		r = avutil.av_opt_set_sample_fmt(swrCtx, "in_sample_fmt", inputFrame.format(), 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_sample_fmt(in_sample_fmt)", r);

		r = avutil.av_opt_set_chlayout(swrCtx, "out_chlayout", encoderCtx.ch_layout(), 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_chlayout(out_chlayout)", r);

		r = avutil.av_opt_set_int(swrCtx, "out_sample_rate", encoderCtx.sample_rate(), 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_int(out_sample_rate)", r);

		r = avutil.av_opt_set_sample_fmt(swrCtx, "out_sample_fmt", encoderCtx.sample_fmt(), 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_opt_set_sample_fmt(out_sample_fmt)", r);

		r = swresample.swr_init(swrCtx);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "swr_init()", r);

		swrInSampleFmt = inputFrame.format();
		swrInSampleRate = inputFrame.sample_rate();

		if (isSwrInChLayoutSet) {
			avutil.av_channel_layout_uninit(swrInChLayoutObj);
			isSwrInChLayoutSet = false;
		}
		r = avutil.av_channel_layout_copy(swrInChLayoutObj, normalizedInLayout);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(cache)", r);
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
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy(retry)", r);
		outFrame.nb_samples(inFrame.nb_samples());
		r = avutil.av_frame_get_buffer(outFrame, 0);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer(retry)", r);

		r = swresample.swr_convert_frame(swrCtx, outFrame, inFrame);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "swr_convert_frame(retry)", r);
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
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avcodec_receive_frame()", r);

			AVFrame frameForFifo = decodedFrame;
			if (needsAudioConversion && convertedFrame != null) {
				avutil.av_frame_unref(convertedFrame);
				convertedFrame.format(encoderCtx.sample_fmt());
				convertedFrame.sample_rate(encoderCtx.sample_rate());
				r = avutil.av_channel_layout_copy(convertedFrame.ch_layout(), encoderCtx.ch_layout());
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_channel_layout_copy()", r);

				convertedFrame.nb_samples(decodedFrame.nb_samples());
				r = avutil.av_frame_get_buffer(convertedFrame, 0);
				FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_frame_get_buffer()", r);

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
