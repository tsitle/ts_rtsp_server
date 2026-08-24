package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main RTSP server configuration.
 */
public final class RtspSrvConfigMain extends RtspSrvConfigFileBase {

	static final int RTSP_THREADS_PLAY_DEFAULT = 20;  // one thread per client session
	static final int RTSP_THREADS_TCI_DEFAULT = 20;  // one thread per client connection
	static final int MQ_THREADS_EXT_DEFAULT = 20;  // one thread per external MQ
	static final int DMX_RTSP_THREADS_DEFAULT = 20;  // one thread per RTSP input source
	static final int DMX_AF_THREADS_DEFAULT = 20;  // one thread per AF input source

	static final int SERVER_USERNAME_LENGTH_MAX = 64;
	static final int SERVER_USERPASS_LENGTH_MIN = 8;
	static final int SERVER_USERPASS_LENGTH_MAX = 64;

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
		/** Maxmimum number of threads for Playback */
		@Expose
		private final int threadsMaximumPlay;
		/** Maxmimum number of threads for incoming client TCP connections */
		@Expose
		private final int threadsMaximumTci;
		/** Maxmimum number of threads for Message Queues */
		@Expose
		private final int threadsMaximumMq;
		/** Maxmimum number of threads for Demux RTSP */
		@Expose
		private final int threadsMaximumDmxRtsp;
		/** Maxmimum number of threads for Demux AF */
		@Expose
		private final int threadsMaximumDmxAf;

		public SectionServer() {
			this.tcpPortRtsp = ProUri.RTSP_TCP_PORT_DEFAULT;
			this.tcpPortRtsps = ProUri.RTSPS_TCP_PORT_DEFAULT;
			this.dataDir = "";
			this.sslCertificate = "";
			this.sslKey = "";
			this.sslCa = "";
			this.threadsMaximumPlay = RTSP_THREADS_PLAY_DEFAULT;
			this.threadsMaximumTci = RTSP_THREADS_TCI_DEFAULT;
			this.threadsMaximumMq = MQ_THREADS_EXT_DEFAULT;
			this.threadsMaximumDmxRtsp = DMX_RTSP_THREADS_DEFAULT;
			this.threadsMaximumDmxAf = DMX_AF_THREADS_DEFAULT;
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
		/** Log level (INFO, DEBUG, WARN, ERROR) */
		@Expose
		private @NonNull String logLevel;
		/** Log outputs */
		@Expose
		private @NonNull SectionLogOutputs outputs;

		@GsonAnnoExclude
		private @Nullable RtxpLogLevel internalLogLevel;

		public SectionLogging() {
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

	private static class SectionDebugging {
		/** Debugging: print the RTSP messages that have been received from the client? */
		@Expose
		private final boolean debugPrintRtspRcvd;
		/** Debugging: print the RTSP messages that have been sent to the client? */
		@Expose
		private final boolean debugPrintRtspSent;
		/** Debugging: print the RTSP SDP description that has been sent to the client? */
		@Expose
		private final boolean debugPrintRtspSdpSent;
		/** Debugging: disable UDP transport? */
		@Expose
		private boolean debugDisableTransportUdp;

		public SectionDebugging() {
			this.debugPrintRtspRcvd = false;
			this.debugPrintRtspSent = false;
			this.debugPrintRtspSdpSent = false;
			this.debugDisableTransportUdp = false;
		}
	}

	/** Main configuration */
	@Expose
	private final @NonNull SectionServer server;
	/** Logging configuration */
	@Expose
	private final @NonNull SectionLogging logging;
	/** Debugging configuration */
	@Expose
	private final @NonNull SectionDebugging debugging;
	/** Map of User Accounts (the map keys are unique usernames) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> userAccounts;
	/** Map of User Account Groups (the map keys are unique group names) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull Set<@NonNull String>> userAccountGroups;
	/** Map of Remote MQ Server SSL Certificates (the map keys are unique host-port combinations) */
	@Expose
	private @NonNull Map<@NonNull String, @NonNull String> remoteMqServerSslCertificates;
	/** List of Stream Config Directories */
	@Expose
	private @NonNull Set<@NonNull String> streamConfigDirectories;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	/**
	 * Constructor.
	 */
	public RtspSrvConfigMain() {
		this.server = new SectionServer();
		this.logging = new SectionLogging();
		this.debugging = new SectionDebugging();
		this.userAccounts = new HashMap<>();
		this.userAccountGroups = new HashMap<>();
		this.remoteMqServerSslCertificates = new HashMap<>();
		this.streamConfigDirectories = new HashSet<>();

		this.internalHasBeenPostProcessed = false;
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
		return getAbsoluteFilePathInDataDir("Invalid RTSPS Server SSL Certificate file path", server.sslCertificate);
	}

	/**
	 * Get the path to the RTSPS SSL Private Key file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL certificate file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslKeyPath() throws ConfigInvalidException {
		return getAbsoluteFilePathInDataDir("Invalid RTSPS Server SSL Private Key file path", server.sslKey);
	}

	/**
	 * Get the path to the RTSPS SSL CA file.
	 * @return Path to the file
	 * @throws ConfigInvalidException If the SSL CA file is set in the config but the file could not be found
	 */
	public Optional<String> getRtspsSslCaPath() throws ConfigInvalidException {
		return getAbsoluteFilePathInDataDir("Invalid RTSPS Server SSL CA file path", server.sslCa);
	}

	public int getThreadsMaximumPlay() {
		return server.threadsMaximumPlay;
	}

	public int getThreadsMaximumTci() {
		return server.threadsMaximumTci;
	}

	public int getThreadsMaximumMq() {
		return server.threadsMaximumMq;
	}

	public int getThreadsMaximumDmxRtsp() {
		return server.threadsMaximumDmxRtsp;
	}

	public int getThreadsMaximumDmxAf() {
		return server.threadsMaximumDmxAf;
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
		return debugging.debugPrintRtspRcvd;
	}

	/**
	 * Get if printing the outgoing RTSP messages is enabled for debugging.
	 * @return True if printing the outgoing RTSP messages is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSent() {
		checkPostProcessed();
		return debugging.debugPrintRtspSent;
	}

	/**
	 * Get if printing the SDP description is enabled for debugging.
	 * @return True if printing the SDP description is enabled, false otherwise
	 */
	public boolean getIsDebugPrintRtspSdpSent() {
		checkPostProcessed();
		return debugging.debugPrintRtspSdpSent;
	}

	/**
	 * Get if UDP transport is disabled for debugging.
	 * @return True if UDP transport is disabled, false otherwise
	 */
	public boolean getIsDebugDisableTransportUdp() {
		checkPostProcessed();
		return debugging.debugDisableTransportUdp;
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

	public @NonNull Set<@NonNull String> getUserAccountGroupIds() {
		checkPostProcessed();
		return Set.copyOf(userAccountGroups.keySet());
	}

	/**
	 * Get usernames that are allowed to access an RTSP Input Source.
	 * @param allowedUserAccountGroups Allowed User Account Groups for the Input Source
	 * @return Usernames that are allowed to access an RTSP Input Source
	 */
	public @NonNull Set<String> getUsersAllowedToAccessInputSource(@NonNull Set<String> allowedUserAccountGroups) {
		checkPostProcessed();
		//
		Set<String> resSet = new HashSet<>();
		for (String tmpUag : allowedUserAccountGroups) {
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
	public Optional<String> getMqServerSslCertificatePath(@NonNull ProUri mqServerUri) throws ConfigInvalidException {
		String tmpHost = mqServerUri.getHost().orElse("");
		return getMqServerSslCertificatePath(tmpHost + ":" + mqServerUri.getPortOrDefault().orElse(0));
	}

	/**
	 * Get the path to the SSL Certificate file for the given host and port.
	 * @param hostAndPort Host and port (e.g. 'example.com:443' or '192.168.3.4:8976')
	 * @return Path to the file
	 * @throws ConfigInvalidException If the file is set in the config but the file could not be found
	 */
	public Optional<String> getMqServerSslCertificatePath(@NonNull String hostAndPort) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".getMqServerSslCertificatePath()";

		checkPostProcessed();
		if (hostAndPort.isBlank()) {
			return Optional.empty();
		}
		String tmpHostStr;
		String tmpSearch1;
		try {
			String tmpUrlStr = (hostAndPort.startsWith("https://") ? "" : "https://") + hostAndPort;
			ProUri tmpProUri = ProUri.of(tmpUrlStr);
			tmpHostStr = tmpProUri.getHost().orElseThrow();
			tmpSearch1 = tmpHostStr + ":" + tmpProUri.getPortOrDefault().orElseThrow();
		} catch (ProUriInvalidUriException e) {
			throw new ConfigInvalidException(FNC_NAME + ": could not parse hostAndPort '" + hostAndPort + "': " + e.getMessage());
		}

		String tmpPathStr = null;
		if (remoteMqServerSslCertificates.containsKey(tmpSearch1)) {
			tmpPathStr = remoteMqServerSslCertificates.get(tmpSearch1);
		}
		if (tmpPathStr == null && remoteMqServerSslCertificates.containsKey(tmpHostStr)) {
			tmpPathStr = remoteMqServerSslCertificates.get(tmpHostStr);
		}
		return getAbsoluteFilePathInDataDir(
				"Invalid MQ SSL Certificate file path for host '" + tmpSearch1 + "'",
				tmpPathStr
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Set<@NonNull String> getStreamConfigDirs() {
		checkPostProcessed();
		return Set.copyOf(streamConfigDirectories);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	void postProcess() throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".postProcess()";

		//noinspection ConstantValue
		if (server == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'server'"); }
		//noinspection ConstantValue
		if (logging == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'logging'"); }
		//noinspection ConstantValue
		if (logging.outputs == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'logging.outputs'"); }

		//
		//noinspection ConstantValue
		if (userAccounts == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'userAccounts'"); }
		userAccounts = RewriteMapStringXxxHelper.removeNullAndBlank(userAccounts, true);

		//
		//noinspection ConstantValue
		if (userAccountGroups == null) { throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'userAccountGroups'"); }
		userAccountGroups = RewriteMapStringXxxHelper.removeNullAndBlank(userAccountGroups, true);
		Map<@NonNull String, @NonNull Set<@NonNull String>> tmpNewUags = new HashMap<>();
		for (Map.Entry<@NonNull String, @NonNull Set<String>> entry : userAccountGroups.entrySet()) {
			Set<@NonNull String> tmpNewUagMembers = RewriteSetStringHelper.removeNullAndBlank(entry.getValue(), true);
			tmpNewUags.put(entry.getKey(), tmpNewUagMembers);
		}
		userAccountGroups = tmpNewUags;

		//noinspection ConstantValue
		if (remoteMqServerSslCertificates == null) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty value for 'remoteMqServerSslCertificates'");
		}

		//
		streamConfigDirectories = RewriteSetStringHelper.removeNullAndBlank(streamConfigDirectories, false);

		//
		internalHasBeenPostProcessed = true;
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String mainConfigFileDirectory) throws ConfigInvalidException {
		checkPostProcessed();

		//
		validateSectionServer();
		validateSectionLogging();
		validateSectionDebugging();
		validateSectionsUserAcc();
		validateSectionUag();
		validateSectionMqSslCerts();
		validateSectionScd(mainConfigFileDirectory);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the absolute path to a file in the 'data' directory.
	 * @param errMsg Error message to use if the file is not found
	 * @param filename Filename to get the absolute path for
	 * @return Path to the file
	 * @throws ConfigInvalidException If the file could not be found
	 */
	private Optional<String> getAbsoluteFilePathInDataDir(@NonNull String errMsg, @Nullable String filename)
			throws ConfigInvalidException {
		checkPostProcessed();
		if (filename == null || filename.isBlank()) {
			return Optional.empty();
		}
		String resStr = DataDirHelper.dataFilenameToAbsolutePath(getDataDirAsPath(), filename);
		Path tmpPathObj = Paths.get(resStr);
		if (! tmpPathObj.toFile().exists()) {
			throw new ConfigInvalidException(errMsg + ": file '" + resStr + "' not found");
		}
		return Optional.of(resStr);
	}

	private void checkFileExistsInDataDir(
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
		getAbsoluteFilePathInDataDir("Invalid File Path for '" + desc + "'", filename).orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
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
			checkFileExistsInDataDir("server.sslCertificate", false, server.sslCertificate);
			checkFileExistsInDataDir("server.sslKey", false, server.sslKey);
			checkFileExistsInDataDir("server.sslCa", true, server.sslCa);
		}

		//
		if (server.threadsMaximumPlay < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'threadsMaximumPlay': " + server.threadsMaximumPlay);
		}
		if (server.threadsMaximumTci < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'threadsMaximumTcp': " + server.threadsMaximumTci);
		}
		if (server.threadsMaximumMq < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'threadsMaximumMq': " + server.threadsMaximumMq);
		}
		if (server.threadsMaximumDmxRtsp < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'threadsMaximumDmxRtsp': " + server.threadsMaximumDmxRtsp);
		}
		if (server.threadsMaximumDmxAf < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid value for 'threadsMaximumDmxAf': " + server.threadsMaximumDmxAf);
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

	private void validateSectionDebugging() {
		// nothing to do
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

	private void validateSectionScd(@NonNull String mainConfigFileDirectory) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validateSectionScd()";

		Set<String> tmpScd = new HashSet<>();
		for (String entry : streamConfigDirectories) {
			try {
				Path tmpFullPath = Path.of(mainConfigFileDirectory).resolve(entry);
				if (! tmpFullPath.toFile().isDirectory()) {
					throw new ConfigInvalidException(FNC_NAME + ": 'streamConfigDirectories' entry '" + entry + "' is not a directory: '" +
							tmpFullPath + "'");
				}
				tmpScd.add(tmpFullPath.toFile().getAbsolutePath());
			} catch (InvalidPathException e) {
				throw new ConfigInvalidException(FNC_NAME + ": 'streamConfigDirectories' entry '" + entry + "' is not a valid path");
			}
		}
		streamConfigDirectories = tmpScd;
	}

	// -------------------------------------------------

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

}
