package org.tsitle.rtsp;

import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.threads.rtsp.ThreadRtspServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtspServerApp {

	private static RtspConfig rtspConfig = null;

	private static int clientConnectionCount = 0;
	private static final AtomicBoolean doStop = new AtomicBoolean(false);

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
				try {
					Thread.sleep(200);
					System.out.println(FNC_NAME + ": Shutting down ...");
					doStop.set(true);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
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
			System.exit(1);
		} catch (IOException e) {
			System.err.println(FNC_NAME + ": IOException caught: " + e.getMessage());
			System.exit(1);
		}

		//
		boolean resB = runServerLoop();
		if (! resB) {
			System.exit(1);
		}
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

}
