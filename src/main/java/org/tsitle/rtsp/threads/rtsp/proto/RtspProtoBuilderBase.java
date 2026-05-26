package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RtspProtoBuilderBase {

	protected static final String CRLF = "\r\n";

	protected final @NonNull LogMsgInterface logMsgInterface;
	protected final @NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface;
	protected final RtspSessionInfo rtspSessionInfo;

	protected RtspProtoBuilderBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtxpTcpReadWriteInterface = rtxpTcpReadWriteInterface;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected static @NonNull String buildDateString() {
		// Date: Fri, 03 Apr 2026 10:54:06 GMT
		return DateTimeFormatter.RFC_1123_DATE_TIME
				.withLocale(Locale.ENGLISH)
				.format(ZonedDateTime.now(ZoneOffset.UTC));
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	protected void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	protected void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	protected void internalLog(
				@SuppressWarnings("SameParameterValue") @NonNull RtxpLogLevel logLevel,
				@NonNull String fncName,
				@NonNull String msg
			) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
