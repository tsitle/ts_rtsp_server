package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspServerConstants;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * RTSP server configuration.
 */
public class RtspConfig {

	final int SERVER_USERNAME_LENGTH_MAX = 64;
	final int SERVER_USERPASS_LENGTH_MIN = 8;
	final int SERVER_USERPASS_LENGTH_MAX = 64;

	private static class SectionServer {
		/** RTSP Server TCP port (without SSL/TLS) -- use -1 to disable */
		@Expose
		private final int tcpPortRtsp;
		/** RTSPS Server TCP port (with SSL/TLS) -- use -1 to disable */
		@Expose
		private final int tcpPortRtsps;
		/** Directory where the media files are stored */
		@Expose
		private @NonNull String dataDir;
		/** SSL Certificate */
		@Expose
		private final @NonNull String sslCertificate;
		/** SSL Private Key (PKCS#8 PEM) */
		@Expose
		private final @NonNull String sslKey;
		/** SSL Certificate Authority -- optional */
		@Expose
		private final @NonNull String sslCa;

		public SectionServer() {
			this.tcpPortRtsp = RtspServerConstants.SERVER_RTSP_TCP_PORT;
			this.tcpPortRtsps = RtspServerConstants.SERVER_RTSPS_TCP_PORT;
			this.dataDir = "";
			this.sslCertificate = "";
			this.sslKey = "";
			this.sslCa = "";
		}
	}

	private static class SectionLogOutputs {
		/** Enable log output to a file? */
		@Expose
		private final boolean enableFile;
		/** Enable log output to the console? */
		@Expose
		private final boolean enableConsole;
		/** Filename for logging to a file */
		@Expose
		private @NonNull String filename;

		public SectionLogOutputs() {
			this.enableFile = false;
			this.enableConsole = false;
			this.filename = "";
		}

		public boolean isEnableFile() {
			return enableFile;
		}

		public boolean isEnableConsole() {
			return enableConsole;
		}

		public @NonNull String getFilename() {
			//noinspection ConstantValue
			return (filename != null ? filename : "");
		}
	}

	private static class SectionLogging {
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
		/** Log outputs */
		@Expose
		private @NonNull SectionLogOutputs outputs;

		@GsonAnnoExclude
		private @Nullable RtxpLogLevel internalLogLevel;

		public SectionLogging() {
			this.debugPrintRtspRcvd = false;
			this.debugPrintRtspSent = false;
			this.debugPrintRtspSdpSent = false;
			this.debugRewindMediaFiles = false;
			this.debugDisableTransportUdp = false;
			this.logLevel = RtxpLogLevel.INFO.name();
			this.outputs = new SectionLogOutputs();

			this.internalLogLevel = null;
		}

		void setLogLevelString(@NonNull String logLevel) {
			this.logLevel = logLevel;
		}

		@SuppressWarnings("NullableProblems")
		@NonNull RtxpLogLevel getInternalLogLevel() {
			//noinspection DataFlowIssue
			return internalLogLevel;
		}

		void setInternalLogLevel(@NonNull RtxpLogLevel internalLogLevel) {
			this.internalLogLevel = internalLogLevel;
		}
	}

	/** Main configuration */
	@Expose
	private final @NonNull SectionServer server;
	/** Logging configuration */
	@Expose
	private final @NonNull SectionLogging logging;
	/** Map of User Accounts (the map keys are unique usernames) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> userAccounts;
	/** Map of User Account Groups (the map keys are unique group names) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull Set<@NonNull String>> userAccountGroups;
	/** Map of Remote MQ Server SSL Certificates (the map keys are unique host-port combinations) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> remoteMqServerSslCertificates;
	/** Map of Stream Sources (the map keys are unique Stream Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspConfigStreamSource> streamSources;
	/** Map of Input Sources (the map keys are unique Input Source identifiers) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull RtspConfigInputSource> inputSources;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Map of Stream Sources (the map keys are unique Stream Source identifiers) */
	@GsonAnnoExclude
	private @NonNull Map<@NonNull Integer, @NonNull RtspConfigStreamSource> internalStreamSources;
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
		this.server = new SectionServer();
		this.logging = new SectionLogging();
		this.userAccounts = new HashMap<>();
		this.userAccountGroups = new HashMap<>();
		this.remoteMqServerSslCertificates = new HashMap<>();
		this.streamSources = new HashMap<>();
		this.inputSources = new HashMap<>();

		//noinspection DataFlowIssue
		this.internalStreamSources = null;
		this.internalMapStreamSourceIdIntToExt = new HashMap<>();
		this.internalMapStreamSourceIdExtToInt = new HashMap<>();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the RTSP Server TCP port (without SSL/TLS).
	 * @return RTSP Server TCP port
	 */
	public int getServerTcpPortRtsp() {
		checkPostProcessed();
		return server.tcpPortRtsp;
	}

	/**
	 * Get the RTSPS Server TCP port (with SSL/TLS).
	 * @return RTSPS Server TCP port
	 */
	public int getServerTcpPortRtsps() {
		checkPostProcessed();
		return server.tcpPortRtsps;
	}

	/**
	 * Get the path to the RTSPS SSL Certificate file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL certificate file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslCertPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL Certificate file path", server.sslCertificate);
	}

	/**
	 * Get the path to the RTSPS SSL Private Key file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL certificate file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslKeyPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL Private Key file path", server.sslKey);
	}

	/**
	 * Get the path to the RTSPS SSL CA file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL CA file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslCaPath() throws ConfigInvalidException {
		return getAbsoluteFilePath("Invalid RTSPS Server SSL CA file path", server.sslCa);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the user password for the given username.
	 * @param username Username
	 * @return User password
	 */
	public Optional<String> getUserPassword(@NonNull String username) {
		checkPostProcessed();
		return Optional.ofNullable(userAccounts.get(username.toLowerCase()));
	}

	/**
	 * Get usernames that are allowed to access the given RTSP Input Source.
	 * @param idInputSource Input Source ID
	 * @return Usernames that are allowed to access the RTSP Input Source
	 */
	public @NonNull Set<String> getUsersAllowedToAccessInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		checkPostProcessed();
		//
		Optional<RtspConfigInputSource> tmpOptIsObj = getInputSourceObj(idInputSource);
		if (tmpOptIsObj.isEmpty()) {
			return Collections.emptySet();
		}
		//
		Set<String> resSet = new HashSet<>();
		for (String tmpUag : tmpOptIsObj.get().getAllowedUserAccountGroups()) {
			if (! userAccountGroups.containsKey(tmpUag)) {
				continue;
			}
			resSet.addAll(userAccountGroups.get(tmpUag));
		}
		return resSet;
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
		if (remoteMqServerSslCertificates.containsKey(tmpSearch1)) {
			tmpPathStr = remoteMqServerSslCertificates.get(tmpSearch1);
		}
		if (tmpPathStr == null && remoteMqServerSslCertificates.containsKey(tmpHost)) {
			tmpPathStr = remoteMqServerSslCertificates.get(tmpHost);
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
	public Optional<RtspConfigStreamSource> getStreamSourceObj(int streamSourceId) {
		checkPostProcessed();
		//noinspection OptionalOfNullableMisuse,DataFlowIssue
		return Optional.ofNullable(internalStreamSources.getOrDefault(streamSourceId, null));
	}

	/**
	 * Get Stream Source by Stream Source ID.
	 * @param idStreamSource Stream Source ID
	 * @return Stream Source
	 */
	public Optional<RtspConfigStreamSource> getStreamSourceObj(@NonNull RtspProtoIdStreamSource idStreamSource) {
		checkPostProcessed();
		//
		for (RtspConfigStreamSource tmpSs : internalStreamSources.values()) {
			if (tmpSs.getIdAsProtoId().equals(idStreamSource)) {
				return Optional.of(tmpSs);
			}
		}
		return Optional.empty();
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
	public Optional<RtspConfigInputSource> getInputSourceObj(@NonNull String inputSourceId) {
		checkPostProcessed();
		//noinspection OptionalOfNullableMisuse,DataFlowIssue
		return Optional.ofNullable(inputSources.getOrDefault(inputSourceId, null));
	}

	/**
	 * Get Input Source by Input Source ID.
	 * @param idInputSource Input Source ID
	 * @return Input Source
	 */
	public Optional<RtspConfigInputSource> getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
		checkPostProcessed();
		//
		for (RtspConfigInputSource tmpIs : inputSources.values()) {
			if (tmpIs.getIdAsProtoId().equals(idInputSource)) {
				return Optional.of(tmpIs);
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
		return server.dataDir;
	}

	/**
	 * Get the data directory as an absolute path.
	 * @return Data directory
	 */
	public @NonNull Path getDataDirAsPath() {
		checkPostProcessed();
		return Path.of(server.dataDir).toAbsolutePath();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get if printing the incoming RTSP messages is enabled for debugging.
	 * @return True if printing the incoming RTSP messages is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspRcvd() {
		checkPostProcessed();
		return logging.debugPrintRtspRcvd;
	}

	/**
	 * Get if printing the outgoing RTSP messages is enabled for debugging.
	 * @return True if printing the outgoing RTSP messages is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSent() {
		checkPostProcessed();
		return logging.debugPrintRtspSent;
	}

	/**
	 * Get if printing the SDP description is enabled for debugging.
	 * @return True if printing the SDP description is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSdpSent() {
		checkPostProcessed();
		return logging.debugPrintRtspSdpSent;
	}

	/**
	 * Get if rewinding the media files is enabled for debugging.
	 * @return True if rewinding the media files is enabled, false otherwise
	 */
	public boolean getIsDebugRewindMediaFiles() {
		checkPostProcessed();
		return logging.debugRewindMediaFiles;
	}

	/**
	 * Get if UDP transport is disabled for debugging.
	 * @return True if UDP transport is disabled, false otherwise
	 */
	public boolean getIsDebugDisableTransportUdp() {
		checkPostProcessed();
		return logging.debugDisableTransportUdp;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtxpLogLevel getLogLevel() {
		checkPostProcessed();
		return logging.getInternalLogLevel();
	}

	public boolean getLoggingEnabledOutputFile() {
		checkPostProcessed();
		return logging.outputs.isEnableFile();
	}

	public boolean getLoggingEnabledOutputConsole() {
		checkPostProcessed();
		return logging.outputs.isEnableConsole();
	}

	public @NonNull String getLoggingOutputFilename() {
		checkPostProcessed();
		return logging.outputs.getFilename();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration by setting record IDs
	 */
	public void postProcess() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".postProcess()";

		//noinspection ConstantValue
		if (server == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'server'"); }
		//noinspection ConstantValue
		if (logging == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'logging'"); }
		//noinspection ConstantValue
		if (logging.outputs == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'logging.outputs'"); }
		//noinspection ConstantValue
		if (userAccounts == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'userAccounts'"); }
		//noinspection ConstantValue
		if (userAccountGroups == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'userAccountGroups'"); }
		//noinspection ConstantValue
		if (remoteMqServerSslCertificates == null) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'remoteMqServerSslCertificates'");
		}
		//noinspection ConstantValue
		if (streamSources == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'streamSources'"); }
		//noinspection ConstantValue
		if (inputSources == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'inputSources'"); }

		//
		Map<@NonNull String, @NonNull String> tmpNewUserAccs = new HashMap<>();
		for (Map.Entry<@NonNull String, @NonNull String> entry : userAccounts.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			tmpNewUserAccs.put(entry.getKey().toLowerCase(), entry.getValue());
		}
		userAccounts = tmpNewUserAccs;

		//
		Map<@NonNull String, @NonNull Set<@NonNull String>> tmpNewUags = new HashMap<>();
		for (Map.Entry<@NonNull String, @NonNull Set<@NonNull String>> entry : userAccountGroups.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			Set<@NonNull String> tmpNewUagMembers = new HashSet<>();
			for (@NonNull String tmpMember : entry.getValue()) {
				//noinspection ConstantValue
				if (tmpMember == null || tmpMember.isBlank()) {
					continue;
				}
				tmpNewUagMembers.add(tmpMember.toLowerCase());
			}
			tmpNewUags.put(entry.getKey().toLowerCase(), tmpNewUagMembers);
		}
		userAccountGroups = tmpNewUags;

		//noinspection ConstantValue
		if (internalStreamSources == null) {
			createInternalStreamSourcesMap();
		}
		//
		internalHasBeenPostProcessed = true;
		//
		List<Integer> tmpSsIdList = getStreamSourceIds();
		for (int tmpSsId : tmpSsIdList) {
			RtspConfigStreamSource tmpSsObj = getStreamSourceObj(tmpSsId).orElseThrow();
			tmpSsObj.setIdAsInt(tmpSsId);
			tmpSsObj.postProcess(getDataDirAsPath());
		}
		//
		List<String> tmpIsIdList = getInputSourceIds();
		for (String tmpIsId : tmpIsIdList) {
			RtspConfigInputSource tmpIsObj = getInputSourceObj(tmpIsId).orElseThrow();
			tmpIsObj.setId(tmpIsId);
			tmpIsObj.postProcess(internalMapStreamSourceIdExtToInt);
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	public void validate() throws ConfigInvalidException {
		checkPostProcessed();

		//
		validateSectionServer();
		validateSectionLogging();
		validateSectionsUserAcc();
		validateSectionUag();
		validateSectionMqSslCerts();
		validateSectionStreamSources();
		validateSectionInputSources();
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
		String resStr = RtspConfigStreamSource.dataFilenameToAbsolutePath(getDataDirAsPath(), filename);
		Path tmpPathObj = Paths.get(resStr);
		if (! tmpPathObj.toFile().exists()) {
			throw new ConfigInvalidException(errMsg + ": file '" + resStr + "' not found");
		}
		return Optional.of(resStr);
	}

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

	private void validateSectionServer() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionServer()";

		if (server.tcpPortRtsp == 0 || server.tcpPortRtsp > 65535) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP Server TCP port: " + server.tcpPortRtsp);
		}
		if (server.tcpPortRtsps == 0 || server.tcpPortRtsps > 65535) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSPS Server TCP port: " + server.tcpPortRtsps);
		}
		if (server.tcpPortRtsp < 0 && server.tcpPortRtsps < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Neither RTSP nor RTSPS Server TCP port set");
		}
		//
		//noinspection ConstantValue
		if (server.dataDir == null || server.dataDir.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'dataDir' directory");
		}
		if (! getDataDirAsPath().toFile().isDirectory()) {
			throw new ConfigInvalidException(FNC_NAME + ": 'dataDir' is not a valid directory: '" +
					getDataDirAsString() + "'");
		}

		//
		if (server.tcpPortRtsps > 0) {
			checkFileExists("server.sslCertificate", false, server.sslCertificate);
			checkFileExists("server.sslKey", false, server.sslKey);
			checkFileExists("server.sslCa", true, server.sslCa);
		}
	}

	private void validateSectionLogging() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionLogging()";

		//noinspection ConstantValue
		if (logging.logLevel == null || logging.logLevel.isBlank()) {
			logging.setLogLevelString(RtxpLogLevel.INFO.name());
		} else if (! RtxpLogLevel.isValid(logging.logLevel)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'logLevel': '" + logging.logLevel + "'");
		}
		logging.setInternalLogLevel(RtxpLogLevel.of(logging.logLevel));

		if (getLoggingEnabledOutputFile() && getLoggingOutputFilename().isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": Logging to file is enabled but 'logging.outputs.filename' is empty");
		}
	}

	private void validateSectionsUserAcc() throws ConfigInvalidException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : userAccounts.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null) {
				continue;
			}
			validateUserOrGroupName(entry.getKey(), true);
			validateUserPassword(entry.getKey(), entry.getValue());
		}
	}

	private void validateSectionUag() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionUag()";

		for (Map.Entry<@NonNull String, @NonNull Set<@NonNull String>> entry : userAccountGroups.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null) {
				continue;
			}
			validateUserOrGroupName(entry.getKey(), false);
			//
			//noinspection ConstantValue
			if (entry.getValue() == null) {
				continue;
			}
			for (@NonNull String tmpMember : entry.getValue()) {
				//noinspection ConstantValue
				if (tmpMember == null) {
					continue;
				}
				if (! userAccounts.containsKey(tmpMember)) {
					throw new ConfigInvalidException(FNC_NAME + ": User account '" + tmpMember + "' in group '" +
							entry.getKey() + "' does not exist");
				}
			}
		}
	}

	private void validateSectionMqSslCerts() throws ConfigInvalidException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : remoteMqServerSslCertificates.entrySet()) {
			//noinspection ConstantValue
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			getMqServerSslCertificatePath(entry.getKey());
		}
	}

	private void validateSectionStreamSources() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionStreamSources()";

		List<Integer> tmpSsIdList = getStreamSourceIds();
		if (tmpSsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Stream Sources found in configuration");
		}
		for (int tmpSsId : tmpSsIdList) {
			RtspConfigStreamSource tmpSsObj = getStreamSourceObj(tmpSsId).orElseThrow();
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
	}

	private void validateSectionInputSources() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionInputSources()";

		List<String> tmpIsIdList = getInputSourceIds();
		if (tmpIsIdList.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Input Sources found in configuration");
		}
		for (String tmpIsId : tmpIsIdList) {
			RtspConfigInputSource tmpIsObj = getInputSourceObj(tmpIsId).orElseThrow();
			if (tmpIsObj.getEnabled()) {
				tmpIsObj.validate(
						internalStreamSources,
						internalMapStreamSourceIdIntToExt,
						userAccountGroups.keySet()
					);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void validateUserOrGroupName(@NonNull String username, boolean isUser) throws ConfigInvalidException {
		//noinspection ConstantValue
		if (username != null) {
			for (char c : username.toCharArray()) {
				if (! Character.isLetterOrDigit(c) && c != '_' && c != '-') {
					throw new ConfigInvalidException("Invalid " + (isUser ? "user" : "group") + " name '" + username + "': " +
							"contains invalid character '" + c + "'");
				}
			}
		}
		//noinspection ConstantValue
		if (username == null || username.isBlank() || username.length() > SERVER_USERNAME_LENGTH_MAX) {
			throw new ConfigInvalidException("Invalid " + (isUser ? "user" : "group") + " name '" + username + "': must have between 1 and " +
					SERVER_USERNAME_LENGTH_MAX + " characters");
		}
	}

	private void validateUserPassword(@NonNull String username, @NonNull String password) throws ConfigInvalidException {
		//noinspection ConstantValue
		if (password != null) {
			for (char c : password.toCharArray()) {
				if (! Character.isLetterOrDigit(c) && c != '!' && c != '*' && c != '+' && c != '-' && c != '_' && c != '.') {
					throw new ConfigInvalidException("Invalid user password for '" + username + "': " +
							"contains invalid character '" + c + "'");
				}
			}
		}
		//noinspection ConstantValue
		if (password == null ||
				password.length() < SERVER_USERPASS_LENGTH_MIN || password.length() > SERVER_USERPASS_LENGTH_MAX) {
			throw new ConfigInvalidException("Invalid user password for '" + username + "': must have between " +
					SERVER_USERPASS_LENGTH_MIN + " and " + SERVER_USERPASS_LENGTH_MAX + " characters");
		}
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
		for (Map.Entry<String, RtspConfigStreamSource> entry : streamSources.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			internalStreamSources.put(idCounter, entry.getValue());
			internalMapStreamSourceIdIntToExt.put(idCounter, entry.getKey());
			internalMapStreamSourceIdExtToInt.put(entry.getKey(), idCounter++);
		}
	}

}
