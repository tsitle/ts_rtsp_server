package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.proto.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;

import java.util.ArrayList;
import java.util.List;

public class RtspProtoLowMsgWriter {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final boolean isDebugPrintRtspSent;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param rtxpTcpReadWrite RTxP TCP socket handler
	 * @param isDebugPrintRtspSent If true, then outgoing RTSP messages are logged
	 */
	public RtspProtoLowMsgWriter(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				boolean isDebugPrintRtspSent
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.isDebugPrintRtspSent = isDebugPrintRtspSent;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void writeMessage(@NonNull RtspProtoLowMsgRaw msgRaw) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".writeMessage()";

		List<String> outputLines = new ArrayList<>();

		/*
		 * Add the main (request/status) line.
		 * Example:
		 *   "GET_PARAMETER rtsp://admin:ABCDEFGH@192.168.1.1:1151/some.stream RTSP/1.0"
		 *  or
		 *   "RTSP/2.0 200 OK"
		 */
		outputLines.add(msgRaw.mainLine + RtspProtoLowMsgConstants.CRLF);

		/*
		 * Write all header lines.
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
		for (String tmpHdLine : msgRaw.headerLines) {
			outputLines.add(tmpHdLine + RtspProtoLowMsgConstants.CRLF);
		}
		outputLines.add(RtspProtoLowMsgConstants.CRLF);

		/*
		 * Write the message body.
		 * Example:
		 *   "v=0"
		 *   "o=mhandley 2890844526 IN IP4 126.16.64.4"
		 *   "s=SDP Seminar"
		 */
		if (! msgRaw.body.isBlank()) {
			outputLines.add(msgRaw.body);
		}

		// send the entire message
		if (isDebugPrintRtspSent) {
			for (String tmpLine : outputLines) {
				logDebug(FNC_NAME, "-------- " + tmpLine.replace(RtspProtoLowMsgConstants.CRLF, "<CRLF>"));
			}
		}
		rtxpTcpReadWrite.writeRtspLines(outputLines);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
