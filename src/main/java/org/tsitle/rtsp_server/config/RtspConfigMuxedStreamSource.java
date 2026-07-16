package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.demux.FfmpegStreamInfoAudio;
import org.tsitle.lib_ffmpeg.demux.FfmpegStreamInfoVideo;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdMsSource;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	public void setIdAsInt(int id) { this.id = id; }

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

	public static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

	/**
	 * Post-process the Muxed-Stream Source.
	 * @param dataDir Directory containing the media files
	 */
	public void postProcess(@NonNull Path dataDir) {
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
	public void validate(
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
		readSubStreamHeaders(tmpExtSsId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Muxed-Stream Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void readSubStreamHeaders(@NonNull String extMsId)
			throws ConfigInvalidException {
		FfmpegStreamInfoVideo streamInfoVideo = new FfmpegStreamInfoVideo();
		FfmpegStreamInfoAudio streamInfoAudio = new FfmpegStreamInfoAudio();

		try {
			FfmpegDemuxer ffmpegDemuxer = new FfmpegDemuxer(
					null,
					getInputUri().getPath(),
					1L,
					null
				);
			ffmpegDemuxer.readStreamInfos(streamInfoVideo, streamInfoAudio);
		} catch (FfmpegGenericException e) {
			throw new ConfigInvalidException("Failed to read sub-stream headers " +
					"for Muxed-Stream Source ID '" + extMsId + "': " + e.getMessage());
		}

		if (streamInfoVideo.ffmpegCodec == FfmpegCodec.UNKNOWN && streamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			throw new ConfigInvalidException("No A/V sub-streams found " +
					"for Muxed-Stream Source ID '" + extMsId + "'");
		}

		if (streamInfoVideo.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			Set<FfmpegCodec> allowedCodecs = new HashSet<>() {{
					add(FfmpegCodec.V_H264);
					add(FfmpegCodec.V_H265);
					add(FfmpegCodec.V_MJPEG);
				}};
			if (! allowedCodecs.contains(streamInfoVideo.ffmpegCodec)) {
				throw new ConfigInvalidException("Video sub-stream codec " + streamInfoVideo.ffmpegCodec + " is not supported " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}
		}
		if (streamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (streamInfoAudio.ffmpegCodec.isPcmAudio() && streamInfoAudio.channelCount > 2) {
				throw new ConfigInvalidException("Audio sub-stream is PCM with more than 2 channels " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}
			Set<FfmpegCodec> allowedCodecs = new HashSet<>() {{
					add(FfmpegCodec.A_AAC);
					add(FfmpegCodec.A_AC3);
					add(FfmpegCodec.A_PCM_ALAW);
					add(FfmpegCodec.A_PCM_MULAW);
					add(FfmpegCodec.A_PCM_S16BE);
					add(FfmpegCodec.A_PCM_S16LE);
					add(FfmpegCodec.A_PCM_U8);
				}};
			if (! allowedCodecs.contains(streamInfoAudio.ffmpegCodec)) {
				throw new ConfigInvalidException("Audio sub-stream codec " + streamInfoAudio.ffmpegCodec + " is not supported " +
						"for Muxed-Stream Source ID '" + extMsId + "'");
			}
		}
	}

}
