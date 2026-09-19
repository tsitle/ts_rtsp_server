package org.tsitle.lib_ffmpeg.demux;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.*;
import org.tsitle.lib_ffmpeg.helpers.*;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataConvHexFmtHelper;
import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Demuxer for A/V streams.
 */
public final class FfmpegDemuxer implements AutoCloseable {

	public enum ReadResult {
		RR_EOF,
		RR_OK_VID,
		RR_OK_AUD
	}

	private static final long FPS_MEASURE_INTERVAL_MS = 10_000L;

	/** Samples per frame for AC-3 audio */
	private static final int AUDIO_SAMPLES_PER_FRAME_AC3 = 1536;

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull String inputPathOrUri;
	private final @NonNull FfmpegDmxSettingsInternal dmxSettings;
	private final @Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface;

	private final boolean isInputSourceAudioOnly;

	private @Nullable AVFormatContext inputAvFmtCtx;
	private final @NonNull FfmpegDmxSubStreamInfoVideo inputSsInfoVid = new FfmpegDmxSubStreamInfoVideo();
	private final @NonNull FfmpegDmxSubStreamInfoAudio inputSsInfoAud = new FfmpegDmxSubStreamInfoAudio();
	private final @NonNull FfmpegDmxStats stats = new FfmpegDmxStats();
	private boolean isInputOpen = false;
	private @Nullable AVPacket cacheAvPkt = null;
	private boolean haveReachedMaxSecs = false;

	private boolean haveCheckedFrameForH26xAnnexB = false;
	private @Nullable FfmpegHelperBsfH26xInterface bsfH26x = null;
	private boolean haveCheckedFrameForAacAdts = false;
	private @Nullable FfmpegHelperBsfAacInterface bsfAac = null;

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputPathOrUri Path to the input file or URI of the input stream
	 * @param dmxSettings Settings for demuxing
	 * @param recvDemuxerStatsInterface 'Receive Demuxer Stats' instance (can be null)
	 */
	private FfmpegDemuxer(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputPathOrUri,
				@NonNull FfmpegDmxSettingsInternal dmxSettings,
				@Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.inputPathOrUri = inputPathOrUri;
		this.dmxSettings = dmxSettings;
		this.recvDemuxerStatsInterface = recvDemuxerStatsInterface;

		if (inputPathOrUri.isBlank()) {
			throw new IllegalArgumentException(FfmpegDemuxer.class.getSimpleName() + ".ctor(): " +
					"inputFilePath is blank");
		}

		//
		this.isInputSourceAudioOnly = checkIfInputSourceIsAudioOnly();

		//
		this.inputAvFmtCtx = avformat.avformat_alloc_context();
		if (this.inputAvFmtCtx == null) {
			throw new IllegalStateException(FfmpegDemuxer.class.getSimpleName() + ".ctor(): " +
					"could not allocate inputAvFmtCtx");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Read the stream info from the input file.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputPathOrUri Path to the input file or URI of the input stream
	 * @param dmxSettings Settings for demuxing (only for reading the sub-stream infos)
	 * @param outSubStreamInfoVid Output for the video sub-stream info
	 * @param outSubStreamInfoAud Output for the audio sub-stream info
	 * @throws FfmpegGenericException If any FFmpeg error occurs
	 */
	@SuppressWarnings("unused")
	public static void readStreamInfos(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputPathOrUri,
				@NonNull FfmpegDmxSettingsRsi dmxSettings,
				@NonNull FfmpegDmxSubStreamInfoVideo outSubStreamInfoVid,
				@NonNull FfmpegDmxSubStreamInfoAudio outSubStreamInfoAud
			) throws FfmpegGenericException {
		outSubStreamInfoVid.clear();
		outSubStreamInfoAud.clear();

		try (FfmpegDemuxer ffmpegDemuxer = new FfmpegDemuxer(
					logMsgInterface,
					inputPathOrUri,
					FfmpegDmxSettingsInternal.of(dmxSettings),
					null
				)) {
			ffmpegDemuxer.internalReadStreamInfos();

			outSubStreamInfoVid.copyFrom(ffmpegDemuxer.inputSsInfoVid);
			outSubStreamInfoAud.copyFrom(ffmpegDemuxer.inputSsInfoAud);
		}
	}

	/**
	 * Create a Demuxer for demuxing only.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputPathOrUri Path to the input file or URI of the input stream
	 * @param dmxSettings Settings for demuxing
	 */
	@SuppressWarnings("unused")
	public static FfmpegDemuxer createForDemuxingOnly(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputPathOrUri,
				@NonNull FfmpegDmxSettingsDemux dmxSettings
			) {
		return new FfmpegDemuxer(
				logMsgInterface,
				inputPathOrUri,
				FfmpegDmxSettingsInternal.of(dmxSettings),
				null
			);
	}

	/**
	 * Create a Demuxer for transcoding.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputPathOrUri Path to the input file or URI of the input stream
	 * @param dmxSettings Settings for demuxing
	 * @param recvDemuxerStatsInterface 'Receive Demuxer Stats' instance (can be null)
	 */
	@SuppressWarnings("unused")
	public static FfmpegDemuxer createForTranscoding(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputPathOrUri,
				@NonNull FfmpegDmxSettingsTc dmxSettings,
				@Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface
			) {
		return new FfmpegDemuxer(
				logMsgInterface,
				inputPathOrUri,
				FfmpegDmxSettingsInternal.of(dmxSettings),
				recvDemuxerStatsInterface
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Read the next A/V packet.
	 * @param outputData Output data
	 * @return Read result
	 * @throws FfmpegGenericException If any FFmpeg error occurs
	 */
	@SuppressWarnings("unused")
	public synchronized @NonNull ReadResult readNextAvPacket(@NonNull FfmpegAvPktBasics outputData) throws FfmpegGenericException {
		outputData.clear();

		//
		if (inputSsInfoVid.subStreamIx < 0 && inputSsInfoAud.subStreamIx < 0) {
			internalReadStreamInfos();
		}

		//
		Optional<ReadResult> tmpOptResEn;
		while (true) {
			tmpOptResEn = internalReadNextAvPacket();
			if (tmpOptResEn.isPresent() && tmpOptResEn.get() == ReadResult.RR_EOF) {
				return ReadResult.RR_EOF;
			}
			if (tmpOptResEn.isEmpty()) {
				avcodec.av_packet_unref(cacheAvPkt);
				continue;
			}
			break;
		}

		if (cacheAvPkt == null) {
			return ReadResult.RR_EOF;
		}

		ReadResult resEn = tmpOptResEn.get();

		copyCachedAvPacketToOutput(resEn == ReadResult.RR_OK_VID, outputData);

		avcodec.av_packet_unref(cacheAvPkt);

		return resEn;
	}

	/**
	 * Seek to a position in the input timeline.
	 * @param targetTimestamp Target position in seconds (fractional is allowed)
	 * @throws FfmpegGenericException If any FFmpeg error occurs
	 */
	@SuppressWarnings("unused")
	public synchronized void seekToTimestamp(double targetTimestamp) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".seekToTimestamp()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		if (! Double.isFinite(targetTimestamp) || targetTimestamp < 0.0) {
			throw new IllegalArgumentException(FNC_NAME + ": seconds must be finite and >= 0.0");
		}
		if (inputSsInfoVid.subStreamIx < 0 && inputSsInfoAud.subStreamIx < 0) {
			throw new IllegalStateException(FNC_NAME + ": need to read the stream info first");
		}
		if (! isInputOpen) {
			throw new IllegalStateException(FNC_NAME + ": input file needs to be open");
		}

		long targetTs = Math.max(0L, Math.round(targetTimestamp * (double)avutil.AV_TIME_BASE));

		int r = avformat.avformat_seek_file(
				inputAvFmtCtx,
				-1,
				Long.MIN_VALUE,
				targetTs,
				Long.MAX_VALUE,
				avformat.AVSEEK_FLAG_BACKWARD
			);
		// fallback for formats that are picky with avformat_seek_file()
		if (r < 0) {
			// this does seem coarser, though
			r = avformat.av_seek_frame(inputAvFmtCtx, -1, targetTs, avformat.AVSEEK_FLAG_BACKWARD);
		}
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avformat_seek_file/frame", r);

		avformat.avformat_flush(inputAvFmtCtx);

		if (cacheAvPkt != null) {
			avcodec.av_packet_unref(cacheAvPkt);
		}

		// reset filter state to avoid stale buffered packets after seek
		if (bsfH26x != null) {
			bsfH26x.close();
			bsfH26x = null;
			haveCheckedFrameForH26xAnnexB = false;
		}
		if (bsfAac != null) {
			bsfAac = null;
			haveCheckedFrameForAacAdts = false;
		}

		haveReachedMaxSecs = false;
		stats.currentMaxPtsSecs = -1.0;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull FfmpegDmxStats getStats() {
		return stats.clone();
	}

	@SuppressWarnings("unused")
	public Optional<AVFormatContext> getFfAvFmtCtxPtr() {
		return Optional.ofNullable(inputAvFmtCtx);
	}

	@SuppressWarnings("unused")
	public Optional<Integer> getFfAvSubStreamIxVideo() {
		if (inputSsInfoVid.subStreamIx < 0) {
			return Optional.empty();
		}
		return Optional.of(inputSsInfoVid.subStreamIx);
	}

	@SuppressWarnings("unused")
	public Optional<Integer> getFfAvSubStreamIxAudio() {
		if (inputSsInfoAud.subStreamIx < 0) {
			return Optional.empty();
		}
		return Optional.of(inputSsInfoAud.subStreamIx);
	}

	@SuppressWarnings("unused")
	public Optional<FfmpegDmxSubStreamInfoVideo> getFfAvSubStreamInfoVideo() {
		if (inputSsInfoVid.subStreamIx < 0) {
			return Optional.empty();
		}
		FfmpegDmxSubStreamInfoVideo resObj = new FfmpegDmxSubStreamInfoVideo();
		resObj.copyFrom(inputSsInfoVid);
		return Optional.of(resObj);
	}

	@SuppressWarnings("unused")
	public Optional<FfmpegDmxSubStreamInfoAudio> getFfAvSubStreamInfoAudio() {
		if (inputSsInfoAud.subStreamIx < 0) {
			return Optional.empty();
		}
		FfmpegDmxSubStreamInfoAudio resObj = new FfmpegDmxSubStreamInfoAudio();
		resObj.copyFrom(inputSsInfoAud);
		return Optional.of(resObj);
	}

	@SuppressWarnings("unused")
	public Optional<Double> getDurationSecs() {
		double resD = (inputSsInfoVid.subStreamIx >= 0 ? inputSsInfoVid.durationSecs : -1.0);
		resD = (resD < 0.001 && inputSsInfoAud.subStreamIx >= 0 ? inputSsInfoAud.durationSecs : resD);
		if (resD < 0.001) {
			return Optional.empty();
		}
		return Optional.of(resD);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		if (inputAvFmtCtx != null) { avformat.avformat_free_context(inputAvFmtCtx); inputAvFmtCtx = null; }
		if (cacheAvPkt != null) { avcodec.av_packet_free(cacheAvPkt); cacheAvPkt = null; }
		if (bsfH26x != null) { bsfH26x.close(); bsfH26x = null; }
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkIfInputSourceIsAudioOnly() {
		/*
		 * Audio files can contain metadata like cover art.
		 * FFmpeg will read an embedded JPEG file as a MJPEG video stream, for instance.
		 */
		String tmpInputLc = inputPathOrUri.toLowerCase();
		return (  // @CODEC
				tmpInputLc.endsWith(".aac") ||
				tmpInputLc.endsWith(".ac3") ||
				tmpInputLc.endsWith(".eac3") ||
				tmpInputLc.endsWith(".flac") ||
				tmpInputLc.endsWith(".m4a") ||
				tmpInputLc.endsWith(".mp2") ||
				tmpInputLc.endsWith(".mp3") ||
				tmpInputLc.endsWith(".mpa") ||
				tmpInputLc.endsWith(".ogg") ||
				tmpInputLc.endsWith(".opus") ||
				tmpInputLc.endsWith(".wav")
			);
	}

	private void internalReadStreamInfos() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadStreamInfos()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		logDebug(FNC_NAME, "Read input stream");

		int r = avformat.avformat_open_input(inputAvFmtCtx, inputPathOrUri, null, null);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);

		r = avformat.avformat_find_stream_info(inputAvFmtCtx, (AVDictionary)null);
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avformat_find_stream_info", r);

		findSubStreamIndices();

		if (inputSsInfoVid.subStreamIx == -1 && inputSsInfoAud.subStreamIx == -1) {
			avformat.avformat_close_input(inputAvFmtCtx);
			throw new FfmpegGenericException(FNC_NAME + ": No supported video or audio stream found");
		}

		// ------------------------------------------------

		long durationTs = inputAvFmtCtx.duration();  // in AV_TIME_BASE units, can be AV_NOPTS_VALUE
		double durationSecs = (durationTs != avutil.AV_NOPTS_VALUE ? durationTs / (double)avutil.AV_TIME_BASE : -1.0);

		final Map<@NonNull String, @NonNull String> metaMap = new HashMap<>();
		readFileMetadata(metaMap);

		// ------------------------------------------------

		if (inputSsInfoVid.subStreamIx != -1) {
			getSubStreamInfoVideo(inputAvFmtCtx, durationSecs, dmxSettings.cfgOutputModeH26x, metaMap, inputSsInfoVid);
		}
		if (inputSsInfoAud.subStreamIx != -1) {
			getSubStreamInfoAudio(inputAvFmtCtx, durationSecs, metaMap, inputSsInfoAud);
		}
		avformat.avformat_close_input(inputAvFmtCtx);
	}

	private void findSubStreamIndices() {
		final String FNC_NAME = getClass().getSimpleName() + ".findSubStreamIndices()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		inputSsInfoVid.subStreamIx = -1;
		inputSsInfoAud.subStreamIx = -1;

		int curStreamNumberVid = 0;
		int curStreamNumberAud = 0;

		for (int i = 0; i < inputAvFmtCtx.nb_streams(); i++) {
			AVStream st = inputAvFmtCtx.streams(i);
			AVCodecParameters par = st.codecpar();

			String tmpCodecName;
			try (BytePointer bp = avcodec.avcodec_get_name(par.codec_id())) {
				tmpCodecName = bp.getString();
			}
			FfmpegCodec tmpFfmpegCodec = FfmpegCodec.of(par.codec_id());
			/*logDebug(FNC_NAME, "Codec ID: " + par.codec_id());
			logDebug(FNC_NAME, "Codec type: " + par.codec_type());*/
			switch (par.codec_type()) {
				case avutil.AVMEDIA_TYPE_VIDEO:
					if (isInputSourceAudioOnly) {
						continue;
					}
					++curStreamNumberVid;
					if (! tmpFfmpegCodec.isVideo()) {
						logDebug(FNC_NAME,
								String.format("(ignoring video track #v:%d with unsupported codec [%s])",
										curStreamNumberVid, tmpCodecName)
							);
						continue;
					}
					if (inputSsInfoVid.subStreamIx < 0 &&
							(dmxSettings.cfgSelectStreamNumberVideo < 1 ||
									curStreamNumberVid == dmxSettings.cfgSelectStreamNumberVideo) &&
							(! dmxSettings.cfgAllowOnlySpecificCodecsVideo || dmxSettings.cfgAllowedCodecsVideo.contains(tmpFfmpegCodec))) {
						logDebug(FNC_NAME, String.format("found video track #v:%d [%s]", curStreamNumberVid, tmpCodecName));
						inputSsInfoVid.subStreamIx = i;
						inputSsInfoVid.streamNumberVideo = curStreamNumberVid;
					} else {
						logDebug(FNC_NAME,
								String.format("(ignoring other video track #v:%d [%s])",
										curStreamNumberVid, tmpCodecName)
							);
					}
					break;
				case avutil.AVMEDIA_TYPE_AUDIO:
					++curStreamNumberAud;
					if (! tmpFfmpegCodec.isAudio()) {
						logDebug(FNC_NAME,
								String.format("(ignoring audio track #a:%d with unsupported codec [%s])",
										curStreamNumberAud, tmpCodecName)
							);
						continue;
					}
					if (inputSsInfoAud.subStreamIx < 0 &&
							(dmxSettings.cfgSelectStreamNumberAudio < 1 ||
									curStreamNumberAud == dmxSettings.cfgSelectStreamNumberAudio) &&
							(! dmxSettings.cfgAllowOnlySpecificCodecsAudio || dmxSettings.cfgAllowedCodecsAudio.contains(tmpFfmpegCodec))) {
						logDebug(FNC_NAME, String.format("found audio track #a:%d [%s]", curStreamNumberAud, tmpCodecName));
						inputSsInfoAud.subStreamIx = i;
						inputSsInfoAud.streamNumberAudio = curStreamNumberAud;
					} else {
						logDebug(FNC_NAME,
								String.format("(ignoring other audio track #a:%d [%s])",
										curStreamNumberAud, tmpCodecName)
							);
					}
					break;
				case avutil.AVMEDIA_TYPE_SUBTITLE:
					logDebug(FNC_NAME, "(ignoring subtitle track [" + tmpCodecName + "])");
					break;
			}
		}
	}

	private void readFileMetadata(@NonNull Map<@NonNull String, @NonNull String> metaMap) {
		if (inputAvFmtCtx == null) {
			return;
		}
		AVDictionary metadata = inputAvFmtCtx.metadata();
		if (metadata == null) {
			return;
		}
		for (String tag : FfmpegDmxSubStreamInfoBase.META_KEYS) {
			AVDictionaryEntry entry = avutil.av_dict_get(metadata, tag, null, 0);
			if (entry == null) {
				continue;
			}
			try (BytePointer valBp = entry.value()) {
				if (valBp != null && valBp.getString() != null) {
					String tmpTagValue = sanitizeTagString(valBp.getString());
					metaMap.put(tag, tmpTagValue);
				}
			}
		}
	}

	private static @NonNull String sanitizeTagString(@NonNull String tagValue) {
		return tagValue
				.replace("ö", "oe")
				.replace("ø", "oe")
				.replace("ä", "ae")
				.replace("å", "ae")
				.replace("ü", "ue")
				.replace("ß", "ss")
				.replace("Ö", "Oe")
				.replace("Ø", "Oe")
				.replace("Ä", "Ae")
				.replace("Å", "Ae")
				.replace("Ü", "Ue")
				.replace("ñ", "nj")
				.replace("Ñ", "Nj")
				.replace("`", "'")
				.replace("á", "a")
				.replace("Á", "A")
				.replace("à", "a")
				.replace("À", "A")
				.replace("é", "e")
				.replace("É", "E")
				.replace("è", "e")
				.replace("È", "E")
				.replace("ó", "o")
				.replace("Ó", "O")
				.replace("ò", "o")
				.replace("Ò", "O")
				.replace("í", "i")
				.replace("Í", "I")
				.replace("ì", "i")
				.replace("Ì", "I")
				.replaceAll("[^\\x20-\\x7E]", "");
	}

	private static @NonNull RationalNumber getSubStreamTimeBase(@NonNull AVFormatContext inputAvFmtCtx, int subStreamIx) {
		int tmpResNum = 0;
		int tmpResDen = 1;
		if (subStreamIx >= 0) {
			AVStream st = inputAvFmtCtx.streams(subStreamIx);
			tmpResNum = st.time_base().num();
			tmpResDen = st.time_base().den();
		}
		return RationalNumber.of(tmpResNum, tmpResDen);
	}

	private static void getSubStreamInfoVideo(
				@NonNull AVFormatContext inputAvFmtCtx,
				double durationSecs,
				@NonNull FfmpegPktConvModeH26x pktConvModeH26x,
				@NonNull Map<@NonNull String, @NonNull String> metaMap,
				@NonNull FfmpegDmxSubStreamInfoVideo ioSsInfoVideo
			) throws FfmpegGenericException {
		final String FNC_NAME = FfmpegDemuxer.class.getSimpleName() + ".getSubStreamInfoVideo()";

		ioSsInfoVideo.timeBasePts = getSubStreamTimeBase(inputAvFmtCtx, ioSsInfoVideo.subStreamIx);

		//
		if (ioSsInfoVideo.subStreamIx < 0) {
			return;
		}
		AVStream st = inputAvFmtCtx.streams(ioSsInfoVideo.subStreamIx);
		ioSsInfoVideo.ffmpegCodec = FfmpegCodec.of(st.codecpar().codec_id());
		ioSsInfoVideo.imgDims = ImageDimensions.of(st.codecpar().width(), st.codecpar().height());
		ioSsInfoVideo.durationSecs = durationSecs;
		ioSsInfoVideo.bitRate = st.codecpar().bit_rate();
		ioSsInfoVideo.pixelFmt = FfmpegHelperPixelFmtConv.convertPixelFmtFromInt(FNC_NAME, st.codecpar().format());
		copySubStreamInfoExtradata(st, pktConvModeH26x, ioSsInfoVideo);
		ioSsInfoVideo.metaMap.clear();
		ioSsInfoVideo.metaMap.putAll(metaMap);

		//
		int tmpFpsNum = 0;
		int tmpFpsDen = 1;

		AVRational tmpAvFps = avformat.av_guess_frame_rate(inputAvFmtCtx, st, null);
		if (! RationalNumber.of(tmpAvFps.num(), tmpAvFps.den()).isValid()) {
			tmpAvFps = st.avg_frame_rate();
		}
		if (! RationalNumber.of(tmpAvFps.num(), tmpAvFps.den()).isValid()) {
			tmpAvFps = st.r_frame_rate();
		}

		if (RationalNumber.of(tmpAvFps.num(), tmpAvFps.den()).isValid()) {
			tmpFpsNum = tmpAvFps.num();
			tmpFpsDen = tmpAvFps.den();
		}
		ioSsInfoVideo.fps = RationalNumber.of(tmpFpsNum, tmpFpsDen);
	}

	private static void getSubStreamInfoAudio(
				@NonNull AVFormatContext inputAvFmtCtx,
				double durationSecs,
				@NonNull Map<@NonNull String, @NonNull String> metaMap,
				@NonNull FfmpegDmxSubStreamInfoAudio ioSsInfoAudio
			) {
		ioSsInfoAudio.timeBasePts = getSubStreamTimeBase(inputAvFmtCtx, ioSsInfoAudio.subStreamIx);

		//
		if (ioSsInfoAudio.subStreamIx < 0) {
			return;
		}
		AVStream st = inputAvFmtCtx.streams(ioSsInfoAudio.subStreamIx);
		ioSsInfoAudio.ffmpegCodec = FfmpegCodec.of(st.codecpar().codec_id());
		ioSsInfoAudio.sampleRate = SampleRateEnum.of(st.codecpar().sample_rate());
		ioSsInfoAudio.channelCount = st.codecpar().ch_layout().nb_channels();
		ioSsInfoAudio.durationSecs = durationSecs;
		ioSsInfoAudio.bitRate = st.codecpar().bit_rate();
		ioSsInfoAudio.bitsPerCodedSample = st.codecpar().bits_per_coded_sample();
		if (ioSsInfoAudio.ffmpegCodec == FfmpegCodec.A_AC3) {
			ioSsInfoAudio.samplesPerFrame = AUDIO_SAMPLES_PER_FRAME_AC3;
		} else {
			ioSsInfoAudio.samplesPerFrame = st.codecpar().frame_size();
		}
		if (ioSsInfoAudio.samplesPerFrame < 1) {
			ioSsInfoAudio.samplesPerFrame = -1;
		}
		copySubStreamInfoExtradata(st, FfmpegPktConvModeH26x.PASSTHROUGH, ioSsInfoAudio);
		ioSsInfoAudio.metaMap.clear();
		ioSsInfoAudio.metaMap.putAll(metaMap);
	}

	private static void copySubStreamInfoExtradata(
				@NonNull AVStream st,
				@NonNull FfmpegPktConvModeH26x pktConvModeH26x,
				@NonNull FfmpegDmxSubStreamInfoBase ioSsInfo
			) {
		if (st.codecpar().extradata() == null || st.codecpar().extradata_size() < 1) {
			//System.out.println("Demuxer: no extradata, codec=" + ioSsInfo.ffmpegCodec);
			return;
		}
		/*
		 * Extract st.codecpar().extradata(), e.g. SPS/PPS for H264 or VPS/SPS/PPS for H265 or AudioSpecificConfig for AAC
		 */
		String tmpEdStr;
		try (BytePointer tmpBp = st.codecpar().extradata()) {
			int extradataSize = st.codecpar().extradata_size();
			byte[] ascBytes = new byte[extradataSize];
			tmpBp.position(0).get(ascBytes, 0, extradataSize);
			tmpEdStr = HexFormat.of().withUpperCase().formatHex(ascBytes);
		}
		//System.out.println("Demuxer: extradata='" + tmpEdStr + "', codec=" + ioSsInfo.ffmpegCodec);
		//
		boolean tmpOutputH26xAsAnnexB = (pktConvModeH26x == FfmpegPktConvModeH26x.ANNEXB);
		ExtradataContainerHex tmpEch = switch (ioSsInfo.ffmpegCodec) {  // @CODEC
				case A_AAC -> ExtradataContainerHex.ofAac(tmpEdStr);
				case A_ALAC -> ExtradataContainerHex.ofAlac(tmpEdStr);
				case A_FLAC -> ExtradataContainerHex.ofFlac(tmpEdStr);
				case A_OPUS -> ExtradataContainerHex.ofOpus(tmpEdStr);
				case A_VORBIS -> ExtradataContainerHex.ofVorbis(tmpEdStr);
				case V_AV1 -> ExtradataContainerHex.ofAv1(tmpEdStr);
				case V_H264 -> ExtradataConvHexFmtHelper.convertH264EncoderExtradata(tmpOutputH26xAsAnnexB, tmpEdStr);
				case V_H265 -> ExtradataConvHexFmtHelper.convertH265EncoderExtradata(tmpOutputH26xAsAnnexB, tmpEdStr);
				case V_MPEG2 -> ExtradataContainerHex.ofMpeg2(tmpEdStr);
				case V_MPEG4 -> ExtradataContainerHex.ofMpeg4(tmpEdStr);
				case V_THEORA -> ExtradataContainerHex.ofTheora(tmpEdStr);
				default -> null;
			};
		if (tmpEch != null) {
			ioSsInfo.extradataHex.copyFrom(tmpEch);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<ReadResult> internalReadNextAvPacket() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadNextAvPacket()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}
		if (inputSsInfoVid.subStreamIx < 0 && inputSsInfoAud.subStreamIx < 0) {
			throw new IllegalStateException(FNC_NAME + ": need at least one input stream");
		}

		if (haveReachedMaxSecs) {
			logDebug(FNC_NAME, "cfgMaxSecs reached, stopping");
			return Optional.of(ReadResult.RR_EOF);
		}

		if (! isInputOpen) {
			int r = avformat.avformat_open_input(inputAvFmtCtx, inputPathOrUri, null, null);
			FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);
			isInputOpen = true;
			stats.startTime = Instant.now();
		}

		if (cacheAvPkt == null) {
			cacheAvPkt = avcodec.av_packet_alloc();
			if (cacheAvPkt == null) {
				throw new RuntimeException(FNC_NAME + ": Cannot allocate AVPacket");
			}
		}

		// ---------------------------------------------------

		Optional<ReadResult> tmpOptRrEn = fetchAvPacketFromFilter(cacheAvPkt);
		if (tmpOptRrEn.isPresent()) {
			return tmpOptRrEn;
		}

		// ---------------------------------------------------

		int r = avformat.av_read_frame(inputAvFmtCtx, cacheAvPkt);
		if (r == avutil.AVERROR_EOF()) {
			statsUpdateOverall();
			return Optional.of(ReadResult.RR_EOF);
		}
		FfmpegHelperFfError.checkFfmpegResult(FNC_NAME, "av_read_frame", r);

		ReadResult resEn;
		if (cacheAvPkt.stream_index() == inputSsInfoVid.subStreamIx) {
			demuxHandlePktVideo();
			resEn = ReadResult.RR_OK_VID;
		} else if (cacheAvPkt.stream_index() == inputSsInfoAud.subStreamIx) {
			demuxHandlePktAudio();
			resEn = ReadResult.RR_OK_AUD;
		} else {
			return Optional.empty();
		}

		boolean tmpStatsUpdated = statsUpdateCurrent();
		if (recvDemuxerStatsInterface != null && tmpStatsUpdated) {
			recvDemuxerStatsInterface.cbReceiveDemuxerStats(stats.clone());
		}

		// ---------------------------------------------------

		boolean tmpResB = setAvPacketForFilter(resEn == ReadResult.RR_OK_VID);
		if (tmpResB) {
			return Optional.empty();
		}

		// ---------------------------------------------------

		if (dmxSettings.cfgMaxSecs > 0 && (long) stats.currentMaxPtsSecs >= dmxSettings.cfgMaxSecs) {
			haveReachedMaxSecs = true;
		}

		return Optional.of(resEn);
	}

	private void demuxHandlePktVideo() {
		if (cacheAvPkt == null) {
			return;
		}
		++stats.pktsAndDataVid.countPkt;
		stats.pktsAndDataVid.countData += cacheAvPkt.size();

		long tmpPts = cacheAvPkt.pts();
		stats.pktsAndDataVid.currentPtsSecs = FfmpegAvPktBasics.ptsUnitsToSecondsHelper(
				tmpPts == avutil.AV_NOPTS_VALUE ? null : tmpPts,
				inputSsInfoVid.timeBasePts
			);
		/*
		logDebug(FNC_NAME, "Video frame #" + Integer.toUnsignedString(stats.pktsAndDataVid.countPkt) + ": " +
				"ptsUnits=" + (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.pts()) +
				", ptsSecs=" + Double.toString(ptsSeconds) +
				", dtsUnits=" + (cacheAvPkt.dts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.dts()) +
				", dataSz=" + cacheAvPkt.size());
		*/

		// [cacheAvPkt] contains one compressed video frame (or one packet, depending on codec)
	}

	private void demuxHandlePktAudio() {
		if (cacheAvPkt == null) {
			return;
		}
		++stats.pktsAndDataAud.countPkt;
		stats.pktsAndDataAud.countData += cacheAvPkt.size();

		long tmpPts = cacheAvPkt.pts();
		stats.pktsAndDataAud.currentPtsSecs = FfmpegAvPktBasics.ptsUnitsToSecondsHelper(
				tmpPts == avutil.AV_NOPTS_VALUE ? null : tmpPts,
				inputSsInfoAud.timeBasePts
			);
		/*
		logDebug(FNC_NAME, "Audio frame #" + Integer.toUnsignedString(stats.pktsAndDataAud.countPkt) + ": " +
				"ptsUnits=" + (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.pts()) +
				", ptsSecs=" + Double.toString(ptsSeconds) +
				", dtsUnits=" + (cacheAvPkt.dts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.dts()));
		*/

		// [cacheAvPkt] contains one (un-)compressed audio packet
	}

	// ---------------------------------------------------

	private Optional<ReadResult> fetchAvPacketFromFilter(@NonNull AVPacket outAvPkt) throws FfmpegGenericException {
		if (bsfH26x != null && bsfH26x.receiveOneConvertedPacket(outAvPkt)) {
			return Optional.of(ReadResult.RR_OK_VID);
		}
		return Optional.empty();
	}

	private boolean setAvPacketForFilter(boolean isVideo) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".setAvPacketForFilter()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}
		if (cacheAvPkt == null) {
			throw new IllegalStateException(FNC_NAME + ": cacheAvPkt is null");
		}

		if (! haveCheckedFrameForH26xAnnexB &&
				isVideo &&
				(inputSsInfoVid.ffmpegCodec == FfmpegCodec.V_H264 ||
						inputSsInfoVid.ffmpegCodec == FfmpegCodec.V_H265) &&
				dmxSettings.cfgOutputModeH26x != FfmpegPktConvModeH26x.PASSTHROUGH) {
			boolean tmpIsAnnexB = FfmpegHelperBsfH26xToLengthPrefixed.isAnnexB(cacheAvPkt);
			boolean needBsfAnnexB = (! tmpIsAnnexB && dmxSettings.cfgOutputModeH26x == FfmpegPktConvModeH26x.ANNEXB);
			boolean needBsfLp = (tmpIsAnnexB && dmxSettings.cfgOutputModeH26x == FfmpegPktConvModeH26x.LENGTH_PREFIXED);
			if (needBsfAnnexB) {
				bsfH26x = new FfmpegHelperBsfH26xToAnnexB(
						inputSsInfoVid.ffmpegCodec == FfmpegCodec.V_H264,
						inputAvFmtCtx.streams(inputSsInfoVid.subStreamIx)
					);
			} else if (needBsfLp) {
				bsfH26x = new FfmpegHelperBsfH26xToLengthPrefixed();
			}
			haveCheckedFrameForH26xAnnexB = true;
		}

		if (isVideo && bsfH26x != null) {
			bsfH26x.setInputPacket(cacheAvPkt);
			return true;
		}

		return false;
	}

	// ---------------------------------------------------

	/*private boolean havePrintedAacInfo = false;*/

	private void copyCachedAvPacketToOutput(
				boolean isVideo,
				@NonNull FfmpegAvPktBasics outputPktBasics
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".copyCachedAvPacketToOutput()";

		if (cacheAvPkt == null) {
			throw new IllegalStateException(FNC_NAME + ": cacheAvPkt is null");
		}

		if (! haveCheckedFrameForAacAdts &&
				! isVideo &&
				inputSsInfoAud.ffmpegCodec == FfmpegCodec.A_AAC &&
				dmxSettings.cfgOutputModeAac != FfmpegPktConvModeAac.PASSTHROUGH) {
			boolean hasAdtsHeader = FfmpegHelperBsfAacNoAdts.hasAdtsHeader(cacheAvPkt);
			boolean needAacAddAdts = (! hasAdtsHeader && dmxSettings.cfgOutputModeAac == FfmpegPktConvModeAac.WITH_ADTS);
			boolean needAacRemoveAdts = (hasAdtsHeader && dmxSettings.cfgOutputModeAac == FfmpegPktConvModeAac.NO_ADTS);
			if (needAacAddAdts) {
				bsfAac = FfmpegHelperBsfAacWithAdts.fromAsc(inputSsInfoAud.extradataHex);
			} else if (needAacRemoveAdts) {
				bsfAac = new FfmpegHelperBsfAacNoAdts();
			}
			//
			haveCheckedFrameForAacAdts = true;
		}

		//
		FfmpegHelperPktConverter.convertFfToBasics(
				false,  // output packet buffer will be cleared
				isVideo,
				cacheAvPkt,
				isVideo ? inputSsInfoVid.timeBasePts : inputSsInfoAud.timeBasePts,
				outputPktBasics
			);

		//
		if (! isVideo && bsfAac != null) {
			bsfAac.processPkt(cacheAvPkt, outputPktBasics.pktBe);
		} else {
			outputPktBasics.pktBe.increaseSize(cacheAvPkt.size());
			cacheAvPkt.data().get(outputPktBasics.pktBe.getBaPtr(), 0, cacheAvPkt.size());
			outputPktBasics.pktBe.setUsed(cacheAvPkt.size());
		}

		/*if (! havePrintedAacInfo && ! isVideo && inputSsInfoAud.ffmpegCodec == FfmpegCodec.A_AAC) {
			logDebug(FNC_NAME,
					"**************************** output AAC=" +
					outputPktBasics.pktBe.slice(0, 2).toHexString(true));
			havePrintedAacInfo = true;
		}*/
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean statsUpdateCurrent() {
		if (stats.startTime == null || FPS_MEASURE_INTERVAL_MS < 1) {
			return false;
		}

		//
		stats.currentMaxPtsSecs = -1.0;
		if (stats.pktsAndDataVid.countPkt > 0) {
			stats.currentMaxPtsSecs = stats.pktsAndDataVid.currentPtsSecs;
		}
		if (stats.pktsAndDataAud.countPkt > 0) {
			if (stats.currentMaxPtsSecs < 0.0 || stats.pktsAndDataAud.currentPtsSecs > stats.currentMaxPtsSecs) {
				stats.currentMaxPtsSecs = stats.pktsAndDataAud.currentPtsSecs;
			}
		}

		//
		if (stats.lastFpsMeasureTime == null) {
			stats.lastFpsMeasureTime = stats.startTime;
		}
		long deltaMs = Duration.between(stats.lastFpsMeasureTime, Instant.now()).toMillis();
		if (deltaMs < FPS_MEASURE_INTERVAL_MS) {
			return false;
		}
		stats.lastFpsMeasureTime = Instant.now();

		if (stats.pktsAndDataVid.countPkt > 0) {
			int deltaPkts = stats.pktsAndDataVid.countPkt - stats.pktsAndDataVid.lastCountPkt;
			stats.pktsAndDataVid.lastCountPkt = stats.pktsAndDataVid.countPkt;

			stats.pktsAndDataVid.currentPktsPerSec = ((double)deltaPkts * 1000.0) / (double)deltaMs;
			stats.pktsAndDataVid.pktsPerSecsList.add(stats.pktsAndDataVid.currentPktsPerSec);
			if (stats.pktsAndDataVid.pktsPerSecsList.size() > 60 * 60 * 4) {  // ^= 4 hours
				stats.pktsAndDataVid.pktsPerSecsList.removeFirst();
			}
		}

		if (stats.pktsAndDataAud.countPkt > 0) {
			int deltaPkts = stats.pktsAndDataAud.countPkt - stats.pktsAndDataAud.lastCountPkt;
			stats.pktsAndDataAud.lastCountPkt = stats.pktsAndDataAud.countPkt;

			stats.pktsAndDataAud.currentPktsPerSec = ((double)deltaPkts * 1000.0) / (double)deltaMs;
			stats.pktsAndDataAud.pktsPerSecsList.add(stats.pktsAndDataAud.currentPktsPerSec);
			if (stats.pktsAndDataAud.pktsPerSecsList.size() > 60 * 60 * 4) {  // ^= 4 hours
				stats.pktsAndDataAud.pktsPerSecsList.removeFirst();
			}
		}

		return true;
	}

	private void statsUpdateOverall() {
		if (stats.startTime == null) {
			return;
		}

		stats.currentMaxPtsSecs = -1.0;

		if (stats.pktsAndDataVid.countPkt > 0) {
			stats.currentMaxPtsSecs = stats.pktsAndDataVid.currentPtsSecs;
		}

		if (stats.pktsAndDataAud.countPkt > 0) {
			if (stats.currentMaxPtsSecs < 0.0 || stats.pktsAndDataAud.currentPtsSecs > stats.currentMaxPtsSecs) {
				stats.currentMaxPtsSecs = stats.pktsAndDataAud.currentPtsSecs;
			}
		}
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
