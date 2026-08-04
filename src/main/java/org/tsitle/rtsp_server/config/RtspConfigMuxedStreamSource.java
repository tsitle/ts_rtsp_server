package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdMsSource;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/**
 * Muxed-Stream Source within an Input Source for RTSP streams.
 */
public final class RtspConfigMuxedStreamSource {

	/** Muxed-Stream Source ID */
	@GsonAnnoExclude
	private @NonNull Integer id;
	/** Is this Muxed-Stream Source enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Path to the media file */
	@Expose
	private @NonNull String filePath;
	/** URL to the RTSP stream */
	@Expose
	private @NonNull String rtspUrl;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	@GsonAnnoExclude
	private final FfmpegDmxSubStreamInfoVideo ffSubStreamInfoVideo = new FfmpegDmxSubStreamInfoVideo();
	@GsonAnnoExclude
	private final FfmpegDmxSubStreamInfoAudio ffSubStreamInfoAudio = new FfmpegDmxSubStreamInfoAudio();

	public RtspConfigMuxedStreamSource() {
		this.id = -1;
		this.enabled = true;
		this.filePath = "";
		this.rtspUrl = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getIdAsInt() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (id == null ? -1 : id);
	}
	public @NonNull RtspProtoIdMsSource getIdAsProtoId() {
		RtspProtoIdMsSource resObj = RtspProtoIdMsSource.ofEmpty();
		resObj.setIdStr(Integer.toUnsignedString(getIdAsInt()));
		resObj.writeProtect();
		return resObj;
	}

	public boolean getEnabled() {
		checkPostProcessed();
		return enabled;
	}

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		//noinspection ConstantValue
		if (filePath != null && ! filePath.isBlank()) {
			return URI.create("file:" + filePath);
		}
		//noinspection ConstantValue
		if (rtspUrl != null && rtspUrl.startsWith("rtsp://")) {
			return URI.create(rtspUrl.replace("rtsp://", "http://"));
		}
		//noinspection ConstantValue
		if (rtspUrl != null && rtspUrl.startsWith("rtsps://")) {
			return URI.create(rtspUrl.replace("rtsps://", "https://"));
		}
		throw new IllegalStateException("filePath and rtspUrl are blank");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void setIdAsInt(int id) { this.id = id; }

	@NonNull FfmpegDmxSubStreamInfoVideo getFfSubStreamInfoVideo() {
		FfmpegDmxSubStreamInfoVideo resObj = new FfmpegDmxSubStreamInfoVideo();
		resObj.copyFrom(ffSubStreamInfoVideo);
		return resObj;
	}

	@NonNull FfmpegDmxSubStreamInfoAudio getFfSubStreamInfoAudio() {
		FfmpegDmxSubStreamInfoAudio resObj = new FfmpegDmxSubStreamInfoAudio();
		resObj.copyFrom(ffSubStreamInfoAudio);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Muxed-Stream Source.
	 * @param dataDir Directory containing the media files
	 */
	void postProcess(@NonNull Path dataDir) {
		internalHasBeenPostProcessed = true;
		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}
		//
		//noinspection ConstantValue
		if (filePath != null && ! filePath.isBlank()) {
			filePath = dataFilenameToAbsolutePath(dataDir, filePath);
		} else {
			filePath = "";
		}
		//
		//noinspection ConstantValue
		if (rtspUrl == null || rtspUrl.isBlank()) {
			rtspUrl = "";
		}
	}

	/**
	 * Validate the Muxed-Stream Source.
	 * @param mapStreamSourceIdIntToExt Map of Muxed-Stream Source IDs (internal) to their external representation
	 * @throws ConfigInvalidException If the Muxed-Stream Source is invalid
	 */
	void validate(
				@NonNull Map<@NonNull Integer, @NonNull String> mapStreamSourceIdIntToExt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		String tmpExtSsId = mapStreamSourceIdIntToExt.get(getIdAsInt());

		if (getIdAsInt() < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Muxed-Stream Source has no ID");
		}

		//
		final String errMsgSuffix = " for Muxed-Stream Source ID '" + tmpExtSsId + "'";
		if (filePath.isBlank() && rtspUrl.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No File Path nor RTSP URL found" + errMsgSuffix);
		}
		if (! (filePath.isBlank() || rtspUrl.isBlank())) {
			throw new ConfigInvalidException(FNC_NAME + ": Cannot have both File Path and RTSP URL" + errMsgSuffix);
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid File Path '" + filePath + "'" + errMsgSuffix +
					" - file not found");
		}
		if (! rtspUrl.isBlank() &&
				! (rtspUrl.startsWith("rtsp://") || rtspUrl.startsWith("rtsps://"))) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP URL '" + rtspUrl + "'" + errMsgSuffix +
					" - unsupported protocol");
		}

		//
		if (enabled) {
			readSubStreamInfos(tmpExtSsId);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Muxed-Stream Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void readSubStreamInfos(@NonNull String extMsId) throws ConfigInvalidException {
		FfmpegDmxSettingsRsi dmxSettingsRsi = new FfmpegDmxSettingsRsi();
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsRsi.cfgAllowedCodecsVideo.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsRsi.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);

		final String errMsgSuffix = "for Muxed-Stream Source ID '" + extMsId + "'";

		final String realUri = getInputUri().toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");
		final String errMsgUri = RtspConfigElementaryStreamSource.buildMsSourceUriForErrorMsgs(getInputUri());

		try {
			FfmpegDemuxer.readStreamInfos(
					null,
					realUri,
					dmxSettingsRsi,
					ffSubStreamInfoVideo,
					ffSubStreamInfoAudio
				);
		} catch (FfmpegGenericException e) {
			throw new ConfigInvalidException("Failed to read sub-stream infos " + errMsgSuffix + " " +
					"(src='" + errMsgUri + "'): " + e.getMessage());
		}

		if (ffSubStreamInfoVideo.ffmpegCodec == FfmpegCodec.UNKNOWN && ffSubStreamInfoAudio.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			throw new ConfigInvalidException("No A/V sub-streams found " + errMsgSuffix);
		}

		if (ffSubStreamInfoVideo.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO.contains(ffSubStreamInfoVideo.ffmpegCodec)) {
				throw new ConfigInvalidException("Video sub-stream codec " + ffSubStreamInfoVideo.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
			if (ffSubStreamInfoVideo.fps.toDouble() > 120.0) {
				/*
				 * FFmpeg sometimes reports the Time Base as the Frame Rate for RTSP streams.
				 * Then the FPS is 90000. So we set it to a safe 30.
				 */
				ffSubStreamInfoVideo.fps.copyFrom(RationalNumber.ofFps(30.0));
			}
			if (FrameRateEnum.of(ffSubStreamInfoVideo.fps.toDouble()) == FrameRateEnum.UNKNOWN) {
				approximateFps();
			}
		}
		if (ffSubStreamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO.contains(ffSubStreamInfoAudio.ffmpegCodec)) {
				throw new ConfigInvalidException("Audio sub-stream codec " + ffSubStreamInfoAudio.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.channelCount < 1) {
				throw new ConfigInvalidException("Audio sub-stream has no channels " + errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.ffmpegCodec.isPcmAudio() &&
					ffSubStreamInfoAudio.channelCount > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX) {
				throw new ConfigInvalidException("PCM Audio sub-stream with more than " +
						DpConstants.DP_PCM_AUDIO_CHANNELS_MAX + " channels " + errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.ffmpegCodec == FfmpegCodec.A_OPUS &&
					ffSubStreamInfoAudio.channelCount > DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX) {
				throw new ConfigInvalidException("Opus Audio sub-stream with more than " +
						DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX + " channels " + errMsgSuffix);
			}
		}
	}

	private void approximateFps() {
		final double orgFps = ffSubStreamInfoVideo.fps.toDouble();
		FrameRateEnum closestEn = FrameRateEnum.UNKNOWN;
		double closestDiff = Double.MAX_VALUE;
		for (FrameRateEnum tmpEn : FrameRateEnum.values()) {
			double curDiff = Math.abs(tmpEn.getFrDbl() - orgFps);
			if (Double.compare(curDiff, closestDiff) < 0) {
				closestDiff = curDiff;
				closestEn = tmpEn;
			}
		}
		if (closestEn != FrameRateEnum.UNKNOWN) {
			ffSubStreamInfoVideo.fps = RationalNumber.ofFps(closestEn.getFrDbl());
		}
	}

}
