package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.helpers.SleepHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class ThreadRtxpTcpCommsBase extends Thread {

	private enum ErrorState {
		ES_NONE,
		ES_TCP_SOCKET_CLOSED,
		ES_TCP_SOCKET_IO,
		ES_TCP_SOCKET_ACTIVITY_TIMEOUT,
		ES_INTERRUPTED,
		ES_OTHER
	}

	protected static final int BINARY_PKT_RB_SIZE_INIT = 10;
	protected static final int BINARY_PKT_RB_SIZE_MAX = 1000;
	private static final int TEXT_RB_SIZE_INIT = 10;
	private static final int TEXT_RB_SIZE_MAX = 1000;
	protected static final String CRLF = "\r\n";

	private final @NonNull LogMsgInterface logMsgInterface;
	protected final @NonNull TcpActivityInterface tcpActivityInterface;
	protected final @NonNull Socket rtspSocketTcp;

	private final @NonNull String threadName;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final AtomicBoolean doStop = new AtomicBoolean(false);
	protected final AtomicBoolean hasSocketBeenImplicitlyClosed = new AtomicBoolean(false);
	protected final BlockedState blockedState = new BlockedState();

	protected final AtomicBoolean isRtpRtcpAllowed = new AtomicBoolean(false);

	private @NonNull ErrorState errorState = ErrorState.ES_NONE;
	private @NonNull String errorMsg = "";

	protected final Map<@NonNull RtspProtoTcpChannelNr, @NonNull BinaryPktRingBuf> mapBinaryDataRbs = new HashMap<>();
	protected final TextDataRingBuf textDataRingBuf = new TextDataRingBuf(TEXT_RB_SIZE_INIT, TEXT_RB_SIZE_MAX);

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param tcpActivityInterface TCP activity checker interface
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param timeoutSocketReadMs Timeout for TCP socket read operations in milliseconds
	 * @param isReadThread Is this thread for reading from the socket?
	 */
	protected ThreadRtxpTcpCommsBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull TcpActivityInterface tcpActivityInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				int timeoutSocketReadMs,
				boolean isReadThread
			) {
		this.logMsgInterface = logMsgInterface;
		this.tcpActivityInterface = tcpActivityInterface;
		this.rtspSocketTcp = rtspSocketTcp;

		//
		if (isReadThread) {
			if (timeoutSocketReadMs < 1) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
						"timeoutSocketReadMs must be positive");
			}
			try {
				rtspSocketTcp.setTcpNoDelay(true);
				rtspSocketTcp.setSoTimeout(timeoutSocketReadMs);
			} catch (SocketException e) {
				throw new RuntimeException(e);
			}
		}
		//
		this.threadName = "RTXP_TCP_" + (isReadThread ? "R" : "W") + "#c" + clientConnectionNr;
		//
		java.net.InetAddress tmpRemoteIp = rtspSocketTcp.getInetAddress();
		if (tmpRemoteIp == null) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
					"could not read remote IP address");
		}
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
			while (! doStop.get()) {
				if (! mainLoop()) {
					break;
				}
				if (! SleepHelper.basicSleep(0L, 250_000)) {
					throw new InterruptedException();
				}
			}
		} catch (TcpSocketClosedException e) {
			errorMsg = "TcpSocketClosedException caught";
			errorState = ErrorState.ES_TCP_SOCKET_CLOSED;
			logDebug(FNC_NAME, errorMsg);
		} catch (TcpSocketIoException e) {
			errorMsg = "TcpSocketIoException caught: " + e.getMessage();
			errorState = ErrorState.ES_TCP_SOCKET_IO;
			logDebug(FNC_NAME, errorMsg);
		} catch (TcpSocketActivityTimeoutException e) {
			errorMsg = "TcpSocketActivityTimeoutException caught";
			errorState = ErrorState.ES_TCP_SOCKET_ACTIVITY_TIMEOUT;
			logDebug(FNC_NAME, errorMsg);
		} catch (InterruptedException e2) {
			errorMsg = "InterruptedException caught";
			errorState = ErrorState.ES_INTERRUPTED;
			logError(FNC_NAME, errorMsg);
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			//e.printStackTrace();
			errorMsg = "Exception caught: " + e.getMessage();
			errorState = ErrorState.ES_OTHER;
			logError(FNC_NAME, errorMsg);
		} finally {
			closeIoStream();
			hasSocketBeenImplicitlyClosed.set(true);
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public boolean isRunning() {
		return isRunning.get();
	}

	public void stopThread() {
		doStop.set(true);
	}

	public boolean hasBeenRequestedToStop() {
		return doStop.get();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	abstract void closeIoStream();

	// -----------------------------------------------------------------------------------------------------------------

	synchronized boolean getIsRtpRtcpAllowed() {
		return isRtpRtcpAllowed.get();
	}

	synchronized void setIsRtpRtcpAllowed(boolean value) {
		isRtpRtcpAllowed.set(value);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract boolean mainLoop()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException;

	protected void closeIoStream(BlockedState.@NonNull Flag flag, @Nullable InputStream is, @Nullable OutputStream os) {
		try {
			blockedState.waitForUnblockedAndThenBlock(flag);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return;
		}
		try {
			if (is != null) {
				is.close();
			}
			if (os != null) {
				os.close();
			}
		} catch (IOException e) {
			// ignore
		} finally {
			blockedState.unblock(flag);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void raiseForErrorState()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		switch (errorState) {
			case ES_TCP_SOCKET_CLOSED -> throw new TcpSocketClosedException();
			case ES_TCP_SOCKET_IO, ES_INTERRUPTED, ES_OTHER -> throw new TcpSocketIoException(errorMsg);
			case ES_TCP_SOCKET_ACTIVITY_TIMEOUT -> throw new TcpSocketActivityTimeoutException();
			case ES_NONE -> { }
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
