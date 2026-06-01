package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RtspProtoLowMsgReader {

	private static final Pattern PATTERN_ILLEGAL_CHARS = Pattern.compile("[\\P{Print}$]");

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final boolean isDebugPrintRtspRcvd;

	public RtspProtoLowMsgReader(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				boolean isDebugPrintRtspRcvd
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.isDebugPrintRtspRcvd = isDebugPrintRtspRcvd;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgRaw readMessage() throws InputStreamNotReadyException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".readRequest()";

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		/*
		 * Read the request line.
		 * Example:
		 *   "GET_PARAMETER rtsp://admin:ABCDEFGH@192.168.1.1:1151/some.stream RTSP/1.0"
		 */
		try {
			resObj.requestLine = readOneLine(true);
			if (isDebugPrintRtspRcvd) {
				logDebug(FNC_NAME, "-- BEG --------------------------------------------------------------------------");
				logDebug(FNC_NAME, "-------- requestLine: " + resObj.requestLine);
			}
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "EOS reached");
			return resObj;
		}
		if (resObj.requestLine.isBlank()) {
			logError(FNC_NAME, "received empty line");
			return resObj;
		}

		/*
		 * Read all header lines.
		 * Example:
		 *   "CSeq: 8"
		 *   "User-Agent: DummyRtspClient/1.0"
		 *   "Session: 74DD52CE"
		 *   ""
		 */
		String headerLine;
		int timeoutCnt = 0;
		do {
			try {
				headerLine = readOneLine(false);
				timeoutCnt = 0;
				if (isDebugPrintRtspRcvd) {
					logDebug(FNC_NAME, "---------------- headerLine: " + headerLine);
				}
				resObj.headerLines.add(headerLine);
			} catch (InputStreamNotReadyException | InputStreamEosException e) {
				if (timeoutCnt++ > 10) {
					break;
				}
				try {
					//noinspection BusyWait
					Thread.sleep(50);
				} catch (InterruptedException e2) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
				headerLine = "xxx";  // keep the loop going
			}
		} while (! headerLine.isBlank());

		resObj.readSuccess = true;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String readOneLine(boolean isFirst)
			throws InputStreamNotReadyException, InputStreamEosException, TcpSocketIoException {
		if (! rtxpTcpReadWrite.canReadRtsp()) {
			throw new InputStreamNotReadyException();
		}
		Optional<String> optLine = rtxpTcpReadWrite.readRtspLine();
		if (optLine.isEmpty()) {
			throw new InputStreamEosException();
		}
		String resS = optLine.get();
		// remove forbidden characters from the line
		Matcher matcher = PATTERN_ILLEGAL_CHARS.matcher(resS);
		resS = matcher.replaceAll("");
		if (isFirst) {
			// strip occasionally occurring nonsense from the beginning of the line
			for (RtspProtoMessageType tmpType : RtspProtoMessageType.values()) {
				int tmpIx = resS.indexOf(tmpType.name());
				if (tmpIx < 0) {
					continue;
				}
				if (tmpIx == 0) {
					break;
				}
				resS = resS.substring(tmpIx).strip();
				break;
			}
		}
		return resS;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
