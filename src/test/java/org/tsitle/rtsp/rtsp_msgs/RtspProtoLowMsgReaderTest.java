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
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(logMsgIf, tcpSocket, true);

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
			assertEquals(3, msgParsedRaw.headerLines.size());

			final String expLine1 = "CSeq: 8";
			final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\";seq=5712;rtptime=934207921";
			final String expLine3 = "";

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
			assertEquals(expLine3, msgParsedRaw.headerLines.get(2));

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
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(logMsgIf, tcpSocket, true);

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
			assertTrue(msgParsedRaw.headerLines.stream().anyMatch(h -> h.startsWith("RTP-Info:")));
			assertEquals(3, msgParsedRaw.headerLines.size());

			final String expLine1 = "CSeq: 8";
			final String expLine2 = "RTP-Info: url=\"rtsp://example.com/fizzle/audiotrack\" seq=5712:rtptime=934207921 ,url='rtsp://example.com/fizzle/videotrack' seq=8888:rtptime=8733749";
			final String expLine3 = "";

			assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
			assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
			assertEquals(expLine3, msgParsedRaw.headerLines.get(2));

			System.out.println(msgParsedRaw);

			rw.closeSocket();
		}
	}

}
