package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RtspProtoLowMsgReader {

	private static final Pattern PATTERN_ILLEGAL_CHARS = Pattern.compile("[\\P{Print}$]");
	private static final int MAX_HEADER_LINES = 1024;
	private static final int MAX_BODY_SIZE = 1024 * 16;  // 16 kB

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
		final String FNC_NAME = getClass().getSimpleName() + ".readMessage()";

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		try {
			// main (request/status) line
			readMainLine(resObj);
			if (isDebugPrintRtspRcvd) {
				logDebug(FNC_NAME, "-- BEG --------------------------------------------------------------------------");
				logDebug(FNC_NAME, "-------- mainLine: " + resObj.mainLine);
			}
			if (resObj.mainLine.isBlank()) {
				logError(FNC_NAME, "received empty mainLine");
				return resObj;
			}

			// all header lines
			readHeaderLines(resObj);
			if (isDebugPrintRtspRcvd) {
				for (String tmpHdLine : resObj.headerLines) {
					logDebug(FNC_NAME, "---------------- headerLine: " + tmpHdLine.replace("\t", "<TAB>"));
				}
			}

			// message body
			readBody(resObj);
			if (isDebugPrintRtspRcvd && ! resObj.body.isEmpty()) {
				for (String tmpBodyLine : resObj.body.split(RtspProtoLowMsgConstants.CRLF)) {
					logDebug(FNC_NAME, "---------------- bodyLine__: " + tmpBodyLine.replace("\t", "<TAB>"));
				}
			}

			//
			resObj.readSuccess = true;
			return resObj;
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "EOS reached");
			return resObj;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void readMainLine(@NonNull RtspProtoLowMsgRaw outputMsg)
			throws InputStreamNotReadyException, TcpSocketIoException, InputStreamEosException {
		/*
		 * Read the main (request/status) line.
		 * Example:
		 *   "GET_PARAMETER rtsp://admin:ABCDEFGH@192.168.1.1:1151/some.stream RTSP/1.0"
		 *  or
		 *   "RTSP/2.0 200 OK"
		 */

		outputMsg.mainLine = readOneLine(true).replace("\t", "");
	}

	private void readHeaderLines(@NonNull RtspProtoLowMsgRaw outputMsg)
			throws TcpSocketIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".readHeaderLines()";

		/*
		 * Read all header lines.
		 * Example:
		 *   "CSeq: 8"
		 *   "User-Agent: DummyRtspClient/1.0"
		 *   "Session: 74DD52CE"
		 *   "RTP-Info:url=\"rtsp://example.com/fizzle/audiotrack\""
		 *   "         ssrc=0D12F123:seq=5712;rtptime=934207921,"
		 *   "\t\turl=\"rtsp://example.com/fizzle/videotrack\""
		 *   "         ssrc=789DAF12:seq=57654;rtptime=2792482193"
		 *   ""
		 */

		String headerLine;
		int timeoutCnt = 0;
		do {
			try {
				headerLine = readOneLine(false);
				//
				if (outputMsg.headerLines.size() >= MAX_HEADER_LINES) {
					logWarn(FNC_NAME, "Max. number of header lines (" + MAX_HEADER_LINES + ") reached, discarding further lines");
					continue;
				}
				//
				timeoutCnt = 0;
				if ((headerLine.startsWith(" ") || headerLine.startsWith("\t")) && ! outputMsg.headerLines.isEmpty()) {
					String tmpPrevLine = outputMsg.headerLines.getLast();
					outputMsg.headerLines.set(outputMsg.headerLines.size() - 1, tmpPrevLine + " " + headerLine.strip());
				} else if (! headerLine.isBlank()) {
					outputMsg.headerLines.add(headerLine);
				}
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
	}

	private void readBody(@NonNull RtspProtoLowMsgRaw outputMsg)
			throws TcpSocketIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".readBody()";

		/*
		 * Read the message body.
		 * Example:
		 *   "v=0"
		 *   "o=mhandley 2890844526 IN IP4 126.16.64.4"
		 *   "s=SDP Seminar"
		 */

		int contentLenHlIx = findContentLengthHeaderLineIndex(outputMsg);
		if (contentLenHlIx < 0) {
			return;
		}
		long parsedContentLen = parseContentLength(outputMsg.headerLines.get(contentLenHlIx));
		if (parsedContentLen <= 0L) {
			return;
		}

		String bodyLine;
		StringBuilder bodySb = new StringBuilder();
		int timeoutCnt = 0;
		do {
			try {
				bodyLine = readOneLine(false);
				timeoutCnt = 0;
				bodySb.append(bodyLine).append(RtspProtoLowMsgConstants.CRLF);
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
				bodyLine = "xxx";  // keep the loop going
			}
		} while (! bodyLine.isBlank() && bodySb.length() < parsedContentLen);

		outputMsg.body = bodySb.toString();
		if (outputMsg.body.length() > parsedContentLen) {
			/*
			 * Silently trim the body length. We added CRLF to the last line of the body, but we do not know
			 * if the original line ended with CRLF.
			 */
			outputMsg.body = outputMsg.body.substring(0, (int)parsedContentLen);
		} else if (outputMsg.body.length() < parsedContentLen) {
			logWarn(FNC_NAME, String.format(
					"Content-Length is less than expected (is=%s, exp=%s)",
					Integer.toUnsignedString(outputMsg.body.length()), Long.toUnsignedString(parsedContentLen)));
		}
		// add CRLF to the end of the body
		if (! outputMsg.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
			outputMsg.body += RtspProtoLowMsgConstants.CRLF;
			// silently update the 'Content-Length' header
			outputMsg.headerLines.set(
					contentLenHlIx,
					RtspHeaderKey.CONTENT_LEN.getStrValue() + ": " + Integer.toUnsignedString(outputMsg.body.length())
				);
		}
		// if the 'Content-Length' header was wrong all along, update it now
		if (outputMsg.body.length() != parsedContentLen) {
			logDebug(FNC_NAME, "Updating Content-Length in msg headers to " +
					Integer.toUnsignedString(outputMsg.body.length()));
			outputMsg.headerLines.set(
					contentLenHlIx,
					RtspHeaderKey.CONTENT_LEN.getStrValue() + ": " + Integer.toUnsignedString(outputMsg.body.length())
				);
		}
	}

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
		resS = resS.replace("\t", "###***TAB***###");
		Matcher matcher = PATTERN_ILLEGAL_CHARS.matcher(resS);
		resS = matcher.replaceAll("");
		resS = resS.replace("###***TAB***###", "\t");
		if (isFirst) {
			// strip occasionally occurring nonsense from the beginning of the line
			for (RtspMessageType tmpType : RtspMessageType.values()) {
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

	private static int findContentLengthHeaderLineIndex(@NonNull RtspProtoLowMsgRaw msg) {
		final String SEARCH_CONTLEN = RtspHeaderKey.CONTENT_LEN.getStrValue().toLowerCase() + ":";

		for (int i = 0; i < msg.headerLines.size(); i++) {
			if (msg.headerLines.get(i).toLowerCase().startsWith(SEARCH_CONTLEN)) {
				return i;
			}
		}
		return -1;
	}

	private long parseContentLength(@NonNull String headerLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseContentLength()";

		long resI = -1L;
		try {
			String[] tmpSplit = headerLine.split(":");
			if (tmpSplit.length != 2) {
				throw new NumberFormatException();
			}
			resI = Long.parseLong(tmpSplit[1].strip());
		} catch (NumberFormatException e) {
			logWarn(FNC_NAME, "Failed to parse Content-Length in '" + headerLine + "'");
			return resI;
		}
		if (resI > MAX_BODY_SIZE) {
			logWarn(FNC_NAME, "Content-Length in '" + headerLine + "' exceeds max. of " +
					Integer.toUnsignedString(MAX_BODY_SIZE) + " bytes, trimming body");
			resI = MAX_BODY_SIZE;
		}
		return resI;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
