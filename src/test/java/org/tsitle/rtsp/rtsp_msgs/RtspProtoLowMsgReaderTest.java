package org.tsitle.rtsp.rtsp_msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgReader;

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

	@Test
	void testRtpInfoHeaderLineRtspV1() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			String msgForSocketStr =
					"""
					RTSP/1.0 200 OK\r
					CSeq: 8\r
					RTP-Info: url="rtsp://example.com/fizzle/audiotrack";seq=5712;rtptime=934207921\r
					\r
					""";
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);
			RtspProtoLowMsgRaw msgParsedRaw = reader.readMessage();

			assertTrue(msgParsedRaw.readSuccess);
			assertEquals(2, msgParsedRaw.headerLines.size());

			final String expLine1 = "CSeq: 8";
			final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\";seq=5712;rtptime=934207921";

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

	@Test
	void testRtpInfoHeaderLineRtspV2() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			@SuppressWarnings("TextBlockMigration") String msgForSocketStr = "RTSP/2.0 200 OK\r\n" +
					"CSeq: 8\r\n" +
					"RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\"\r\n" +
					"    seq=5712:rtptime=934207921\r\n" +
					"\t,url='rtsp://example.com/fizzle/videotrack' seq=8888:rtptime=8733749\r\n" +
					"\r\n";
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);
			RtspProtoLowMsgRaw msgParsedRaw = reader.readMessage();

			assertTrue(msgParsedRaw.readSuccess);
			assertEquals(2, msgParsedRaw.headerLines.size());

			final String expLine1 = "CSeq: 8";
			final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\" seq=5712:rtptime=934207921 ,url='rtsp://example.com/fizzle/videotrack' seq=8888:rtptime=8733749";

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

	@Test
	void testMsgBody_correctContLen1() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			@SuppressWarnings("TextBlockMigration") String msgForSocketStr = "RTSP/2.0 200 OK\r\n" +
					"Content-Type: xylo\r\n" +
					"Content-Length: 60\r\n" +
					"\r\n" +
					"v=0\r\n" +
					"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
					"s=SDP Seminar";  // <-- no CRLF at the end
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);
			RtspProtoLowMsgRaw msgParsedRaw = reader.readMessage();

			assertTrue(msgParsedRaw.readSuccess);
			assertEquals(2, msgParsedRaw.headerLines.size());

			final String expLine1 = "Content-Type: xylo";
			final String expLine2 = "Content-Length: 60";
			final int expBodyLen = 60;

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
			assertEquals(expBodyLen, msgParsedRaw.body.length());

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

	@Test
	void testMsgBody_correctContLen2() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			@SuppressWarnings("TextBlockMigration") String msgForSocketStr = "RTSP/2.0 200 OK\r\n" +
					"Content-Type: xylo\r\n" +
					"Content-Length: 60\r\n" +
					"\r\n" +
					"v=0\r\n" +
					"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
					"s=SDP Seminar\r\n";  // <-- with CRLF at the end
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);
			RtspProtoLowMsgRaw msgParsedRaw = reader.readMessage();

			assertTrue(msgParsedRaw.readSuccess);
			assertEquals(2, msgParsedRaw.headerLines.size());

			final String expLine1 = "Content-Type: xylo";
			final String expLine2 = "Content-Length: 60";
			final int expBodyLen = 60;

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
			assertEquals(expBodyLen, msgParsedRaw.body.length());

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

	@Test
	void testMsgBody_tooShortContLen() throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			TestLogs logMsgIf = new TestLogs();
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			@SuppressWarnings("TextBlockMigration") String msgForSocketStr = "RTSP/2.0 200 OK\r\n" +
					"Content-Type: xylo\r\n" +
					"Content-Length: 1000\r\n" +
					"\r\n" +
					"v=0\r\n" +
					"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
					"s=SDP Seminar";
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(logMsgIf, rw, true);
			RtspProtoLowMsgRaw msgParsedRaw = reader.readMessage();

			assertTrue(msgParsedRaw.readSuccess);
			assertEquals(2, msgParsedRaw.headerLines.size());

			final String expLine1 = "Content-Type: xylo";
			final String expLine2 = "Content-Length: 62";
			final int expBodyLen = 62;

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
			assertEquals(expBodyLen, msgParsedRaw.body.length());

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

}
