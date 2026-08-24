package org.tsitle.rtsp_server;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.helpers.FfmpegHelperFfLogLevel;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.rtsp_server.availstreams.RtspAvailableStreamsSvc;
import org.tsitle.rtsp_server.config.RtspSrvConfigFileReader;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.lib_xrtxp.ssl.SslException;
import org.tsitle.rtsp_server.threadmng.*;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.lib_xrtxp.ssl.SslContextFactory;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.threads.logging.ThreadRtxpLogger;
import org.tsitle.rtsp_server.threads.rtsp_tcp.RtspServerConstants;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.rtsp_server.threads.streamscfg.ThreadStreamsConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RtspServerApp {

	private static RtspSrvConfigMain rtspSrvConfig = null;

	private static int clientConnectionCount = 0;
	private static final AtomicBoolean doStop = new AtomicBoolean(false);
	private static final CancelToken cancelToken = new CancelToken();
	private static final AtomicBoolean doNeedShutdownHandler = new AtomicBoolean(true);
	private static final AtomicBoolean isShutdownComplete = new AtomicBoolean(false);
	private static @Nullable ThreadRtxpLogger threadRtxpLogger = null;
	private static @Nullable RtspThreadMngPlay rtspThreadMngPlay = null;
	private static @Nullable RtspThreadMngTci rtspThreadMngTci = null;
	private static @Nullable RtspThreadMngInpMqExt rtspThreadMngInpMqExt = null;
	private static @Nullable RtspThreadMngInpDmxRtsp rtspThreadMngInpDmxRtsp = null;
	private static @Nullable RtspThreadMngInpDmxAf rtspThreadMngInpDmxAf = null;

	private static final @NonNull RtspAvailableStreamsSvc availableStreamsSvc = new RtspAvailableStreamsSvc();
	private static @Nullable ThreadStreamsConfig threadStreamsConfig = null;

	private RtspServerApp() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/* pre-Java25: public */ static void main(String[] argv) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".main()";

		verifyTlsCryptoProviders();

		//
		if (argv.length != 1) {
			System.err.println(FNC_NAME + ": Required argument <config-file> missing");
			System.exit(1);
		}

		//
		addShutdownHook(FNC_NAME);

		// set the log level for FFmpeg
		FfmpegHelperFfLogLevel.muteLogMsgs();

		//
		readMainConfigFile(argv[0]);

		//
		startLoggerThread();

		// start the ('external to internal') Message Queue Thread Manager
		startMessageQueueThreadsE2I();

		// start the Demux RTSP Thread Manager
		startDmxRtspThreads();

		// start the Demux AF Thread Manager
		startDmxAfThreads();

		//
		startStreamsConfigThread();

		//
		boolean resB = runServerLoop();
		if (! resB) {
			waitUntilLogQueueIsEmpty();
			//
			doNeedShutdownHandler.set(false);
			System.exit(1);
		}

		//
		stopThreads();

		//
		System.out.println(FNC_NAME + ": Server terminated");
		isShutdownComplete.set(true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void verifyTlsCryptoProviders() {
		boolean hasSunEc = Arrays.stream(Security.getProviders())
				.map(Provider::getName)
				.anyMatch("SunEC"::equals);

		if (! hasSunEc) {
			throw new IllegalStateException(
					"Missing required JCA provider 'SunEC' in runtime image. " +
					"TLS/ECDHE handshakes may fail. " +
					"Rebuild runtime image with jlink module: --add-modules jdk.crypto.ec"
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void addShutdownHook(@NonNull String fncName) {
		// without the Signal handler below, the Shutdown Hook works just fine
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (doNeedShutdownHandler.get()) {
					System.out.println();
					System.out.println(fncName + ": SDH: Shutting down ...");
					doStop.set(true);
					//
					int loopCnt = 0;
					while (! isShutdownComplete.get() && loopCnt++ < 60) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							System.err.println(fncName + ": SDH: InterruptedException");
							Thread.currentThread().interrupt();  // restore flag
						}
					}
					if (isShutdownComplete.get()) {
						System.out.println(fncName + ": SDH: Shutdown complete");
					} else {
						System.err.println(fncName + ": SDH: threads still running, forcing shutdown");
					}
				}
			}));
		// using the Signal handler here causes the Shutdown Hook to not be called. But System.exit() will then trigger it
		/*sun.misc.Signal.handle(new sun.misc.Signal("INT"),  // SIGINT
				signal -> {
					System.out.println(fncName + ": Interrupted by Ctrl+C");
					System.exit(1);
				});*/
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspThreadMngTci createRtspThreadMngTci(
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc
			) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".createRtspThreadMngTci()";

		if (rtspThreadMngPlay == null) {
			throw new IllegalStateException(FNC_NAME + ": RtspThreadMngPlay not initialized");
		}
		return new RtspThreadMngTci(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspSrvConfig,
				cfgServerNameAndVersion,
				availableStreamsSvc,
				availableStreamsSvc,
				globalSessionInfoSvc,
				rtspThreadMngPlay
			);
	}

	private static @NonNull RtspThreadMngPlay createRtspThreadMngPlay(
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc
			) {
		return new RtspThreadMngPlay(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspSrvConfig,
				globalSessionInfoSvc
			);
	}

	private static @NonNull RtspThreadMngInpMqExt createRtspThreadMngMqExt() {
		return new RtspThreadMngInpMqExt(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspSrvConfig,
				availableStreamsSvc,
				availableStreamsSvc
			);
	}

	private static @NonNull RtspThreadMngInpDmxRtsp createRtspThreadMngDmxRtsp() {
		return new RtspThreadMngInpDmxRtsp(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspSrvConfig,
				availableStreamsSvc,
				availableStreamsSvc
			);
	}

	private static @NonNull RtspThreadMngInpDmxAf createRtspThreadMngDmxAf() {
		return new RtspThreadMngInpDmxAf(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspSrvConfig,
				availableStreamsSvc,
				availableStreamsSvc
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void readMainConfigFile(@NonNull String configFilePath) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".readMainConfigFile()";

		try {
			rtspSrvConfig = RtspSrvConfigFileReader.readMainConfigFromFile(configFilePath);
		} catch (ConfigInvalidException e) {
			System.err.println(FNC_NAME + ": ConfigInvalidException caught: " + e.getMessage());
			doNeedShutdownHandler.set(false);
			System.exit(1);
		} catch (IOException e) {
			System.err.println(FNC_NAME + ": IOException caught: " + e.getMessage());
			doNeedShutdownHandler.set(false);
			System.exit(1);
		} catch (IllegalArgumentException e) {
			System.err.println(FNC_NAME + ": IllegalArgumentException caught: " + e.getMessage());
			doNeedShutdownHandler.set(false);
			System.exit(1);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void startLoggerThread() {
		threadRtxpLogger = new ThreadRtxpLogger();
		threadRtxpLogger.setEnableOutputConsole(rtspSrvConfig.getLoggingEnabledOutputConsole());
		threadRtxpLogger.setEnableOutputFile(
				rtspSrvConfig.getLoggingEnabledOutputFile(),
				rtspSrvConfig.getLoggingOutputFilename()
			);
		threadRtxpLogger.setDaemon(false);
		threadRtxpLogger.start();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void startStreamsConfigThread() {
		threadStreamsConfig = new ThreadStreamsConfig(
				RtspServerApp::addMsgForLogThread,
				rtspSrvConfig,
				availableStreamsSvc
			);
		threadStreamsConfig.setDaemon(false);
		threadStreamsConfig.start();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void waitUntilLogQueueIsEmpty() {
		while (threadRtxpLogger != null && threadRtxpLogger.havePendingMessages()) {
			try {
				//noinspection BusyWait
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull SSLServerSocketFactory createSslServerSocketFactory() throws Exception {
		Optional<String> optSslCaPath = rtspSrvConfig.getRtspsSslCaPath();
		SSLContext sslCtx = SslContextFactory.createServerSocketFactory(
				Path.of(rtspSrvConfig.getRtspsSslCertPath().orElseThrow()),
				Path.of(rtspSrvConfig.getRtspsSslKeyPath().orElseThrow()),
				optSslCaPath.isEmpty() || optSslCaPath.get().isEmpty() ? null : Path.of(optSslCaPath.get())
			);
		return sslCtx.getServerSocketFactory();
	}

	private static @NonNull ServerSocket openRtspsSocket(int port) throws SslException {
		try {
			SSLServerSocketFactory sslFact = createSslServerSocketFactory();
			SSLServerSocket resObj = (SSLServerSocket)sslFact.createServerSocket(port);
			resObj.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
			resObj.setEnabledCipherSuites(new String[] {
					// TLS 1.3
					"TLS_AES_256_GCM_SHA384",
					"TLS_AES_128_GCM_SHA256",
					"TLS_CHACHA20_POLY1305_SHA256",

					// TLS 1.2
					"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
					"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
					"TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
				});
			return resObj;
		} catch (SslException e) {
			throw e;
		} catch (Exception e) {
			throw new SslException("Failed to open RTSPS socket on port " + port + ": " + e.getMessage());
		}
	}

	private static boolean runServerLoop() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".runServerLoop()";

		final int rtspsTcpPort = rtspSrvConfig.getServerTcpPortRtsps();
		final int rtspTcpPort = rtspSrvConfig.getServerTcpPortRtsp();

		final String cfgServerNameAndVersion = getAppNameAndVersion();

		final RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();

		rtspThreadMngPlay = createRtspThreadMngPlay(globalSessionInfoSvc);
		rtspThreadMngTci = createRtspThreadMngTci(cfgServerNameAndVersion, globalSessionInfoSvc);

		try (ServerSocket listenSocketRtsps = (rtspsTcpPort > 0 ? openRtspsSocket(rtspsTcpPort) : null)) {
			try (ServerSocket listenSocketRtsp = (rtspTcpPort > 0 ? new ServerSocket(rtspSrvConfig.getServerTcpPortRtsp()) : null)) {
				if (listenSocketRtsps != null) {
					logInfo(FNC_NAME, "Waiting for RTSPS connections on port " + rtspsTcpPort);
					listenSocketRtsps.setSoTimeout(25);  // only for accept()
				}
				if (listenSocketRtsp != null) {
					logInfo(FNC_NAME, "Waiting for RTSP connections on port " + rtspTcpPort);
					listenSocketRtsp.setSoTimeout(25);  // only for accept()
				}

				Socket socketRtspTcp = null;
				boolean haveConn = false;
				boolean isRtspsConn = false;
				int loopCount = 0;
				Set<@NonNull RtspProtoIdSession> dbgDeletedSessionIds = new HashSet<>();

				while (! doStop.get()) {
					if (listenSocketRtsps != null) {
						try {
							socketRtspTcp = listenSocketRtsps.accept();  // blocks for setSoTimeout() value
							haveConn = true;
							isRtspsConn = true;
						} catch (SocketTimeoutException ignored) {
							// nothing to do
						}
					}
					if (! haveConn && listenSocketRtsp != null) {
						try {
							socketRtspTcp = listenSocketRtsp.accept();  // blocks for setSoTimeout() value
							haveConn = true;
							isRtspsConn = false;
						} catch (SocketTimeoutException ignored) {
							// nothing to do
						}
					}
					if (! haveConn || socketRtspTcp == null) {
						innerMainLoop_tasksMaintenance(
								FNC_NAME,
								++loopCount,
								dbgDeletedSessionIds,
								globalSessionInfoSvc
							);
						continue;
					}
					haveConn = false;

					innerMainLoop_submitNewTcpThread(socketRtspTcp, isRtspsConn);

					/*if (clientConnectionCount == 2) {  // for profiling only
						break;
					}*/
				}
			}
		} catch (BindException e) {
			logError(FNC_NAME, "BindException caught: " + e.getMessage());
			return false;
		} catch (SocketException e) {
			logError(FNC_NAME, "SocketException caught: " + e.getMessage());
			return false;
		} catch (IOException e) {
			logError(FNC_NAME, "IOException caught: " + e.getMessage());
			return false;
		} catch (SslException e) {
			logError(FNC_NAME, "SslException caught: " + e.getMessage());
			return false;
		}
		return true;
	}

	private static void innerMainLoop_tasksMaintenance(
				@NonNull String fncName,
				int loopCount,
				@NonNull Set<@NonNull RtspProtoIdSession> dbgDeletedSessionIds,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc
			) {
		boolean isLoopCount50 = (loopCount % 50 == 0);

		// check for Streams' Config update
		if (isLoopCount50 && availableStreamsSvc.haveStreamsChanged()) {
			performStreamsUpdate(globalSessionInfoSvc);
		}
		// start a queued Play thread
		if (! isLoopCount50 && rtspThreadMngPlay != null) {
			rtspThreadMngPlay.startNewPlayThreadFromQueue();
		}
		// clean up expired Play threads and Session Info objects
		if (isLoopCount50 && rtspThreadMngPlay != null) {
			rtspThreadMngPlay.doHousekeeping();
			//
			globalSessionInfoSvc.doHousekeepingForSessionInfos(dbgDeletedSessionIds);
			for (RtspProtoIdSession tmpId : dbgDeletedSessionIds) {
				logDebug(fncName, "Deleted Session ID after timeout: " + tmpId.getIdStr().orElse("-unset-"));
			}
		}
		// clean up expired TCI, MQext, DmxRtsp and DmxAf threads
		if (isLoopCount50) {
			if (rtspThreadMngTci != null) { rtspThreadMngTci.doHousekeeping(); }
			if (rtspThreadMngInpMqExt != null) { rtspThreadMngInpMqExt.doHousekeeping(); }
			if (rtspThreadMngInpDmxRtsp != null) { rtspThreadMngInpDmxRtsp.doHousekeeping(); }
			if (rtspThreadMngInpDmxAf != null) { rtspThreadMngInpDmxAf.doHousekeeping(); }
		}
	}

	private static void innerMainLoop_submitNewTcpThread(@NonNull Socket socketRtspTcp, boolean isRtspsConn)
			throws SocketException {
		if (rtspThreadMngPlay == null || rtspThreadMngTci == null) {
			return;
		}
		socketRtspTcp.setSoTimeout(10);  // only for read()

		//
		rtspThreadMngTci.startNewTciThread(
				++clientConnectionCount,
				socketRtspTcp,
				isRtspsConn
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void stopThreads() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".stopThreads()";

		cancelToken.cancelled = true;  // used by RtspThreadMngXxx
		if (rtspThreadMngPlay != null) { rtspThreadMngPlay.shutdownAllThreads(); }
		if (rtspThreadMngTci != null) { rtspThreadMngTci.shutdownAllThreads(); }
		if (rtspThreadMngInpMqExt != null) { rtspThreadMngInpMqExt.shutdownAllThreads(); }
		if (rtspThreadMngInpDmxRtsp != null) { rtspThreadMngInpDmxRtsp.shutdownAllThreads(); }
		if (rtspThreadMngInpDmxAf != null) { rtspThreadMngInpDmxAf.shutdownAllThreads(); }

		//
		if (threadStreamsConfig != null) {
			threadStreamsConfig.stopThread();
			try {
				threadStreamsConfig.join();
			} catch (InterruptedException e) {
				System.err.println(FNC_NAME + ": Interrupted while joining thread StreamsConfig");
			}
			threadStreamsConfig = null;
		}

		//
		waitUntilLogQueueIsEmpty();
		//
		if (threadRtxpLogger != null) {
			threadRtxpLogger.stopThread();
			try {
				threadRtxpLogger.join();
			} catch (InterruptedException e) {
				System.err.println(FNC_NAME + ": Interrupted while joining thread RtxpLogger");
			}
			threadRtxpLogger = null;
		}

		System.err.println(FNC_NAME + ": all threads stopped");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void performStreamsUpdate(
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc
			) {
		Set<@NonNull RtspProtoIdInputSource> stopIsIds = new HashSet<>();
		Set<@NonNull RtspProtoIdEsSource> stopMqEsIds = new HashSet<>();
		availableStreamsSvc.getIdsForThreadsThatNeedToBeStopped(stopIsIds, stopMqEsIds);

		//
		Set<@NonNull RtspProtoIdSession> stopSessionIds = new HashSet<>();
		globalSessionInfoSvc.findSessionsThatUseInputSources(stopIsIds, stopSessionIds);

		//
		if (rtspThreadMngPlay != null) { rtspThreadMngPlay.shutdownThreadsForSessionIds(stopSessionIds); }
		if (rtspThreadMngTci != null) { rtspThreadMngTci.shutdownThreadsForSessionIds(stopSessionIds); }
		if (rtspThreadMngInpMqExt != null) { rtspThreadMngInpMqExt.shutdownThreadsForEsIds(stopMqEsIds); }
		if (rtspThreadMngInpDmxRtsp != null) { rtspThreadMngInpDmxRtsp.shutdownThreadsForIsIds(stopIsIds); }
		if (rtspThreadMngInpDmxAf != null) { rtspThreadMngInpDmxAf.shutdownThreadsForIsIds(stopIsIds); }

		sleepLongAndProsper();

		//
		availableStreamsSvc.performStreamsUpdate();

		//
		startMessageQueueThreadsE2I();
		startDmxRtspThreads();
		startDmxAfThreads();
	}

	private static void sleepLongAndProsper() {
		try {
			int ms = 1000;
			while (ms > 0 && ! doStop.get()) {
				//noinspection BusyWait
				Thread.sleep(100L);
				ms -= 100;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
	}

	private static void startMessageQueueThreadsE2I() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".startMessageQueueThreadsE2I()";

		final Set<@NonNull RtspProtoIdEsSource> mqStreamSources = availableStreamsSvc.findRequiredMqEsSourcesForInputSources();
		if (mqStreamSources.isEmpty()) {
			return;
		}
		if (mqStreamSources.size() > rtspSrvConfig.getThreadsMaximumMq()) {
			logError(FNC_NAME, "Too many MQ Sub-Stream sources " +
					"(have=" + mqStreamSources.size() + ", max=" + rtspSrvConfig.getThreadsMaximumMq() + ")");
			return;
		}

		if (rtspThreadMngInpMqExt == null) {
			rtspThreadMngInpMqExt = createRtspThreadMngMqExt();
		}

		for (RtspProtoIdEsSource tmpEsSourceId : mqStreamSources) {
			if (rtspThreadMngInpMqExt.isThreadForEsSourceIdRunning(tmpEsSourceId)) {
				continue;
			}
			rtspThreadMngInpMqExt.startNewMqExtThread(tmpEsSourceId);
		}
	}

	private static void startDmxRtspThreads() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".startDmxRtspThreads()";

		final Set<@NonNull RtspProtoIdInputSource> dmxRtspStreamSources = availableStreamsSvc.findRequiredDmxRtspInputSources();
		if (dmxRtspStreamSources.isEmpty()) {
			return;
		}
		if (dmxRtspStreamSources.size() > rtspSrvConfig.getThreadsMaximumDmxRtsp()) {
			logError(FNC_NAME, "Too many DMX RTSP Sub-Stream sources " +
					"(have=" + dmxRtspStreamSources.size() + ", max=" + rtspSrvConfig.getThreadsMaximumDmxRtsp() + ")");
			return;
		}

		if (rtspThreadMngInpDmxRtsp == null) {
			rtspThreadMngInpDmxRtsp = createRtspThreadMngDmxRtsp();
		}

		for (RtspProtoIdInputSource tmpIsId : dmxRtspStreamSources) {
			if (rtspThreadMngInpDmxRtsp.isThreadForInputSourceIdRunning(tmpIsId)) {
				continue;
			}
			rtspThreadMngInpDmxRtsp.startNewDmxThread(tmpIsId);
		}
	}

	private static void startDmxAfThreads() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".startDmxAfThreads()";

		final Set<@NonNull RtspProtoIdInputSource> dmxAfStreamSources = availableStreamsSvc.findRequiredDmxAfInputSources();
		if (dmxAfStreamSources.isEmpty()) {
			return;
		}
		if (dmxAfStreamSources.size() > rtspSrvConfig.getThreadsMaximumDmxAf()) {
			logError(FNC_NAME, "Too many DMX AF Sub-Stream sources " +
					"(have=" + dmxAfStreamSources.size() + ", max=" + rtspSrvConfig.getThreadsMaximumDmxAf() + ")");
			return;
		}

		if (rtspThreadMngInpDmxAf == null) {
			rtspThreadMngInpDmxAf = createRtspThreadMngDmxAf();
		}

		for (RtspProtoIdInputSource tmpIsId : dmxAfStreamSources) {
			if (rtspThreadMngInpDmxAf.isThreadForInputSourceIdRunning(tmpIsId)) {
				continue;
			}
			rtspThreadMngInpDmxAf.startNewDmxThread(tmpIsId);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getAppNameAndVersion() {
		String tmpAppVersion = System.getProperty(AppConstants.SYSPROP_CSTM_APP_VERSION);
		if (tmpAppVersion == null) {
			tmpAppVersion = "0.0";
		}
		return RtspServerConstants.SERVER_NAME + "/" + tmpAppVersion;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private static void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}
	private static void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private static void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}
	private static synchronized void addMsgForLogThread(
				@NonNull RtxpLogLevel logLevel,
				@NonNull String threadId,
				@NonNull String msg
			) {
		if (threadRtxpLogger == null) { return; }
		RtxpLogLevel minLevel = rtspSrvConfig.getLogLevel();
		if (logLevel == RtxpLogLevel.DEBUG && minLevel != RtxpLogLevel.DEBUG) { return; }
		if (logLevel == RtxpLogLevel.INFO && (minLevel == RtxpLogLevel.WARN || minLevel == RtxpLogLevel.ERROR)) { return; }
		if (logLevel == RtxpLogLevel.WARN && minLevel == RtxpLogLevel.ERROR) { return; }
		threadRtxpLogger.log(logLevel, threadId, msg);
	}

}
