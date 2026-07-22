package org.tsitle.rtsp_server;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegLogLevelHelper;
import org.tsitle.lib_rtsp_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.config.RtspConfigElementaryStreamSource;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.lib_xrtxp.ssl.SslException;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.ssl.SslContextFactory;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.rtsp_server.threads.logging.RtxpLogger;
import org.tsitle.rtsp_server.threads.mq_e2i.ThreadMqE2I;
import org.tsitle.rtsp_server.threads.rtsp_tcp.RtspServerConstants;
import org.tsitle.rtsp_server.threads.rtsp_tcp.ThreadRtspTcpClientInbound;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RtspServerApp {

	private static final int RTSP_THREADS_TCM = 20;  // one thread per client connection

	private static RtspConfig rtspConfig = null;

	private static int clientConnectionCount = 0;
	private static final AtomicBoolean doStop = new AtomicBoolean(false);
	private static final CancelToken cancelToken = new CancelToken();
	private static final AtomicBoolean doNeedShutdownHandler = new AtomicBoolean(true);
	private static final AtomicBoolean isShutdownComplete = new AtomicBoolean(false);
	private static RtxpLogger rtxpLoggerThread = new RtxpLogger();
	private static final ExecutorService poolRtspTcm = new ThreadPoolExecutor(
			RTSP_THREADS_TCM,
			RTSP_THREADS_TCM,
			60L, TimeUnit.SECONDS,
			new SynchronousQueue<>(true)
		);
	private static @Nullable ExecutorService poolMqE2I = null;
	private static @Nullable RtspPlayThreadMng rtspPlayThreadMng = null;

	private RtspServerApp() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static void main(String[] argv) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".main()";

		verifyTlsCryptoProviders();

		//
		if (argv.length != 1) {
			System.err.println(FNC_NAME + ": Required argument <config-file> missing");
			System.exit(1);
		}

		// without the Signal handler below, the Shutdown Hook works just fine
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (doNeedShutdownHandler.get()) {
					System.out.println();
					System.out.println(FNC_NAME + ": SDH: Shutting down ...");
					doStop.set(true);
					//
					int loopCnt = 0;
					while (! isShutdownComplete.get() && loopCnt++ < 60) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							System.err.println(FNC_NAME + ": SDH: InterruptedException");
							Thread.currentThread().interrupt();  // restore flag
						}
					}
					if (isShutdownComplete.get()) {
						System.out.println(FNC_NAME + ": SDH: Shutdown complete");
					} else {
						System.err.println(FNC_NAME + ": SDH: threads still running, forcing shutdown");
					}
				}
			}));
		// using the Signal handler here causes the Shutdown Hook to not be called. But System.exit() will then trigger it
		/*sun.misc.Signal.handle(new sun.misc.Signal("INT"),  // SIGINT
				signal -> {
					System.out.println("Interrupted by Ctrl+C");
					System.exit(1);
				});*/

		// set the log level for FFmpeg
		FfmpegLogLevelHelper.muteLogMsgs();

		// read the configuration
		try {
			rtspConfig = ConfigReader.readConfigFromFile(argv[0]);
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

		// start the ('external to internal') Message Queue threads
		final List<Integer> mqStreamSources = findMqStreamSources();
		poolMqE2I = new ThreadPoolExecutor(
				Math.max(mqStreamSources.size(), 1),
				Math.max(mqStreamSources.size(), 1),
				60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(true)
			);
		startMqs(mqStreamSources);

		// start the logger thread
		rtxpLoggerThread.setEnableOutputConsole(rtspConfig.getLoggingEnabledOutputConsole());
		rtxpLoggerThread.setEnableOutputFile(
				rtspConfig.getLoggingEnabledOutputFile(),
				rtspConfig.getLoggingOutputFilename()
			);
		rtxpLoggerThread.setName("RTXPLOGGER");
		rtxpLoggerThread.setDaemon(false);
		rtxpLoggerThread.start();

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

	private static void waitUntilLogQueueIsEmpty() {
		while (rtxpLoggerThread.havePendingMessages()) {
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

	private static @NonNull List<@NonNull Integer> findMqStreamSources() {
		List<Integer> resL = new ArrayList<>();
		for (Integer esSourceId : rtspConfig.getElementaryStreamSourceIds()) {
			Optional<RtspConfigElementaryStreamSource> optSs = rtspConfig.getElementaryStreamSourceObj(esSourceId);
			if (optSs.isEmpty()) {
				continue;
			}
			if (optSs.get().getEnabled() && optSs.get().getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
				resL.add(esSourceId);
			}
		}
		return resL;
	}

	private static void startMqs(@NonNull List<@NonNull Integer> esSourceIds) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".startMqs()";

		assert poolMqE2I != null;

		for (Integer tmpEsSourceId : esSourceIds) {
			RtspConfigElementaryStreamSource tmpEsSrcObj = rtspConfig.getElementaryStreamSourceObj(tmpEsSourceId).orElseThrow();
			Optional<String> tmpSslCertPath;
			try {
				tmpSslCertPath = rtspConfig.getMqServerSslCertificatePath(tmpEsSrcObj.getInputUri());
			} catch (ConfigInvalidException e) {
				// should never happen
				throw new IllegalStateException(e);
			}
			MqElementaryStreamSourceSettings mqSetts = tmpEsSrcObj.getInputMqSettings().orElseThrow();
			logDebug(FNC_NAME, "Starting MqE2I for '" +
					mqSetts.getHostname() + ":" + Integer.toUnsignedString(mqSetts.getPort().getPort16bit().orElseThrow()) + ":" +
					mqSetts.getRscGroup() + ":" + mqSetts.getRscChannel() + "'");
			ThreadMqE2I thread = new ThreadMqE2I(
					RtspServerApp::addMsgForLogThread,
					cancelToken,
					(@NonNull RtspProtoIdEsSource cbArgIdEsSource, @NonNull MqCodecSettings cbArgCodecSettings) -> {
							RtspConfigElementaryStreamSource tmpCbEsSrcObj =
									rtspConfig.getElementaryStreamSourceObj(cbArgIdEsSource).orElseThrow();
							if (cbArgCodecSettings.codec != null) {
								tmpCbEsSrcObj.setMqDynamicCodec(cbArgCodecSettings.getAsRtpPacketType());
							}
							if (cbArgCodecSettings.videoFps != null &&
									cbArgCodecSettings.videoFps != FrameRateEnum.UNKNOWN) {
								tmpCbEsSrcObj.setMqDynamicVideoFps(cbArgCodecSettings.videoFps);
							}
							if (cbArgCodecSettings.audioSamplerate != null &&
									cbArgCodecSettings.audioSamplerate != SampleRateEnum.UNKNOWN) {
								tmpCbEsSrcObj.setMqDynamicAudioSamplerateHz(cbArgCodecSettings.audioSamplerate);
							}
							if (cbArgCodecSettings.audioChannels != null) {
								tmpCbEsSrcObj.setMqDynamicAudioChannelCount(cbArgCodecSettings.audioChannels);
							}
							if (cbArgCodecSettings.audioSamplesPerFrame != null) {
								tmpCbEsSrcObj.setMqDynamicAudioSamplesPerFrame(cbArgCodecSettings.audioSamplesPerFrame);
							}
						},
					tmpEsSrcObj.getIdAsProtoId(),
					mqSetts,
					tmpSslCertPath.orElse("")
				);

			poolMqE2I.submit(thread);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull SSLServerSocketFactory createSslServerSocketFactory() throws Exception {
		Optional<String> optSslCaPath = rtspConfig.getRtspsSslCaPath();
		SSLContext sslCtx = SslContextFactory.createServerSocketFactory(
				Path.of(rtspConfig.getRtspsSslCertPath().orElseThrow()),
				Path.of(rtspConfig.getRtspsSslKeyPath().orElseThrow()),
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

		final int rtspsTcpPort = rtspConfig.getServerTcpPortRtsps();
		final int rtspTcpPort = rtspConfig.getServerTcpPortRtsp();

		final String cfgServerNameAndVersion = getAppNameAndVersion();

		final RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();

		rtspPlayThreadMng = new RtspPlayThreadMng(
				RtspServerApp::addMsgForLogThread,
				cancelToken,
				rtspConfig,
				globalSessionInfoSvc
			);

		try (ServerSocket listenSocketRtsps = (rtspsTcpPort > 0 ? openRtspsSocket(rtspsTcpPort) : null)) {
			try (ServerSocket listenSocketRtsp = (rtspTcpPort > 0 ? new ServerSocket(rtspConfig.getServerTcpPortRtsp()) : null)) {
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
						// start a queued Play thread
						rtspPlayThreadMng.startNewPlayThreadFromQueue();
						// clean up expired Play threads and Session Info objects
						if (++loopCount % 50 == 0) {
							rtspPlayThreadMng.doHousekeepingForPlayThreads();
							//
							globalSessionInfoSvc.doHousekeepingForSessionInfos(dbgDeletedSessionIds);
							for (RtspProtoIdSession tmpId : dbgDeletedSessionIds) {
								logDebug(FNC_NAME, "Deleted Session ID after timeout: " + tmpId.getIdStr().orElse("-unset-"));
							}
						}
						continue;
					}
					haveConn = false;
					socketRtspTcp.setSoTimeout(10);  // only for read()

					//
					ThreadRtspTcpClientInbound thread = new ThreadRtspTcpClientInbound(
							RtspServerApp::addMsgForLogThread,
							cancelToken,
							rtspConfig,
							cfgServerNameAndVersion,
							globalSessionInfoSvc,
							rtspPlayThreadMng,
							++clientConnectionCount,
							socketRtspTcp,
							isRtspsConn
						);

					try {
						poolRtspTcm.submit(thread);
					} catch (RejectedExecutionException e) {
						logWarn(FNC_NAME, "RejectedExecutionException caught: " + e.getMessage());
						try { socketRtspTcp.close(); } catch (IOException ignored) { }
					}

					/*if (clientConnectionCount == 2) {  // for profiling only
						break;
					}*/
				}
			}
		} catch (BindException e) {
			logError(FNC_NAME, "BindException caught: " + e.getMessage());
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

	private static void stopThreads() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".stopThreads()";

		if (rtspPlayThreadMng != null) {
			rtspPlayThreadMng.shutdownAllThreads();
		}

		//
		poolRtspTcm.shutdown();
		if (poolMqE2I != null) {
			poolMqE2I.shutdown();
		}
		cancelToken.cancelled = true;

		stopPool(FNC_NAME, "POOLRTSP", poolRtspTcm);

		if (poolMqE2I != null) {
			stopPool(FNC_NAME, "POOLMQEXT", poolMqE2I);
		}

		//
		waitUntilLogQueueIsEmpty();
		//
		rtxpLoggerThread.stopThread();
		try {
			rtxpLoggerThread.join();
		} catch (InterruptedException e) {
			System.err.println(FNC_NAME + ": Interrupted while joining thread RtxpLogger");
		}
		rtxpLoggerThread = null;

		System.err.println(FNC_NAME + ": all threads stopped");
	}

	private static void stopPool(@NonNull String fncName, @NonNull String poolName, @NonNull ExecutorService poolObj) {
		try {
			if (! poolObj.awaitTermination(10, TimeUnit.SECONDS)) {
				System.err.println(fncName + ": timeout, forcing shutdown " + poolName);
				poolObj.shutdownNow();  // force shutdown
			}
		} catch (InterruptedException e) {
			System.err.println(fncName + ": interrupted, forcing shutdown " + poolName);
			poolObj.shutdownNow();
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
	private static void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
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
		if (rtxpLoggerThread == null) { return; }
		RtxpLogLevel minLevel = rtspConfig.getLogLevel();
		if (logLevel == RtxpLogLevel.DEBUG && minLevel != RtxpLogLevel.DEBUG) { return; }
		if (logLevel == RtxpLogLevel.INFO && (minLevel == RtxpLogLevel.WARN || minLevel == RtxpLogLevel.ERROR)) { return; }
		if (logLevel == RtxpLogLevel.WARN && minLevel == RtxpLogLevel.ERROR) { return; }
		rtxpLoggerThread.log(logLevel, threadId, msg);
	}

}
