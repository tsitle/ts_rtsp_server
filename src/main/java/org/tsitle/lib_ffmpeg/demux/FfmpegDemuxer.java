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
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Demuxer for A/V streams.
 */
public final class FfmpegDemuxer {

	private static final long FPS_MEASURE_INTERVAL_MS = 10_000L;

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull String inputFilePath;
	private final long outputMaxSecs;
	private final @Nullable FfmpegReceiveDemuxedAvInterface receiveDemuxedAvInterface;

	private @Nullable AVFormatContext inputAvFmtCtx = null;
	private final @NonNull FfmpegStreamInfoVideo inputStreamInfoVid = new FfmpegStreamInfoVideo();
	private final @NonNull FfmpegStreamInfoAudio inputStreamInfoAud = new FfmpegStreamInfoAudio();
	private @NonNull FfmpegDemuxerStats stats = new FfmpegDemuxerStats();

	/**
	 * Constructor.
	 * @param logMsgInterface 'Log message' instance (can be null)
	 * @param inputFilePath Path to the input file
	 * @param outputMaxSecs Maximum seconds to demux (<= 0 means no limit)
	 * @param receiveDemuxedAvInterface 'Receive demuxed A/V data' instance (can be null)
	 */
	public FfmpegDemuxer(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull String inputFilePath,
				long outputMaxSecs,
				@Nullable FfmpegReceiveDemuxedAvInterface receiveDemuxedAvInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.inputFilePath = inputFilePath;
		this.outputMaxSecs = outputMaxSecs;
		this.receiveDemuxedAvInterface = receiveDemuxedAvInterface;

		if (inputFilePath.isBlank()) {
			throw new IllegalArgumentException(FfmpegDemuxer.class.getSimpleName() + ".ctor(): inputFilePath is blank");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void readStreamInfos(
				@NonNull FfmpegStreamInfoVideo streamInfoVid,
				@NonNull FfmpegStreamInfoAudio streamInfoAud
			) throws FfmpegGenericException {
		final String FNC_NAME = getClass().getSimpleName() + ".readStreamInfos()";

		logDebug(FNC_NAME, "Read file: '" + inputFilePath + "'");

		streamInfoVid.reset();
		streamInfoAud.reset();

		//
		inputAvFmtCtx = avformat.avformat_alloc_context();
		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": could not allocate inputAvFmtCtx");
		}

		try {
			int r = avformat.avformat_open_input(inputAvFmtCtx, inputFilePath, null, null);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);

			r = avformat.avformat_find_stream_info(inputAvFmtCtx, (AVDictionary)null);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_find_stream_info", r);

			findStreamIndices();

			if (inputStreamInfoVid.streamIx == -1 && inputStreamInfoAud.streamIx == -1) {
				throw new FfmpegGenericException(FNC_NAME + ": No supported video or audio stream found");
			}

			// ------------------------------------------------

			if (inputStreamInfoVid.streamIx != -1) {
				getStreamInfoVideo(inputAvFmtCtx, inputStreamInfoVid);
			}
			if (inputStreamInfoAud.streamIx != -1) {
				getStreamInfoAudio(inputAvFmtCtx, inputStreamInfoAud);
			}
		} finally {
			avformat.avformat_free_context(inputAvFmtCtx);
			inputAvFmtCtx = null;
		}

		//
		streamInfoVid.copyFrom(inputStreamInfoVid);
		streamInfoAud.copyFrom(inputStreamInfoAud);
	}

	public void startDemuxing() throws FfmpegGenericException, FfmpegDecoderNotFoundException, FfmpegEncoderNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".startDemuxing()";

		logDebug(FNC_NAME, "Demux file: '" + inputFilePath + "'" +
				(outputMaxSecs > 0 ? " (max secs: " + outputMaxSecs + ")" : ""));

		inputAvFmtCtx = avformat.avformat_alloc_context();
		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": could not allocate inputAvFmtCtx");
		}

		stats = new FfmpegDemuxerStats();

		try {
			int r = avformat.avformat_open_input(inputAvFmtCtx, inputFilePath, null, null);
			FfmpegErrorHelper.checkFfmpegResult(FNC_NAME, "avformat_open_input", r);

			demuxAllPackets();
		} finally {
			avformat.avformat_free_context(inputAvFmtCtx);
			inputAvFmtCtx = null;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull FfmpegDemuxerStats getStatsPtr() {
		return stats;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void findStreamIndices() {
		final String FNC_NAME = getClass().getSimpleName() + ".findStreamIndices()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		inputStreamInfoVid.streamIx = -1;
		inputStreamInfoAud.streamIx = -1;

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
					if (! tmpFfmpegCodec.isVideo()) {
						logDebug(FNC_NAME, "(ignoring video track with unsupported codec [" + tmpCodecName + "])");
						continue;
					}
					if (inputStreamInfoVid.streamIx < 0) {
						logDebug(FNC_NAME, "found video track [" + tmpCodecName + "]");
						inputStreamInfoVid.streamIx = i;
					} else {
						logDebug(FNC_NAME, "(ignoring other video track [" + tmpCodecName + "])");
					}
					break;
				case avutil.AVMEDIA_TYPE_AUDIO:
					if (! tmpFfmpegCodec.isAudio()) {
						logDebug(FNC_NAME, "(ignoring audio track with unsupported codec [" + tmpCodecName + "])");
						continue;
					}
					if (inputStreamInfoAud.streamIx < 0) {
						logDebug(FNC_NAME, "found audio track [" + tmpCodecName + "]");
						inputStreamInfoAud.streamIx = i;
					} else {
						logDebug(FNC_NAME, "(ignoring other audio track [" + tmpCodecName + "])");
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

	private void demuxAllPackets()
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".demuxAllPackets()";

		if (inputAvFmtCtx == null) {
			throw new IllegalStateException(FNC_NAME + ": inputAvFmtCtx is null");
		}

		AVPacket pkt = new AVPacket();

		//
		stats.startTime = Instant.now();

		while (avformat.av_read_frame(inputAvFmtCtx, pkt) >= 0) {
			if (pkt.stream_index() == inputStreamInfoVid.streamIx) {
				demuxHandlePktVideo(pkt);
			} else if (pkt.stream_index() == inputStreamInfoAud.streamIx) {
				demuxHandlePktAudio(pkt);
			}

			avcodec.av_packet_unref(pkt);

			if (receiveDemuxedAvInterface != null && statsUpdateCurrent()) {
				receiveDemuxedAvInterface.cbReceiveDemuxerStats(stats);
			}

			if (outputMaxSecs > 0 && (long)stats.currentMaxPtsSecs >= outputMaxSecs) {
				break;
			}
		}

		//
		statsUpdateOverall();
	}

	private void demuxHandlePktVideo(@NonNull AVPacket pkt)
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		++stats.pktsAndDataVid.countPkt;
		stats.pktsAndDataVid.countData += pkt.size();

		stats.pktsAndDataVid.currentPtsSecs = ptsUnitsToSeconds(inputStreamInfoVid.timeBasePts, pkt.pts());
		/*
		logDebug(FNC_NAME, "Video frame #" + Integer.toUnsignedString(stats.pktsAndDataVid.countPkt) + ": " +
				"ptsUnits=" + (pkt.pts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : pkt.pts()) +
				", ptsSecs=" + Double.toString(ptsSeconds) +
				", dtsUnits=" + (pkt.dts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : pkt.dts()) +
				", dataSz=" + pkt.size());
		*/

		// [pkt] contains one compressed video frame (or one packet, depending on codec)

		if (inputAvFmtCtx == null || receiveDemuxedAvInterface == null) {
			return;
		}

		/*byte[] frameBa = new byte[pkt.size()];
		pkt.data().get(frameBa);
		transcoderVideo.transcodePacketFromBuffer(frameBa);*/

		receiveDemuxedAvInterface.cbReceiveDemuxedVideoFrame(inputAvFmtCtx, inputStreamInfoVid.streamIx, pkt);
	}

	private void demuxHandlePktAudio(@NonNull AVPacket pkt)
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		++stats.pktsAndDataAud.countPkt;
		stats.pktsAndDataAud.countData += pkt.size();

		stats.pktsAndDataAud.currentPtsSecs = ptsUnitsToSeconds(inputStreamInfoAud.timeBasePts, pkt.pts());
		/*
		logDebug(FNC_NAME, "Audio frame #" + Integer.toUnsignedString(stats.pktsAndDataAud.countPkt) + ": " +
				"ptsUnits=" + (pkt.pts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : pkt.pts()) +
				", ptsSecs=" + Double.toString(ptsSeconds) +
				", dtsUnits=" + (pkt.dts() == avutil.AV_NOPTS_VALUE ? "NOPTS" : pkt.dts()));
		*/

		// [pkt] contains one (un-)compressed audio packet

		if (inputAvFmtCtx == null || receiveDemuxedAvInterface == null) {
			return;
		}

		//byte[] samples = new byte[pkt.size()];
		//pkt.data().get(samples);

		receiveDemuxedAvInterface.cbReceiveDemuxedAudioSamples(inputAvFmtCtx, inputStreamInfoAud.streamIx, pkt);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static double ptsUnitsToSeconds(@NonNull RationalNumber timeBasePts, long ptsUnits) {
		if (ptsUnits == avutil.AV_NOPTS_VALUE) {
			return -1.0;
		}
		return ((double)ptsUnits * (double)timeBasePts.getNumerator()) / (double)timeBasePts.getDenominator();
	}

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
