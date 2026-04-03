package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * RTSP configuration.
 */
public class RtspConfig {

	private static class RtspsSslServerKey {
		/** Certificate */
		@Expose
		private final @NonNull String certificate;
		/** Private Key (PKCS#8 PEM) */
		@Expose
		private final @NonNull String key;
		/** Certificate Authority -- optional */
		@Expose
		private final @NonNull String ca;

		public RtspsSslServerKey() {
			this.certificate = "";
			this.key = "";
			this.ca = "";
		}
	}

	/** RTSP Server TCP port (without SSL/TLS) -- use -1 to disable */
	@Expose
	private final int serverTcpPortRtsp;
	/** RTSPS Server TCP port (with SSL/TLS) -- use -1 to disable */
	@Expose
	private final int serverTcpPortRtsps;
	/** RTSPS Server SSL certificate and key */
	@Expose
	private final @NonNull RtspsSslServerKey rtspsServerSslKey;
	/** Map of Users (the map keys are unique usernames) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> users;
	/** Map of MQ Server SSL Certificates (the map keys are unique host-port combinations) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> mqServerSslCertificates;
	/** Map of Stream Sources (the map keys are unique Stream Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspStreamSource> streamSources;
	/** Map of Input Sources (the map keys are unique Input Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspInputSource> inputSources;
	/** Directory where the media files are stored */
	@Expose
	private @NonNull String dataDir;
	/** Debugging: print the RTSP messages that have been received from the client? */
	@Expose
	private final boolean debugPrintRtspRcvd;
	/** Debugging: print the RTSP messages that have been sent to the client? */
	@Expose
	private final boolean debugPrintRtspSent;
	/** Debugging: print the RTSP SDP description that has been sent to the client? */
	@Expose
	private final boolean debugPrintRtspSdpSent;
	/** Debugging: rewind the media files? */
	@Expose
	private final boolean debugRewindMediaFiles;
	/** Debugging: disable UDP transport? */
	@Expose
	private boolean debugDisableTransportUdp;
	/** Log level (INFO, DEBUG, WARN, ERROR) */
	@Expose
	private @NonNull String logLevel;

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
	@GsonAnnoExclude
	private @Nullable RtxpLogLevel internalLogLevel;

	/**
	 * Constructor.
	 */
	public RtspConfig() {
		this.serverTcpPortRtsp = RtspConstants.SERVER_RTSP_TCP_PORT;
		this.serverTcpPortRtsps = RtspConstants.SERVER_RTSPS_TCP_PORT;
		this.rtspsServerSslKey = new RtspsSslServerKey();
		this.users = new HashMap<>();
		this.mqServerSslCertificates = new HashMap<>();
		this.streamSources = new HashMap<>();
		this.inputSources = new HashMap<>();
		this.dataDir = "";
		this.debugPrintRtspRcvd = false;
		this.debugPrintRtspSent = false;
		this.debugPrintRtspSdpSent = false;
		this.debugRewindMediaFiles = false;
		this.debugDisableTransportUdp = false;
		this.logLevel = RtxpLogLevel.INFO.name();

		//noinspection DataFlowIssue
		this.internalStreamSources = null;
		this.internalMapStreamSourceIdIntToExt = new HashMap<>();
		this.internalMapStreamSourceIdExtToInt = new HashMap<>();
		this.internalLogLevel = null;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the RTSP Server TCP port (without SSL/TLS).
	 * @return RTSP Server TCP port
	 */
	public int getServerTcpPortRtsp() {
		checkPostProcessed();
		return serverTcpPortRtsp;
	}

	/**
	 * Get the RTSPS Server TCP port (with SSL/TLS).
	 * @return RTSPS Server TCP port
	 */
	public int getServerTcpPortRtsps() {
		checkPostProcessed();
		return serverTcpPortRtsps;
	}

	/**
	 * Get the path to the RTSPS SSL Certificate file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL certificate file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslCertPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL Certificate file path", rtspsServerSslKey.certificate);
	}

	/**
	 * Get the path to the RTSPS SSL Private Key file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL certificate file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslKeyPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL Private Key file path", rtspsServerSslKey.key);
	}

	/**
	 * Get the path to the RTSPS SSL CA file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL CA file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslCaPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL CA file path", rtspsServerSslKey.ca);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the user password for the given username.
	 * @param username Username
	 * @return User password
	 */
	public Optional<String> getUserPassword(@NonNull String username) {
		checkPostProcessed();
		return Optional.ofNullable(users.get(username));
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the path to the SSL Certificate file for the given host and port.
	 * @param mqServerUri URI of the MQ server
	 * @return Path to the file
	 * @throws ConfigInvalidException If the file is set in the config but the file could not be found
	 */
	public Optional<String> getMqServerSslCertificatePath(@NonNull URI mqServerUri) throws ConfigInvalidException {
		return getMqServerSslCertificatePath(mqServerUri.getHost() + ":" + mqServerUri.getPort());
	}

	/**
	 * Get the path to the SSL Certificate file for the given host and port.
	 * @param hostAndPort Host and port (e.g. 'example.com:443' or '192.168.3.4:8976')
	 * @return Path to the file
	 * @throws ConfigInvalidException If the file is set in the config but the file could not be found
	 */
	public Optional<String> getMqServerSslCertificatePath(@NonNull String hostAndPort) throws ConfigInvalidException {
		checkPostProcessed();
		if (hostAndPort.isBlank()) {
			return Optional.empty();
		}
		URI tmpUri = URI.create((hostAndPort.startsWith("https://") ? "" : "https://") + hostAndPort);
		String tmpHost = tmpUri.getHost();
		int tmpPort = (tmpUri.getPort() == -1 ? 443 : tmpUri.getPort());
		String tmpSearch1 = tmpHost + ":" + tmpPort;
		String tmpPathStr = null;
		if (mqServerSslCertificates.containsKey(tmpSearch1)) {
			tmpPathStr = mqServerSslCertificates.get(tmpSearch1);
		}
		if (tmpPathStr == null && mqServerSslCertificates.containsKey(tmpHost)) {
			tmpPathStr = mqServerSslCertificates.get(tmpHost);
		}
		return getAbsoluteFilePath("Invalid MQ SSL Certificate file path for host '" + tmpSearch1 + "'", tmpPathStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get a list of all Stream Source IDs.
	 * @return Stream Source IDs
	 */
	public @NonNull List<@NonNull Integer> getStreamSourceIds() {
		checkPostProcessed();
		return List.copyOf(
				internalStreamSources.keySet().stream().sorted().toList()
			);
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
		return List.copyOf(
				inputSources.keySet().stream().sorted().toList()
			);
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
			if (optStreamSource.isEmpty() || ! optStreamSource.get().getEnabled()) {
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
	 * Get if printing the incoming RTSP messages is enabled for debugging.
	 * @return True if printing the incoming RTSP messages is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspRcvd() {
		checkPostProcessed();
		return debugPrintRtspRcvd;
	}

	/**
	 * Get if printing the outgoing RTSP messages is enabled for debugging.
	 * @return True if printing the outgoing RTSP messages is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSent() {
		checkPostProcessed();
		return debugPrintRtspSent;
	}

	/**
	 * Get if printing the SDP description is enabled for debugging.
	 * @return True if printing the SDP description is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSdpSent() {
		checkPostProcessed();
		return debugPrintRtspSdpSent;
	}

	/**
	 * Get if rewinding the media files is enabled for debugging.
	 * @return True if rewinding the media files is enabled, false otherwise
	 */
	public boolean getIsDebugRewindMediaFiles() {
		checkPostProcessed();
		return debugRewindMediaFiles;
	}

	/**
	 * Get if UDP transport is disabled for debugging.
	 * @return True if UDP transport is disabled, false otherwise
	 */
	public boolean getIsDebugDisableTransportUdp() {
		checkPostProcessed();
		return debugDisableTransportUdp;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtxpLogLevel getLogLevel() {
		checkPostProcessed();
		//noinspection DataFlowIssue
		return internalLogLevel;
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
		//noinspection ConstantValue
		if (logLevel == null || logLevel.isBlank()) {
			logLevel = RtxpLogLevel.INFO.name();
		} else if (! RtxpLogLevel.isValid(logLevel)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'logLevel': '" + logLevel + "'");
		}
		internalLogLevel = RtxpLogLevel.of(logLevel);
		//
		if (serverTcpPortRtsp == 0 || serverTcpPortRtsp > 65535) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP Server TCP port: " + serverTcpPortRtsp);
		}
		if (serverTcpPortRtsps == 0 || serverTcpPortRtsps > 65535) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSPS Server TCP port: " + serverTcpPortRtsps);
		}
		if (serverTcpPortRtsp < 0 && serverTcpPortRtsps < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Neither RTSP nor RTSPS Server TCP port set");
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
		if (serverTcpPortRtsps > 0) {
			//noinspection ConstantValue
			if (rtspsServerSslKey == null) {
				throw new ConfigInvalidException(FNC_NAME + ": Empty value for rtspsServerSslKey");
			}
			checkFileExists("rtspsServerSslKey.certificate", false, rtspsServerSslKey.certificate);
			checkFileExists("rtspsServerSslKey.key", false, rtspsServerSslKey.key);
			checkFileExists("rtspsServerSslKey.ca", true, rtspsServerSslKey.ca);
		}

		//
		for (Map.Entry<@NonNull String, @NonNull String> entry : mqServerSslCertificates.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			getMqServerSslCertificatePath(entry.getKey());
		}
		//
		List<Integer> tmpSsIdList = getStreamSourceIds();
		if (tmpSsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Stream Sources found in configuration");
		}
		for (int tmpSsId : tmpSsIdList) {
			RtspStreamSource tmpSsObj = getStreamSourceObj(tmpSsId).orElseThrow();
			tmpSsObj.validate(internalMapStreamSourceIdIntToExt);
			//
			if (tmpSsObj.getIsSourceFromMq() && tmpSsObj.getEnabled()) {
				Optional<String> tmpCert = getMqServerSslCertificatePath(tmpSsObj.getInputUri());
				if (tmpCert.isEmpty()) {
					String tmpExtSsId = internalMapStreamSourceIdIntToExt.get(tmpSsId);
					System.err.println(FNC_NAME + ": Warning: Stream Source '" + tmpExtSsId + "' has no SSL certificate");
				}
			}
		}
		//
		List<String> tmpIsIdList = getInputSourceIds();
		if (tmpIsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Input Sources found in configuration");
		}
		for (String tmpIsId : tmpIsIdList) {
			RtspInputSource tmpIsObj = getInputSourceObj(tmpIsId).orElseThrow();
			if (tmpIsObj.getEnabled()) {
				tmpIsObj.validate(internalStreamSources, internalMapStreamSourceIdIntToExt);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the absolute path to the file.
	 * @param errMsg Error message to use if the file is not found
	 * @param filename Filename to get the absolute path for
	 * @return Path to the file
	 * @throws ConfigInvalidException If the file is set in the config but the file could not be found
	 */
	private Optional<String> getAbsoluteFilePath(@NonNull String errMsg, @Nullable String filename) throws ConfigInvalidException {
		checkPostProcessed();
		if (filename == null || filename.isBlank()) {
			return Optional.empty();
		}
		String resStr = RtspStreamSource.dataFilenameToAbsolutePath(getDataDirAsPath(), filename);
		Path tmpPathObj = Paths.get(resStr);
		if (! tmpPathObj.toFile().exists()) {
			throw new ConfigInvalidException(errMsg + ": file '" + resStr + "' not found");
		}
		return Optional.of(resStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkFileExists(
				@NonNull String desc,
				boolean canBeEmpty,
				@Nullable String filename
			) throws ConfigInvalidException {
		if (canBeEmpty && (filename == null || filename.isBlank())) {
			return;
		}
		if (filename == null || filename.isBlank()) {
			throw new ConfigInvalidException("Empty value for '" + desc + "'");
		}
		getAbsoluteFilePath("Invalid file path for '" + desc + "'", filename).orElseThrow();
	}

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
