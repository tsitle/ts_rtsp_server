package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgReader;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class RtspProtoLowMsgReaderTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void rawResponse_rtpInfoHeaderLineRtspV1() throws Exception {
		final String expLine1 = "CSeq: 8";
		final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\";seq=5712;rtptime=934207921";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				"RTSP/1.0 200 OK\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));

		System.out.println(msgParsedRaw);
	}

	@Test
	void rawResponse_rtpInfoHeaderLineRtspV2() throws Exception {
		final String expLine1 = "CSeq: 8";
		final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\" " +
				"seq=5712:rtptime=934207921 ,url='rtsp://example.com/fizzle/videotrack' seq=8888:rtptime=8733749";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				"RTSP/2.0 200 OK\r\n" +
				expLine1 + "\r\n" +
				"RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\"\r\n" +
				"    seq=5712:rtptime=934207921\r\n" +
				"\t,url='rtsp://example.com/fizzle/videotrack' seq=8888:rtptime=8733749\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));

		System.out.println(msgParsedRaw);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void rawResponse_msgBody_correctContLen1_noCRLF() throws Exception {
		final String expLine1 = "Content-Type: xylo";
		final int expBodyLen = 62;
		final String expLine2 = "Content-Length: " + expBodyLen;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				"RTSP/2.0 200 OK\r\n" +
				expLine1 + "\r\n" +
				"Content-Length: 60" + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";  // <-- no CRLF at the end
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);
	}

	@Test
	void rawResponse_msgBody_correctContLen2_withCRLF() throws Exception {
		final String expLine1 = "Content-Type: xylo";
		final int expBodyLen = 62;
		final String expLine2 = "Content-Length: " + expBodyLen;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				"RTSP/2.0 200 OK\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar\r\n";  // <-- with CRLF at the end
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);
	}

	@Test
	void rawResponse_msgBody_tooShortContLen() throws Exception {
		final String expMainLine = "RTS-say-what-P/3.2 111111 No Idea What This Means";
		final String expLine1 = "Content-Type: xylo";
		final int expBodyLen = 62;
		final String expLine2 = "Content-Length: " + expBodyLen;
		final String expBodyStr = "v=0\r\no=mhandley 2890844526 IN IP4 126.16.64.4\r\ns=SDP Seminar\r\n";  // <-- with CRLF at the end

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				"Content-Length: 1000\r\n" +  // <-- incorrect value
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";  // <-- without CRLF
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyLen, msgParsedRaw.body.length());
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void rawRequest_options() throws Exception {
		final String expMainLine = "OPTIONS rtsp://some.com/stream RTSP/3.0";
		final String expLine1 = "Content-Type: xylo";
		final int expBodyLen = 62;
		final String expLine2 = "Content-Length: " + expBodyLen;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoLowMsgRaw sendMsgAndReadRaw(@NonNull String msgForSocketStr) throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);

			//java.time.Instant tmpNow = java.time.Instant.now();
			RtspProtoLowMsgRaw msgRaw = reader.readMessage();
			//System.out.println("Read time [ms]: " + (java.time.Instant.now().toEpochMilli() - tmpNow.toEpochMilli()));

			rw.closeSocket();

			return msgRaw;
		}
	}

}
