package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.config.*;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class StreamsCfgReader {

	static final String STREAMS_CFG_FILE_EXT = ".json";

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSrvConfigMain rtspSrvConfig;
	private final @NonNull Set<@NonNull String> streamsConfigDirs;

	StreamsCfgReader(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull Set<@NonNull String> streamsConfigDirs
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSrvConfig = rtspSrvConfig;
		this.streamsConfigDirs = streamsConfigDirs;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@NonNull Set<@NonNull String> getAllInputFilenames() throws IOException {
		Set<@NonNull String> resSet = new HashSet<>();
		for (String tmpScdCur : streamsConfigDirs) {
			Path tmpScdPath = Paths.get(tmpScdCur).toAbsolutePath();
			try (Stream<Path> stream = Files.list(tmpScdPath)) {
				resSet.addAll(
						stream
								.filter(Files::isRegularFile)
								.filter(path -> path.toString().endsWith(STREAMS_CFG_FILE_EXT))
								.map(tmpScdPath::resolve)
								.map(Path::toString)
								.collect(Collectors.toSet())
					);
			}
		}
		return resSet;
	}

	Optional<Map<@NonNull String, @NonNull RtspSrvConfigFileStreams>> readAllInputFiles(
				@NonNull Set<@NonNull String> allInputFiles
			) throws IOException {
		boolean haveInvalidFile = false;
		Map<String, RtspSrvConfigFileStreams> resMap = new HashMap<>();
		for (String inputFilename : allInputFiles) {
			Optional<RtspSrvConfigFileStreams> tmpOptCfs = readOneInputFile(inputFilename);
			if (tmpOptCfs.isEmpty()) {
				haveInvalidFile = true;
				continue;
			}
			resMap.put(inputFilename, tmpOptCfs.get());
		}
		if (haveInvalidFile) {
			return Optional.empty();
		}
		return Optional.of(resMap);
	}

	void mergeInputConfigs(
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigFileStreams> mapCfs,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> allSubStreams,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> allStreams
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".mergeInputConfigs()";

		Set<String> ignoredIdsSs = new HashSet<>();
		Set<String> ignoredIdsStreams = new HashSet<>();
		for (Map.Entry<String, RtspSrvConfigFileStreams> tmpEntryCfs : mapCfs.entrySet()) {
			String tmpInpFile = tmpEntryCfs.getKey();
			RtspSrvConfigFileStreams tmpCfsObj = tmpEntryCfs.getValue();

			mergeEntries(
					FNC_NAME,
					tmpInpFile,
					"Sub-Stream",
					ignoredIdsSs,
					tmpCfsObj.getSubStreams(),
					allSubStreams
				);
			mergeEntries(
					FNC_NAME,
					tmpInpFile,
					"Stream",
					ignoredIdsStreams,
					tmpCfsObj.getStreams(),
					allStreams
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private Optional<RtspSrvConfigFileStreams> readOneInputFile(@NonNull String inputFilename) throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".readOneInputFile()";

		//logDebug(FNC_NAME, "Input filename: " + inputFilename);
		try {
			RtspSrvConfigFileStreams tmpCfs = RtspSrvConfigFileReader.readStreamsConfigFromFile(rtspSrvConfig, inputFilename);
			return Optional.of(tmpCfs);
		} catch (ConfigInvalidException e) {
			logError(FNC_NAME, "ConfigInvalidException for file '" + inputFilename + "': " +
					e.getMessage());
		} catch (IOException e) {
			throw new IOException(FNC_NAME + ": readStreamsConfigFromFile() failed: " + e.getMessage());
		}
		return Optional.empty();
	}

	private <T> void mergeEntries(
				@NonNull String fncName,
				@NonNull String inputFilename,
				@NonNull String desc,
				Set<String> ignoredIds,
				@NonNull Map<@NonNull String, @NonNull T> inputEntries,
				@NonNull Map<@NonNull String, @NonNull T> mergedEntries
			) {
		for (Map.Entry<String, T> tmpEntry : inputEntries.entrySet()) {
			String tmpSsId = tmpEntry.getKey();
			if (ignoredIds.contains(tmpSsId)) {
				continue;
			}
			if (mergedEntries.containsKey(tmpSsId)) {
				ignoredIds.add(tmpSsId);
				logWarn(fncName, "ignoring duplicate " + desc + " ID '" + tmpSsId + "' from file '" + inputFilename + "'");
				mergedEntries.remove(tmpSsId);
				continue;
			}
			T tmpSsObj = tmpEntry.getValue();
			mergedEntries.put(tmpSsId, tmpSsObj);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
