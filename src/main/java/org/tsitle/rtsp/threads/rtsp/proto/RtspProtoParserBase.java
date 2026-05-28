package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public class RtspProtoParserBase {

	private static final Pattern PATTERN_ILLEGAL_CHARS = Pattern.compile("[\\P{Print}$]");

	protected final boolean isForServer;
	protected final boolean isForRequests;
	protected final @NonNull LogMsgInterface logMsgInterface;
	protected final @NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface;
	protected final RtspSessionInfo rtspSessionInfo;

	protected RtspProtoParserBase(
				boolean isForServer,
				boolean isForRequests,
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.isForServer = isForServer;
		this.isForRequests = isForRequests;
		this.logMsgInterface = logMsgInterface;
		this.rtxpTcpReadWriteInterface = rtxpTcpReadWriteInterface;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull String readOneLine(boolean isFirst)
			throws InputStreamNotReadyException, InputStreamEosException, TcpSocketIoException {
		if (! rtxpTcpReadWriteInterface.canReadRtsp()) {
			throw new InputStreamNotReadyException();
		}
		Optional<String> optLine = rtxpTcpReadWriteInterface.readRtspLine();
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

	protected boolean parseHeaderLineCommon(@NonNull String headerLine) throws RtspInvalidSessionIdException {
		if (headerLine.isBlank()) {
			return true;  // line has been handled
		}

		if (headerLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_1) ||
				headerLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_2)) {
			// ignore this non-standard header line
			return true;  // line has been handled
		}
		if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_USERAGENT)) {
			parseHeaderLine_useragent(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_SESSION)) {
			parseHeaderLine_session(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_DATE)) {
			parseHeaderLine_date(headerLine);
		} else {
			return false;  // line has NOT been handled
		}
		return true;  // line has been handled
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	protected void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	protected void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderLine_useragent(@NonNull String headerLine) {
		/*
		 * GStreamer (Rocky Linux 10): GStreamer/1.24.11
		 * GStreamer (KUbuntu 24): GStreamer/1.24.2
		 * VLC (Rocky Linux 10): LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)
		 * FFplay (Rocky Linux 10): Lavf61.7.100
		 * VLC (Windows): LibVLC/3.0.21 (LIVE555 Streaming Media v2016.11.28)
		 * VLC (macOS x86):
		 *   LibVLC/3.0.23 (LIVE555 Streaming Media v2016.11.28)
		 *   RealMedia Player Version 6.0.9.1235 (linux-2.0-libc6-i386-gcc2.95)
		 * RTSP Player (Windows): Lavf59.27.100
		 * Win RTSP Player (Windows): RTSPClient v1.0.16.0615 (LIVE555 Streaming Media v2016.05.20)
		 */
		rtspSessionInfo.clientUserAgent = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_USERAGENT.length()).strip();
	}

	private void parseHeaderLine_session(@NonNull String headerLine) throws RtspInvalidSessionIdException {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_session()";

		String tmpSessId = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_SESSION.length()).strip();
		if (isForServer && isForRequests) {
			rtspSessionInfo.rtspClientRequestSessionId = tmpSessId;
		}
		//logDebug(FNC_NAME, "Session='" + tmpSessId + "'");
		if (! rtspSessionInfo.rtspSessionId.equals(tmpSessId)) {
			throw new RtspInvalidSessionIdException();
		}
	}

	private void parseHeaderLine_date(@NonNull String headerLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_date()";

		String tmp = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_DATE.length()).strip();
		//logDebug(FNC_NAME, "Date='" + tmp + "'");
		if (tmp.isBlank()) {
			logError(FNC_NAME, "Invalid empty Date header value - ignoring");
		}
	}

}
