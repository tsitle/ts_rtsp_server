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

	public static class ResponseInfo {
		public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
		public @NonNull Set<@NonNull RtspProtoMessageType> supportedMessageTypes = new HashSet<>();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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

	public @NonNull ResponseInfo parseResponse() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseResponse()";

		// parse response lines and extract the responseStatus:
		String responseLine;
		try {
			Optional<String> tmpOptLine = receiveFirstLine();
			if (tmpOptLine.isEmpty()) {
				logError(FNC_NAME, "failed to receive first line");
				return new ResponseInfo();
			}
			responseLine = tmpOptLine.get();
			if (rtspConfig.getIsDebugPrintRtspRcvd()) {
				logDebug(FNC_NAME, "-- BEG --------------------------------------------------------------------------");
				logDebug(FNC_NAME, "-------- responseLine: " + responseLine);
			}
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "EOS reached");
			return new ResponseInfo();
		}
		if (responseLine.isBlank()) {
			logError(FNC_NAME, "received empty line");
			return new ResponseInfo();
		}

		// read responseStatus from the responseLine
		ResponseInfo resObj = new ResponseInfo();
		resObj.statusCode = parseResponseStatusCode(responseLine);

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
				parseHeaderLineResponse(headerLine, resObj);
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
				resObj.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			} catch (RtspInvalidSessionIdException e) {
				logError(FNC_NAME, "Invalid Session ID, rejecting response");
				resObj.statusCode = RtspProtoStatusCode.SESSION_NOT_FOUND;
			}
		} while (! headerLine.isBlank());

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private Optional<String> receiveFirstLine() throws TcpSocketIoException, InputStreamEosException {
		int maxTries = 50;
		while (maxTries-- > 0) {
			try {
				String tmpStr = readOneLine(true);
				return Optional.of(tmpStr);
			} catch (InputStreamNotReadyException e) {
				try {
					Thread.sleep(15);
				} catch (InterruptedException ignore) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoStatusCode parseResponseStatusCode(String requestLine) {
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

	private void parseHeaderLineResponse(@NonNull String headerLine, @NonNull ResponseInfo responseInfo)
			throws RtspInvalidSessionIdException, RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLineResponse()";

		if (parseHeaderLineCommon(headerLine)) {
			return;
		}

		if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_CSEQ)) {
			parseHeaderLine_cseq(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_OPT_PUBLIC)) {
			parseHeaderLine_options_public(headerLine, responseInfo);
		} else {
			logWarn(FNC_NAME, "Received unknown header: '" + headerLine + "'");
		}
	}

	private void parseHeaderLine_cseq(@NonNull String headerLine) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_cseq()";

		/*
		 * Example:
		 *   "CSeq: 6"
		 */
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

	private void parseHeaderLine_options_public(@NonNull String headerLine, @NonNull ResponseInfo responseInfo) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_options_public()";

		/*
		 * Example:
		 *   "Public: SETUP, PLAY, PAUSE, TEARDOWN, DESCRIBE, OPTIONS, SET_PARAMETER"
		 */
		String tmpOptionsStr = headerLine.substring(RTSP_RR_HEADER_TOKEN_OPT_PUBLIC.length()).strip();
		for (String tmpOption : tmpOptionsStr.split(",")) {
			tmpOption = tmpOption.strip();
			try {
				RtspProtoMessageType tmpEn = RtspProtoMessageType.valueOf(tmpOption);
				responseInfo.supportedMessageTypes.add(tmpEn);
			} catch (IllegalArgumentException e) {
				logWarn(FNC_NAME, "Invalid option: '" + tmpOption + "'");
			}
		}
	}

}
