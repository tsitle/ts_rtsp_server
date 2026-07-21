package org.tsitle.lib_ffmpeg.demux;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull String inputFilePath;
	private final @NonNull FfmpegDmxSettingsInternal dmxSettings;
	private final @Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface;

	private @Nullable AVFormatContext inputAvFmtCtx;
	private final @NonNull FfmpegStreamInfoVideo inputStreamInfoVid = new FfmpegStreamInfoVideo();
	private final @NonNull FfmpegStreamInfoAudio inputStreamInfoAud = new FfmpegStreamInfoAudio();
	private final @NonNull FfmpegDmxStats stats = new FfmpegDmxStats();
	private boolean isInputOpen = false;
	private @Nullable AVPacket cacheAvPkt = null;
	private boolean haveReachedMaxSecs = false;

	private @Nullable BsfH26xAnnexB bsfH26xAnnexB = null;
	private @Nullable AacAdtsPacketizer aacAdtsPacketizer = null;

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputFilePath Path to the input file
	 * @param dmxSettings Settings for demuxing
	 * @param recvDemuxerStatsInterface 'Receive Demuxer Stats' instance (can be null)
	 */
	private FfmpegDemuxer(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputFilePath,
				@NonNull FfmpegDmxSettingsInternal dmxSettings,
				@Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.inputFilePath = inputFilePath;
		this.dmxSettings = dmxSettings;
		this.recvDemuxerStatsInterface = recvDemuxerStatsInterface;

		if (inputFilePath.isBlank()) {
			throw new IllegalArgumentException(FfmpegDemuxer.class.getSimpleName() + ".ctor(): inputFilePath is blank");
		}

		//
		inputAvFmtCtx = avformat.avformat_alloc_context();
		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FfmpegDemuxer.class.getSimpleName() + ".ctor(): could not allocate inputAvFmtCtx");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Read the stream info from the input file.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputFilePath Path to the input file
	 * @param dmxSettings Settings for demuxing (only for reading the sub-stream infos)
	 * @param outStreamInfoVid Output for the video stream info
	 * @param outStreamInfoAud Output for the audio stream info
	 * @throws FfmpegGenericException If any FFmpeg error occurs
	 */
	public static void readStreamInfos(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputFilePath,
				@NonNull FfmpegDmxSettingsRsi dmxSettings,
				@NonNull FfmpegStreamInfoVideo outStreamInfoVid,
				@NonNull FfmpegStreamInfoAudio outStreamInfoAud
			) throws FfmpegGenericException {
		outStreamInfoVid.reset();
		outStreamInfoAud.reset();

		try (FfmpegDemuxer ffmpegDemuxer = new FfmpegDemuxer(
					logMsgInterface,
					inputFilePath,
					FfmpegDmxSettingsInternal.of(dmxSettings),
					null
				)) {
			ffmpegDemuxer.internalReadStreamInfos();

			outStreamInfoVid.copyFrom(ffmpegDemuxer.inputStreamInfoVid);
			outStreamInfoAud.copyFrom(ffmpegDemuxer.inputStreamInfoAud);
		}
	}

	/**
	 * Create a Demuxer for demuxing only.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputFilePath Path to the input file
	 * @param dmxSettings Settings for demuxing
	 */
	public static FfmpegDemuxer createForDemuxingOnly(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputFilePath,
				@NonNull FfmpegDmxSettingsDemux dmxSettings
			) {
		return new FfmpegDemuxer(
				logMsgInterface,
				inputFilePath,
				FfmpegDmxSettingsInternal.of(dmxSettings),
				null
			);
	}

	/**
	 * Create a Demuxer for transcoding.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputFilePath Path to the input file
	 * @param dmxSettings Settings for demuxing
	 * @param recvDemuxerStatsInterface 'Receive Demuxer Stats' instance (can be null)
	 */
	@SuppressWarnings("unused")
	public static FfmpegDemuxer createForTranscoding(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputFilePath,
				@NonNull FfmpegDmxSettingsTc dmxSettings,
				@Nullable FfmpegReceiveDemuxerStatsInterface recvDemuxerStatsInterface
			) {
		return new FfmpegDemuxer(
				logMsgInterface,
				inputFilePath,
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
	public @NonNull ReadResult readNextAvPacket(@NonNull FfmpegAvPktBasics outputData) throws FfmpegGenericException {
		outputData.clear();

		//
		if (inputStreamInfoVid.streamIx < 0 && inputStreamInfoAud.streamIx < 0) {
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

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull FfmpegDmxStats getStatsPtr() {
		return stats;
	}

	@SuppressWarnings("unused")
	public Optional<AVFormatContext> getFfAvFmtCtxPtr() {
		return Optional.ofNullable(inputAvFmtCtx);
	}

	@SuppressWarnings("unused")
	public Optional<Integer> getFfAvStreamIxVideo() {
		if (inputStreamInfoVid.streamIx < 0) {
			return Optional.empty();
		}
		return Optional.of(inputStreamInfoVid.streamIx);
	}

	@SuppressWarnings("unused")
	public Optional<Integer> getFfAvStreamIxAudio() {
		if (inputStreamInfoAud.streamIx < 0) {
			return Optional.empty();
		}
		return Optional.of(inputStreamInfoAud.streamIx);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		if (inputAvFmtCtx != null) { avformat.avformat_free_context(inputAvFmtCtx); inputAvFmtCtx = null; }
		if (cacheAvPkt != null) { avcodec.av_packet_free(cacheAvPkt); cacheAvPkt = null; }
		if (bsfH26xAnnexB != null) { bsfH26xAnnexB.close(); bsfH26xAnnexB = null; }
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalReadStreamInfos() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadStreamInfos()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		logDebug(FNC_NAME, "Read file: '" + inputFilePath + "'");

		int r = avformat.avformat_open_input(inputAvFmtCtx, inputFilePath, null, null);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);

		r = avformat.avformat_find_stream_info(inputAvFmtCtx, (AVDictionary)null);
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_find_stream_info", r);

		findStreamIndices();

		if (inputStreamInfoVid.streamIx == -1 && inputStreamInfoAud.streamIx == -1) {
			avformat.avformat_close_input(inputAvFmtCtx);
			throw new FfmpegGenericException(FNC_NAME + ": No supported video or audio stream found");
		}

		// ------------------------------------------------

		if (inputStreamInfoVid.streamIx != -1) {
			getStreamInfoVideo(inputAvFmtCtx, inputStreamInfoVid);
		}
		if (inputStreamInfoAud.streamIx != -1) {
			getStreamInfoAudio(inputAvFmtCtx, inputStreamInfoAud);
		}
		avformat.avformat_close_input(inputAvFmtCtx);
	}

	private void findStreamIndices() {
		final String FNC_NAME = getClass().getSimpleName() + ".findStreamIndices()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		inputStreamInfoVid.streamIx = -1;
		inputStreamInfoAud.streamIx = -1;

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
					++curStreamNumberVid;
					if (! tmpFfmpegCodec.isVideo()) {
						logDebug(FNC_NAME,
								String.format("(ignoring video track #v:%d with unsupported codec [%s])",
										curStreamNumberVid, tmpCodecName)
							);
						continue;
					}
					if (inputStreamInfoVid.streamIx < 0 &&
							(dmxSettings.cfgSelectStreamNumberVideo < 1 ||
									curStreamNumberVid == dmxSettings.cfgSelectStreamNumberVideo) &&
							(! dmxSettings.cfgAllowOnlySpecificCodecsVideo || dmxSettings.cfgAllowedCodecsVideo.contains(tmpFfmpegCodec))) {
						logDebug(FNC_NAME, String.format("found video track #v:%d [%s]", curStreamNumberVid, tmpCodecName));
						inputStreamInfoVid.streamIx = i;
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
					if (inputStreamInfoAud.streamIx < 0 &&
							(dmxSettings.cfgSelectStreamNumberAudio < 1 ||
									curStreamNumberAud == dmxSettings.cfgSelectStreamNumberAudio) &&
							(! dmxSettings.cfgAllowOnlySpecificCodecsAudio || dmxSettings.cfgAllowedCodecsAudio.contains(tmpFfmpegCodec))) {
						logDebug(FNC_NAME, String.format("found audio track #a:%d [%s]", curStreamNumberAud, tmpCodecName));
						inputStreamInfoAud.streamIx = i;
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

	private static @NonNull RationalNumber getStreamTimeBase(@NonNull AVFormatContext inputAvFmtCtx, int streamIx) {
		int tmpResNum = 0;
		int tmpResDen = 1;
		if (streamIx >= 0) {
			AVStream st = inputAvFmtCtx.streams(streamIx);
			tmpResNum = st.time_base().num();
			tmpResDen = st.time_base().den();
		}
		return RationalNumber.of(tmpResNum, tmpResDen);
	}

	private static void getStreamInfoVideo(
				@NonNull AVFormatContext inputAvFmtCtx,
				@NonNull FfmpegStreamInfoVideo ioStreamInfoVideo
			) {
		ioStreamInfoVideo.timeBasePts = getStreamTimeBase(inputAvFmtCtx, ioStreamInfoVideo.streamIx);

		//
		if (ioStreamInfoVideo.streamIx < 0) {
			return;
		}
		AVStream st = inputAvFmtCtx.streams(ioStreamInfoVideo.streamIx);
		ioStreamInfoVideo.ffmpegCodec = FfmpegCodec.of(st.codecpar().codec_id());
		ioStreamInfoVideo.imgDims = ImageDimensions.of(st.codecpar().width(), st.codecpar().height());
		ioStreamInfoVideo.bitRate = st.codecpar().bit_rate();
		/*
		 * We might need to extract st.codecpar().extradata() as well (SPS/PPS for H26x)
		 */

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
		ioStreamInfoVideo.fps = RationalNumber.of(tmpFpsNum, tmpFpsDen);
	}

	private static void getStreamInfoAudio(
				@NonNull AVFormatContext inputAvFmtCtx,
				@NonNull FfmpegStreamInfoAudio ioStreamInfoAudio
			) {
		ioStreamInfoAudio.timeBasePts = getStreamTimeBase(inputAvFmtCtx, ioStreamInfoAudio.streamIx);

		//
		if (ioStreamInfoAudio.streamIx < 0) {
			return;
		}
		AVStream st = inputAvFmtCtx.streams(ioStreamInfoAudio.streamIx);
		ioStreamInfoAudio.ffmpegCodec = FfmpegCodec.of(st.codecpar().codec_id());
		ioStreamInfoAudio.sampleRate = SampleRateEnum.of(st.codecpar().sample_rate());
		ioStreamInfoAudio.channelCount = st.codecpar().ch_layout().nb_channels();
		ioStreamInfoAudio.bitRate = st.codecpar().bit_rate();
		ioStreamInfoAudio.bitsPerCodedSample = st.codecpar().bits_per_coded_sample();
		if (ioStreamInfoAudio.ffmpegCodec == FfmpegCodec.A_AAC &&
				st.codecpar().extradata() != null && st.codecpar().extradata_size() > 0) {
			ioStreamInfoAudio.aacSamplesPerFrame = st.codecpar().frame_size();
			try (BytePointer tmpBp = st.codecpar().extradata()) {
				int extradataSize = st.codecpar().extradata_size();
				byte[] ascBytes = new byte[extradataSize];
				tmpBp.position(0).get(ascBytes, 0, extradataSize);
				ioStreamInfoAudio.aacAudioSpecificConfigHex = HexFormat.of().withUpperCase().formatHex(ascBytes);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<ReadResult> internalReadNextAvPacket() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadNextAvPacket()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}
		if (inputStreamInfoVid.streamIx < 0 && inputStreamInfoAud.streamIx < 0) {
			throw new IllegalStateException(FNC_NAME + ": need at least one input stream");
		}

		if (haveReachedMaxSecs) {
			logDebug(FNC_NAME, "cfgMaxSecs reached, stopping");
			return Optional.of(ReadResult.RR_EOF);
		}

		if (! isInputOpen) {
			int r = avformat.avformat_open_input(inputAvFmtCtx, inputFilePath, null, null);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);
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

		Optional<ReadResult> tmpOptRrEn = fetchAvPacketFromFilter();
		if (tmpOptRrEn.isPresent()) {
			return tmpOptRrEn;
		}

		// ---------------------------------------------------

		int r = avformat.av_read_frame(inputAvFmtCtx, cacheAvPkt);
		if (r == avutil.AVERROR_EOF()) {
			statsUpdateOverall();
			return Optional.of(ReadResult.RR_EOF);
		}
		FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "av_read_frame", r);

		ReadResult resEn;
		if (cacheAvPkt.stream_index() == inputStreamInfoVid.streamIx) {
			demuxHandlePktVideo();
			resEn = ReadResult.RR_OK_VID;
		} else if (cacheAvPkt.stream_index() == inputStreamInfoAud.streamIx) {
			demuxHandlePktAudio();
			resEn = ReadResult.RR_OK_AUD;
		} else {
			return Optional.empty();
		}

		boolean tmpStatsUpdated = statsUpdateCurrent();
		if (recvDemuxerStatsInterface != null && tmpStatsUpdated) {
			recvDemuxerStatsInterface.cbReceiveDemuxerStats(stats);
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

		FfmpegAvPktBasics tmpFfAvPktBas = new FfmpegAvPktBasics();
		tmpFfAvPktBas.timeBase.copyFrom(inputStreamInfoVid.timeBasePts);
		tmpFfAvPktBas.ptsUnits = (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? null : cacheAvPkt.pts());
		stats.pktsAndDataVid.currentPtsSecs = tmpFfAvPktBas.ptsUnitsToSeconds();
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

		FfmpegAvPktBasics tmpFfAvPktBas = new FfmpegAvPktBasics();
		tmpFfAvPktBas.timeBase.copyFrom(inputStreamInfoAud.timeBasePts);
		tmpFfAvPktBas.ptsUnits = (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? null : cacheAvPkt.pts());
		stats.pktsAndDataAud.currentPtsSecs = tmpFfAvPktBas.ptsUnitsToSeconds();
		/*
		logDebug(FNC_NAME, "Audio frame #" + Integer.toUnsignedString(stats.pktsAndDataAud.countPkt) + ": " +
				"ptsUnits=" + (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.pts()) +
				", ptsSecs=" + Double.toString(ptsSeconds) +
				", dtsUnits=" + (cacheAvPkt.dts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : cacheAvPkt.dts()));
		*/

		// [cacheAvPkt] contains one (un-)compressed audio packet
	}

	// ---------------------------------------------------

	private Optional<ReadResult> fetchAvPacketFromFilter() throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".fetchAvPacketFromFilter()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}
		if (cacheAvPkt == null) {
			throw new IllegalStateException(FNC_NAME + ": cacheAvPkt is null");
		}

		if (bsfH26xAnnexB == null && dmxSettings.cfgOutputH26xAsAnnexB &&
				(inputStreamInfoVid.ffmpegCodec == FfmpegCodec.V_H264 || inputStreamInfoVid.ffmpegCodec == FfmpegCodec.V_H265)) {
			bsfH26xAnnexB = new BsfH26xAnnexB(
					inputStreamInfoVid.ffmpegCodec == FfmpegCodec.V_H264,
					inputAvFmtCtx.streams(inputStreamInfoVid.streamIx)
				);
		}

		if (bsfH26xAnnexB != null && bsfH26xAnnexB.receiveOnePacket(cacheAvPkt)) {
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

		if (isVideo && bsfH26xAnnexB != null) {
			bsfH26xAnnexB.setInputPacket(cacheAvPkt);
			return true;
		}

		return false;
	}

	// ---------------------------------------------------

	private void copyCachedAvPacketToOutput(
				boolean isVideo,
				@NonNull FfmpegAvPktBasics outputData
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".copyCachedAvPacketToOutput()";

		if (cacheAvPkt == null) {
			throw new IllegalStateException(FNC_NAME + ": cacheAvPkt is null");
		}

		if (! isVideo && dmxSettings.cfgOutputAacWithAdts && inputStreamInfoAud.ffmpegCodec == FfmpegCodec.A_AAC) {
			if (aacAdtsPacketizer == null) {
				aacAdtsPacketizer = AacAdtsPacketizer.fromAsc(inputStreamInfoAud.aacAudioSpecificConfigHex);
			}
			aacAdtsPacketizer.wrapAuWithAdts(cacheAvPkt, outputData.pktBe);
		} else {
			outputData.pktBe.increaseSize(cacheAvPkt.size());
			cacheAvPkt.data().get(outputData.pktBe.getBaPtr(), 0, cacheAvPkt.size());
			outputData.pktBe.setUsed(cacheAvPkt.size());
		}

		outputData.ptsUnits = (cacheAvPkt.pts() == avutil.AV_NOPTS_VALUE ? null : cacheAvPkt.pts());
		outputData.dtsUnits = (cacheAvPkt.dts() == avutil.AV_NOPTS_VALUE ? null : cacheAvPkt.dts());
		outputData.timeBase.copyFrom(
				isVideo ? inputStreamInfoVid.timeBasePts : inputStreamInfoAud.timeBasePts
			);
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
		}

		if (stats.pktsAndDataAud.countPkt > 0) {
			int deltaPkts = stats.pktsAndDataAud.countPkt - stats.pktsAndDataAud.lastCountPkt;
			stats.pktsAndDataAud.lastCountPkt = stats.pktsAndDataAud.countPkt;

			stats.pktsAndDataAud.currentPktsPerSec = ((double)deltaPkts * 1000.0) / (double)deltaMs;
			stats.pktsAndDataAud.pktsPerSecsList.add(stats.pktsAndDataAud.currentPktsPerSec);
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
