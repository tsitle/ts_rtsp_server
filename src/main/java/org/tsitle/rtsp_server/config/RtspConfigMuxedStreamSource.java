package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdMsSource;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;

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
	private final FfmpegStreamInfoVideo ffStreamInfoVideo = new FfmpegStreamInfoVideo();
	@GsonAnnoExclude
	private final FfmpegStreamInfoAudio ffStreamInfoAudio = new FfmpegStreamInfoAudio();

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

	static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

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
		final String errMsgSuffix = "for Muxed-Stream Source ID '" + tmpExtSsId + "'";
		if (filePath.isBlank() && rtspUrl.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No File Path nor RTSP URL found " + errMsgSuffix);
		}
		if (! (filePath.isBlank() || rtspUrl.isBlank())) {
			throw new ConfigInvalidException(FNC_NAME + ": Cannot have both File Path and RTSP URL " + errMsgSuffix);
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid File Path '" + filePath + "' " + errMsgSuffix +
					" - file not found");
		}
		if (! rtspUrl.isBlank() &&
				! (rtspUrl.startsWith("rtsp://") || rtspUrl.startsWith("rtsps://"))) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP URL '" + rtspUrl + "' " + errMsgSuffix +
					" - unsupported protocol");
		}

		//
		if (enabled) {
			readSubStreamInfos(tmpExtSsId);
		}
	}

	@NonNull FfmpegStreamInfoVideo getFfStreamInfoVideoPtr() {
		return ffStreamInfoVideo;
	}

	@NonNull FfmpegStreamInfoAudio getFfStreamInfoAudioPtr() {
		return ffStreamInfoAudio;
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
		dmxSettingsRsi.cfgAllowedCodecsVideo.addAll(RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsRsi.cfgAllowedCodecsAudio.addAll(RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_AUDIO);

		final String errMsgSuffix = "for Muxed-Stream Source ID '" + extMsId + "'";

		final String realUri = getInputUri().toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");
		try {
			FfmpegDemuxer.readStreamInfos(
					null,
					realUri,
					dmxSettingsRsi,
					ffStreamInfoVideo,
					ffStreamInfoAudio
				);
		} catch (FfmpegGenericException e) {
			throw new ConfigInvalidException("Failed to read sub-stream infos " + errMsgSuffix + " " +
					"(src='" + realUri + "'): " + e.getMessage());
		}

		if (ffStreamInfoVideo.ffmpegCodec == FfmpegCodec.UNKNOWN && ffStreamInfoAudio.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			throw new ConfigInvalidException("No A/V sub-streams found " + errMsgSuffix);
		}

		if (ffStreamInfoVideo.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_VIDEO.contains(ffStreamInfoVideo.ffmpegCodec)) {
				throw new ConfigInvalidException("Video sub-stream codec " + ffStreamInfoVideo.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
			if (FrameRateEnum.of(ffStreamInfoVideo.fps.toDouble()) == FrameRateEnum.UNKNOWN) {
				approximateFps();
			}
		}
		if (ffStreamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (ffStreamInfoAudio.channelCount < 1) {
				throw new ConfigInvalidException("Audio sub-stream has no channels " + errMsgSuffix);
			}
			if (ffStreamInfoAudio.channelCount > RtpConstants.RTP_AUDIO_CHANNELS_MAX) {
				throw new ConfigInvalidException("Audio sub-stream with more than " + RtpConstants.RTP_AUDIO_CHANNELS_MAX +
						" channels " + errMsgSuffix);
			}
			if (! RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_AUDIO.contains(ffStreamInfoAudio.ffmpegCodec)) {
				throw new ConfigInvalidException("Audio sub-stream codec " + ffStreamInfoAudio.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
		}
	}

	private void approximateFps() {
		final double orgFps = ffStreamInfoVideo.fps.toDouble();
		FrameRateEnum closestEn = FrameRateEnum.UNKNOWN;
		double closestDiff = Double.MAX_VALUE;
		for (FrameRateEnum tmpEn : FrameRateEnum.values()) {
			double curDiff = Math.abs(tmpEn.getFrDbl() - orgFps);
			if (curDiff < closestDiff) {
				closestDiff = curDiff;
				closestEn = tmpEn;
			}
		}
		if (closestEn != FrameRateEnum.UNKNOWN) {
			ffStreamInfoVideo.fps = RationalNumber.ofFps(closestEn.getFrDbl());
		}
	}

}
