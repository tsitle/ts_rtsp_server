package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Streams configuration for the RTSP server.
 */
public final class RtspSrvConfigFileStreams extends RtspSrvConfigFileBase {

	/** Map of Sub-Streams */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> subStreams;
	/** Map of Streams */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> streams;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigFileStreams() {
		this.subStreams = new HashMap<>();
		this.streams = new HashMap<>();

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> getSubStreams() {
		checkPostProcessed();
		Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> resMap = new HashMap<>();
		for (Map.Entry<String, RtspSrvConfigStreamsSs> entry : subStreams.entrySet()) {
			resMap.put(entry.getKey(), entry.getValue().clone());
		}
		return resMap;
	}

	public @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> getStreams() {
		checkPostProcessed();
		Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> resMap = new HashMap<>();
		for (Map.Entry<String, RtspSrvConfigStreamsStream> entry : streams.entrySet()) {
			resMap.put(entry.getKey(), entry.getValue().clone());
		}
		return resMap;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	void postProcess() throws ConfigInvalidException {
		internalHasBeenPostProcessed = true;

		//
		subStreams = RewriteMapStringXxxHelper.removeNullAndBlank(subStreams, true);
		for (Map.Entry<String, RtspSrvConfigStreamsSs> entry : subStreams.entrySet()) {
			entry.getValue().postProcess();
		}

		//
		streams = RewriteMapStringXxxHelper.removeNullAndBlank(streams, true);
		for (Map.Entry<String, RtspSrvConfigStreamsStream> entry : streams.entrySet()) {
			entry.getValue().postProcess();
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(
				@NonNull Set<@NonNull String> userAccountGroupIds,
				@NonNull Path dataDirPath
			) throws ConfigInvalidException {
		checkPostProcessed();

		//
		for (Map.Entry<String, RtspSrvConfigStreamsSs> entry : subStreams.entrySet()) {
			entry.getValue().validate(entry.getKey(), dataDirPath);
		}

		//
		for (Map.Entry<String, RtspSrvConfigStreamsStream> entry : streams.entrySet()) {
			entry.getValue().validate(entry.getKey(), userAccountGroupIds);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
