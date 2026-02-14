package org.tsitle.rtsp;

import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.threads.rtsp.ThreadRtspServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtspServerApp {

	private static RtspConfig rtspConfig = null;

	private static int clientConnectionCount = 0;
	private static final AtomicBoolean doStop = new AtomicBoolean(false);
	private static final AtomicBoolean doNeedShutdownHandler = new AtomicBoolean(true);
	private static final Map<@Nullable Integer, @Nullable ThreadRtspServer> rtspServerThreads = new ConcurrentHashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static void main(String[] argv) {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".main()";

		//
		if (argv.length != 1) {
			System.err.println(FNC_NAME + ": Required argument <config-file> missing");
			System.exit(1);
		}

		// without the Signal handler below, the Shutdown Hook works just fine
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if (doNeedShutdownHandler.get()) {
					System.out.println();
					System.out.println(FNC_NAME + ": Shutting down ...");
					doStop.set(true);
					//
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						System.out.println(FNC_NAME + ": InterruptedException");
						Thread.currentThread().interrupt();
					}
				}
			}));
		// using the Signal handler here causes the Shutdown Hook to not be called. But System.exit() will then trigger it
		/*sun.misc.Signal.handle(new sun.misc.Signal("INT"),  // SIGINT
				signal -> {
					System.out.println();
					System.out.println(FNC_NAME + ": Interrupted by Ctrl+C");
					System.exit(1);
				});*/

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
		}

		//
		boolean resB = runServerLoop();
		if (! resB) {
			doNeedShutdownHandler.set(false);
			System.exit(1);
		}

		//
		stopThreads();

		//
		System.out.println(FNC_NAME + ": Server terminated");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static boolean runServerLoop() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".runServerLoop()";

		// initiate TCP connection with the client for the RTSP session
		try (ServerSocket listenSocket = new ServerSocket(rtspConfig.getServerTcpPort())) {
			System.out.println(FNC_NAME + ": Waiting for connections on port " + rtspConfig.getServerTcpPort());

			listenSocket.setSoTimeout(50);  // only for accept()
			Socket socketRtspTcp;

			// @TODO limit concurrent RTSP sessions

			while (! doStop.get()) {
				try {
					socketRtspTcp = listenSocket.accept();  // blocks for setSoTimeout() value
				} catch (SocketTimeoutException e) {
					continue;
				}
				socketRtspTcp.setSoTimeout(50);  // only for read()

				//
				ThreadRtspServer thread = new ThreadRtspServer(
						rtspConfig,
						++clientConnectionCount,
						socketRtspTcp
					);
				rtspServerThreads.put(clientConnectionCount, thread);
				thread.setDaemon(false);
				thread.start();
			}
		} catch (BindException e) {
			System.err.println(FNC_NAME + ": BindException caught: " + e.getMessage());
			return false;
		} catch (IOException e) {
			System.err.println(FNC_NAME + ": IOException caught: " + e.getMessage());
			return false;
		}
		return true;
	}

	private static void stopThreads() {
		final String FNC_NAME = RtspServerApp.class.getSimpleName() + ".stopThreads()";

		for (Map.Entry<Integer, ThreadRtspServer> tmpEntry : rtspServerThreads.entrySet()) {
			if (tmpEntry.getKey() == null || tmpEntry.getValue() == null) {
				continue;
			}
			if (tmpEntry.getValue().isAlive()) {
				System.out.println(FNC_NAME + ": Stopping thread #" + tmpEntry.getKey());
				tmpEntry.getValue().stopThread();  // blocks until the thread has actually stopped
				try {
					tmpEntry.getValue().join();
				} catch (InterruptedException e) {
					System.err.println(FNC_NAME + ": Interrupted while joining thread " + tmpEntry.getKey());
				}
				System.out.println(FNC_NAME + ": Thread #" + tmpEntry.getKey() + " stopped");
			} else {
				System.out.println(FNC_NAME + ": Thread #" + tmpEntry.getKey() + " already stopped");
			}
		}
	}

}
