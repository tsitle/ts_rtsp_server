package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;

import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public final class RtspProtoResponseParser extends RtspProtoParserBase {

	private final RtspConfig rtspConfig;

	public RtspProtoResponseParser(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		super(logMsgInterface, rtxpTcpReadWriteInterface, rtspSessionInfo);

		this.rtspConfig = rtspConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoStatusCode parseResponse() throws InputStreamNotReadyException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseResponse()";

		// parse response lines and extract the responseStatus:
		String responseLine;
		try {
			responseLine = readOneLine(true);
			if (rtspConfig.getIsDebugPrintRtspRcvd()) {
				logDebug(FNC_NAME, "-- BEG --------------------------------------------------------------------------");
				logDebug(FNC_NAME, "-------- responseLine: " + responseLine);
			}
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "EOS reached");
			return RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
		}
		if (responseLine.isBlank()) {
			logError(FNC_NAME, "received empty line");
			return RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
		}

		// read responseStatus from the responseLine
		RtspProtoStatusCode respStatusCode = parseResponseStatusCode(responseLine);

		// parse header lines
		String headerLine = "";
		int timeoutCnt = 0;
		do {
			try {
				headerLine = readOneLine(false);
				timeoutCnt = 0;
				if (rtspConfig.getIsDebugPrintRtspRcvd()) {
					logDebug(FNC_NAME, "---------------- headerLine: " + headerLine);
				}
				parseHeaderLineResponse(headerLine);
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
			} catch (RtspInvalidRequestException e) {
				logError(FNC_NAME, "InvalidRtspRequestException: " + e.getMessage());
				respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
			} catch (RtspInvalidSessionIdException e) {
				logError(FNC_NAME, "Invalid Session ID, rejecting response");
				respStatusCode = RtspProtoStatusCode.SESSION_NOT_FOUND;
			}
		} while (! headerLine.isBlank());

		return respStatusCode;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private RtspProtoStatusCode parseResponseStatusCode(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseResponseStatusCode()";

		/*
		 * Examples:
		 *   RTSP/1.0 200 OK
		 *   RTSP/1.0 405 Method Not Allowed
		 */
		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();
			String tmpStatCodeStr = tokens.nextToken();
			int tmpStatCodeInt;
			try {
				tmpStatCodeInt = Integer.parseInt(tmpStatCodeStr);
			} catch (NumberFormatException e) {
				logWarn(FNC_NAME, "Invalid Status Code: '" + tmpStatCodeStr + "' - " +
						"invalid format");
				return RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
			}
			//
			return Arrays.stream(RtspProtoStatusCode.values())
					.filter(tmpType -> tmpType.getValue() == tmpStatCodeInt)
					.findFirst()
					.orElse(RtspProtoStatusCode.INTERNAL_SERVER_ERROR);
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			return RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderLineResponse(@NonNull String headerLine)
			throws RtspInvalidSessionIdException, RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLineResponse()";

		if (parseHeaderLineCommon(headerLine)) {
			return;
		}

		if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_CSEQ)) {
			parseHeaderLine_cseq(headerLine);
		} else {
			logWarn(FNC_NAME, "Received unknown header: '" + headerLine + "'");
		}
	}

	private void parseHeaderLine_cseq(@NonNull String headerLine) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_cseq()";

		String tmpCseqStr = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_CSEQ.length()).strip();
		int tmpCseqInt;
		try {
			tmpCseqInt = Integer.parseInt(tmpCseqStr);
		} catch (NumberFormatException e) {
			logWarn(FNC_NAME, "Invalid CSeq parameter: '" + tmpCseqStr + "' - " +
					"invalid format");
			return;
		}
		if (tmpCseqInt != rtspSessionInfo.rtspServerSeqNrExpected) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Invalid CSeq");
		}
	}

}
