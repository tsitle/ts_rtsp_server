package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.nio.file.Path;
import java.util.*;

/**
 * RTSP configuration.
 */
public class RtspConfig {

	/** RTSP Server TCP port */
	@Expose
	private final int serverTcpPort;
	/** Map of Stream Sources (the map keys are unique Stream Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspStreamSource> streamSources;
	/** Map of Input Sources (the map keys are unique Input Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspInputSource> inputSources;
	/** Directory where the media files are stored */
	@Expose
	private @NonNull String dataDir;
	/** Debugging: print the SDP description? */
	@Expose
	private final boolean debugPrintSDP;
	/** Debugging: rewind the media files? */
	@Expose
	private final boolean debugRewindMediaFiles;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Map of Stream Sources (the map keys are unique Stream Source identifiers) */
	@GsonAnnoExclude
	private @NonNull Map<@NonNull Integer, @NonNull RtspStreamSource> internalStreamSources;
	/** Internal use: Map internal to external Stream Source IDs */
	@GsonAnnoExclude
	private @NonNull Map<@NonNull Integer, @NonNull String> internalMapStreamSourceIdIntToExt;
	/** Internal use: Map external to internal Stream Source IDs */
	@SuppressWarnings("FieldMayBeFinal")
	@GsonAnnoExclude
	private @NonNull Map<@NonNull String, @NonNull Integer> internalMapStreamSourceIdExtToInt;

	/**
	 * Constructor.
	 */
	public RtspConfig() {
		this.serverTcpPort = RtspConstants.SERVER_RTSP_TCP_PORT;
		this.streamSources = new HashMap<>();
		this.inputSources = new HashMap<>();
		this.dataDir = "";
		this.debugPrintSDP = false;
		this.debugRewindMediaFiles = false;

		//noinspection DataFlowIssue
		this.internalStreamSources = null;
		this.internalMapStreamSourceIdIntToExt = new HashMap<>();
		this.internalMapStreamSourceIdExtToInt = new HashMap<>();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the RTSP Server TCP port.
	 * @return RTSP Server TCP port
	 */
	public int getServerTcpPort() {
		checkPostProcessed();
		return serverTcpPort;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get a list of all Stream Source IDs.
	 * @return Stream Source IDs
	 */
	public @NonNull List<@NonNull Integer> getStreamSourceIds() {
		checkPostProcessed();
		return List.copyOf(internalStreamSources.keySet().stream().sorted().toList());
	}

	/**
	 * Get Stream Source by Stream Source ID.
	 * @param streamSourceId Stream Source ID
	 * @return Stream Source
	 */
	public Optional<RtspStreamSource> getStreamSourceObj(int streamSourceId) {
		checkPostProcessed();
		//noinspection OptionalOfNullableMisuse,DataFlowIssue
		return Optional.ofNullable(internalStreamSources.getOrDefault(streamSourceId, null));
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get a list of all Input Source IDs.
	 * @return Input Source IDs
	 */
	public @NonNull List<@NonNull String> getInputSourceIds() {
		checkPostProcessed();
		return List.copyOf(inputSources.keySet().stream().sorted().toList());
	}

	/**
	 * Get Input Source by Input Source ID.
	 * @param inputSourceId Input Source ID
	 * @return Input Source
	 */
	public Optional<RtspInputSource> getInputSourceObj(String inputSourceId) {
		checkPostProcessed();
		//noinspection OptionalOfNullableMisuse,DataFlowIssue
		return Optional.ofNullable(inputSources.getOrDefault(inputSourceId, null));
	}

	/**
	 * Get Input Source ID for a given Stream Source ID.
	 * @param streamSourceId Stream Source ID
	 * @return Input Source ID
	 */
	public Optional<String> getInputSourceIdForStreamSourceId(int streamSourceId) {
		checkPostProcessed();
		return inputSources.entrySet().stream()
				.filter(e -> e.getValue().getStreamSourceIds().contains(streamSourceId))
				.findFirst()
				.map(Map.Entry::getKey);
	}

	/**
	 * Get the first video Stream Source for the Input Source.
	 * @param inputSourceId Input Source ID
	 * @return Stream Source
	 */
	@SuppressWarnings("unused")
	public Optional<RtspStreamSource> getInputSourcesFirstVideoStreamSourceObj(String inputSourceId) {
		checkPostProcessed();
		return getInputSourcesFirstOfKindStreamSourceObj(inputSourceId, true);
	}

	/**
	 * Get the first audio Stream Source for the Input Source.
	 * @param inputSourceId Input Source ID
	 * @return Stream Source
	 */
	@SuppressWarnings("unused")
	public Optional<RtspStreamSource> getInputSourcesFirstAudioStreamSourceObj(String inputSourceId) {
		checkPostProcessed();
		return getInputSourcesFirstOfKindStreamSourceObj(inputSourceId, false);
	}

	/**
	 * Get the first audio or video Stream Source for the Input Source.
	 * @param inputSourceId Input Source ID
	 * @param isVideo Get video Stream Source if true, audio Stream Source otherwise
	 * @return Stream Source
	 */
	public Optional<RtspStreamSource> getInputSourcesFirstOfKindStreamSourceObj(String inputSourceId, boolean isVideo) {
		checkPostProcessed();
		//
		Optional<RtspInputSource> optInputSource = getInputSourceObj(inputSourceId);
		if (optInputSource.isEmpty()) {
			return Optional.empty();
		}
		for (int tmpSsId : optInputSource.get().getStreamSourceIds()) {
			Optional<RtspStreamSource> optStreamSource = getStreamSourceObj(tmpSsId);
			if (optStreamSource.isEmpty()) {
				continue;
			}
			if ((isVideo && optStreamSource.get().getCodec().isVideo()) ||
					(! isVideo && optStreamSource.get().getCodec().isAudio())) {
				return optStreamSource;
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the data directory as a string (could be a relative or absolute path).
	 * @return Data directory
	 */
	public @NonNull String getDataDirAsString() {
		checkPostProcessed();
		return dataDir;
	}

	/**
	 * Get the data directory as an absolute path.
	 * @return Data directory
	 */
	public @NonNull Path getDataDirAsPath() {
		checkPostProcessed();
		return Path.of(dataDir).toAbsolutePath();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get if printing the SDP description is enabled for debugging.
	 * @return True if printing the SDP description is enabled, false otherwise
	 */
	public boolean getIsDebugPrintSDP() {
		checkPostProcessed();
		return debugPrintSDP;
	}

	/**
	 * Get if rewinding the media files is enabled for debugging.
	 * @return True if rewinding the media files is enabled, false otherwise
	 */
	public boolean getIsDebugRewindMediaFiles() {
		checkPostProcessed();
		return debugRewindMediaFiles;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration by setting record IDs
	 */
	public void postProcess() throws ConfigInvalidException {
		//noinspection ConstantValue
		if (internalStreamSources == null) {
			createInternalStreamSourcesMap();
		}
		//
		internalHasBeenPostProcessed = true;
		//
		List<Integer> tmpSsIdList = getStreamSourceIds();
		for (int tmpSsId : tmpSsIdList) {
			RtspStreamSource tmpSsObj = getStreamSourceObj(tmpSsId).orElseThrow();
			tmpSsObj.setId(tmpSsId);
			tmpSsObj.postProcess(getDataDirAsPath());
		}
		//
		List<String> tmpIsIdList = getInputSourceIds();
		for (String tmpIsId : tmpIsIdList) {
			RtspInputSource tmpIsObj = getInputSourceObj(tmpIsId).orElseThrow();
			tmpIsObj.setId(tmpIsId);
			tmpIsObj.postProcess(internalMapStreamSourceIdExtToInt);
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	public void validate() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		if (serverTcpPort <= 0 || serverTcpPort > 65535) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP Server TCP port: " + serverTcpPort);
		}
		//
		//noinspection ConstantValue
		if (dataDir == null || dataDir.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty value for Data directory");
		}
		if (! getDataDirAsPath().toFile().isDirectory()) {
			throw new ConfigInvalidException(FNC_NAME + ": Data directory is not a valid directory: '" +
					getDataDirAsString() + "'");
		}
		//
		List<Integer> tmpSsIdList = getStreamSourceIds();
		if (tmpSsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Stream Sources found in configuration");
		}
		for (int tmpSsId : tmpSsIdList) {
			RtspStreamSource tmpSsObj = getStreamSourceObj(tmpSsId).orElseThrow();
			tmpSsObj.validate(internalMapStreamSourceIdIntToExt);
		}
		//
		List<String> tmpIsIdList = getInputSourceIds();
		if (tmpIsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Input Sources found in configuration");
		}
		for (String tmpIsId : tmpIsIdList) {
			RtspInputSource tmpIsObj = getInputSourceObj(tmpIsId).orElseThrow();
			tmpIsObj.validate(internalStreamSources, internalMapStreamSourceIdIntToExt);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Configuration has not been post-processed yet");
		}
	}

	private void createInternalStreamSourcesMap() {
		internalStreamSources = new HashMap<>();
		internalMapStreamSourceIdIntToExt = new HashMap<>();
		//noinspection ConstantValue
		if (streamSources == null) {
			return;
		}

		int idCounter = 0;
		for (Map.Entry<String, RtspStreamSource> entry : streamSources.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			internalStreamSources.put(idCounter, entry.getValue());
			internalMapStreamSourceIdIntToExt.put(idCounter, entry.getKey());
			internalMapStreamSourceIdExtToInt.put(entry.getKey(), idCounter++);
		}
	}

}
