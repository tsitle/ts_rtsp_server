package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.availstreams.RtspAsSvcInputData;
import org.tsitle.rtsp_server.config.*;
import org.tsitle.rtsp_server.availstreams.RtspAvailableStreamsSvc;
import org.tsitle.rtsp_server.threads.ThreadBase;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ThreadStreamsConfig extends ThreadBase {

	private record WatchDir(@NonNull Path path, @NonNull WatchService watchService) { }

	private final @NonNull Set<@NonNull String> streamsConfigDirs;
	private final @NonNull RtspAvailableStreamsSvc availableStreamsSvc;

	private final String threadName;
	private final ReentrantLock lock = new ReentrantLock();
	private final List<WatchDir> watchDirs = new ArrayList<>();
	private boolean isFirstRun = true;

	private final @NonNull StreamsCfgReader streamsCfgReader;
	private final @NonNull StreamsCfgMapper streamsCfgMapper;

	public ThreadStreamsConfig(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull RtspAvailableStreamsSvc availableStreamsSvc
			) {
		super(logMsgInterface);

		this.threadName = "STREAMSCFG";

		//
		this.streamsConfigDirs = rtspSrvConfig.getStreamConfigDirs();
		this.availableStreamsSvc = availableStreamsSvc;

		//
		this.streamsCfgReader = new StreamsCfgReader(logMsgInterface, rtspSrvConfig, streamsConfigDirs);
		this.streamsCfgMapper = new StreamsCfgMapper(logMsgInterface);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			startWatchSvcs();
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
			isRunning.set(false);
			return;
		}

		//
		try {
			while (! doStop.get()) {
				mainLoop();
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			for (WatchDir tmpWc : watchDirs) {
				try {
					tmpWc.watchService.close();
				} catch (IOException e) {
					// ignore
				}
			}
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void startWatchSvcs() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".startWatchSvcs()";

		lock.lock();
		try {
			for (String tmpScdCur : streamsConfigDirs) {
				WatchService tmpScdWc = FileSystems.getDefault().newWatchService();
				if (tmpScdWc == null) {
					return;
				}

				Path tmpScdPath = Paths.get(tmpScdCur).toAbsolutePath();
				logDebug("startWatchSvc", "watching '" + tmpScdPath + "'");
				try {
					tmpScdPath.register(
							tmpScdWc,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY
					);
				} catch (IOException e) {
					throw new IOException(FNC_NAME + ": register() failed: " + e.getMessage());
				}

				watchDirs.add(new WatchDir(tmpScdPath, tmpScdWc));
			}
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException, IOException {
		lock.lock();
		try {
			if (watchDirs.isEmpty()) {
				Thread.sleep(1000);
				return;
			}
			final boolean bckpIsFirstRun = isFirstRun;
			isFirstRun = false;
			if (bckpIsFirstRun || checkForChanges()) {
				updateStreams();
			}
			Thread.sleep(500);
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkForChanges() {
		boolean resB = false;
		for (WatchDir wd : watchDirs) {
			resB |= checkForChangesInOneScd(wd);
		}
		return resB;
	}

	private boolean checkForChangesInOneScd(@NonNull WatchDir wd) {
		boolean resB = false;
		WatchKey key;
		while ((key = wd.watchService.poll()) != null) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					continue;
				}
				Path ctx = wd.path.resolve((Path)event.context()).toAbsolutePath();
				if (! ctx.toString().endsWith(StreamsCfgReader.STREAMS_CFG_FILE_EXT)) {
					continue;
				}
				/*logDebug("checkForChangesInOneScd()", "Event kind: " + event.kind() + ": '" + ctx.toAbsolutePath() + "'");*/
				resB = true;
			}
			key.reset();
		}
		return resB;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateStreams() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".updateStreams()";

		Set<@NonNull String> allInputFiles;
		try {
			allInputFiles = streamsCfgReader.getAllInputFilenames();
		} catch (IOException e) {
			throw new IOException(FNC_NAME + ": getAllInputFilenames() failed: " + e.getMessage());
		}

		//
		Optional<Map<@NonNull String, @NonNull RtspSrvConfigFileStreams>> optAllInputFiles =
				streamsCfgReader.readAllInputFiles(allInputFiles);
		if (optAllInputFiles.isEmpty()) {
			logWarn(FNC_NAME, "no Streams Config files to process");
			return;
		}
		Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> allSubStreams = new HashMap<>();
		Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> allStreams = new HashMap<>();
		streamsCfgReader.mergeInputConfigs(
				optAllInputFiles.get(),
				allSubStreams,
				allStreams
			);

		//
		boolean tmpR = StreamsCfgExtendedValidator.doExtendedValidation(
				Objects.requireNonNull(logMsgInterface),
				FNC_NAME,
				allSubStreams,
				allStreams
			);
		if (! tmpR) {
			logWarn(FNC_NAME, "aborting processing of Streams Config files");
			return;
		}

		//
		RtspAsSvcInputData asSvcInputData = new RtspAsSvcInputData();
		Optional<Boolean> tmpOptHaveChanges = streamsCfgMapper.mapStreamsCfg(allSubStreams, allStreams, asSvcInputData);
		if (tmpOptHaveChanges.isEmpty()) {
			logWarn(FNC_NAME, "aborting processing of Streams Config files");
			return;
		}
		if (! tmpOptHaveChanges.get()) {
			logInfo(FNC_NAME, "updated available streams (no changes)");
			return;
		}

		//
		availableStreamsSvc.updateAvailableStreamsFromConfig(asSvcInputData);

		//
		int tmpCntDeleted = asSvcInputData.isIdsDeleted.size();
		int tmpCntAdded = asSvcInputData.isIdsAdded.size();
		int tmpCntModified = asSvcInputData.isIdsModified.size();
		logInfo(FNC_NAME, String.format("updated available streams (new=%d, mod=%d, del=%d)",
				tmpCntAdded, tmpCntModified, tmpCntDeleted));
	}

}
