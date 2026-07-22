package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
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
		if (filePath == null) {
			throw new IllegalStateException("filePath is null");
		}
		if (filePath.isBlank()) {
			throw new IllegalStateException("filePath is blank");
		}
		return URI.create("file:" + filePath);
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
		if (filePath.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No file path found for Muxed-Stream Source ID '" +
					tmpExtSsId + "'");
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid file path '" + filePath +
					"' for Muxed-Stream Source ID '" + tmpExtSsId + "' - file not found");
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

		try {
			FfmpegDemuxer.readStreamInfos(
					null,
					getInputUri().getPath(),
					dmxSettingsRsi,
					ffStreamInfoVideo,
					ffStreamInfoAudio
				);
		} catch (FfmpegGenericException e) {
			throw new ConfigInvalidException("Failed to read sub-stream infos " +
					"for Muxed-Stream Source ID '" + extMsId + "': " + e.getMessage());
		}

		if (ffStreamInfoVideo.ffmpegCodec == FfmpegCodec.UNKNOWN && ffStreamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			throw new ConfigInvalidException("No A/V sub-streams found " +
					"for Muxed-Stream Source ID '" + extMsId + "'");
		}

		if (ffStreamInfoVideo.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_VIDEO.contains(ffStreamInfoVideo.ffmpegCodec)) {
				throw new ConfigInvalidException("Video sub-stream codec " + ffStreamInfoVideo.ffmpegCodec + " is not supported " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}
		}
		if (ffStreamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (ffStreamInfoAudio.ffmpegCodec.isPcmAudio() && ffStreamInfoAudio.channelCount > 2) {
				throw new ConfigInvalidException("Audio sub-stream is PCM with more than 2 channels " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}  // @TODO
			if (! RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_AUDIO.contains(ffStreamInfoAudio.ffmpegCodec)) {
				throw new ConfigInvalidException("Audio sub-stream codec " + ffStreamInfoAudio.ffmpegCodec + " is not supported " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}
		}
	}

}
