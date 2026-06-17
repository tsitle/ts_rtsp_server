package org.tsitle.lib.rtsp.proto.msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.lib.rtsp.proto.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.lib.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.lib.rtsp.proto.lowlevel.RtspMimeType;
import org.tsitle.lib.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoStatusCode;
import org.tsitle.lib.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib.rtsp.proto.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.lib.rtsp.proto.lowlevel.request.RtspProtoLowRequestConsumer;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RtspProtoLowRequestConsumerTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_generic_mainLine_fail1() throws Exception {
		final String expMainLine = "OPTIONS rtsp://some.com/stream RTSP/3.0";  // <-- wrong RTSP version

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"CSeq: 1\r\n" +
				"Content-Type: " + RtspMimeType.SDP.getStrValue() + "\r\n" +
				"Content-Length: 62\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(RtspProtoMessageType.UNKNOWN, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
	}

	@Test
	void structuredRequest_generic_mainLine_fail2() throws Exception {
		final String expMainLine = "XOPTX rtsp://some.com/stream RTSP/1.0";  // <-- wrong METHOD

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"Content-Type: " + RtspMimeType.SDP.getStrValue() + "\r\n" +
				"Content-Length: 62\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(RtspProtoMessageType.UNKNOWN, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_ALLOWED, msgStructured.statusCode);
	}

	@Test
	void structuredRequest_generic_mainLine_fail3() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expMainLine = expMsgType.name() + " http://some.com/stream RTSP/1.0";  // <-- wrong URL-Protocol

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"Content-Type: " + RtspMimeType.SDP.getStrValue() + "\r\n" +
				"Content-Length: 62\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_generic_cseq_fail1() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expMainLine = expMsgType.name() + " rtsp://some.com/stream RTSP/1.0";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(0, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
	}

	@Test
	void structuredRequest_generic_cseq_fail2() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expMainLine = expMsgType.name() + " rtsp://some.com/stream RTSP/1.0";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"CSeq: -1" + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(1, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
	}

	@Test
	void structuredRequest_generic_cseq_ok() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expMainLine = expMsgType.name() + " rtsps://some.com/stream RTSP/2.0";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				"CSeq: " + Long.toUnsignedString(4294967295L) + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(1, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);

		System.out.println(msgStructured);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_announce_wrongContType() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.ANNOUNCE;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE * 2L;
		final String expLine1 = "cseq:" + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "content-type:" + RtspMimeType.PARAMETERS.getStrValue();
		final int expBodyLen = 48;
		final String expLine3 = "content-length:" + expBodyLen;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue() + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n" +
				"Key1: Value1:With:Colons\r\n" +
				"Key2:\"Extreme'Value\"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());
	}

	@Test
	void structuredRequest_announce_ok() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.ANNOUNCE;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE * 2L;
		final String expLine1 = "cseq:" + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "content-type:" + RtspMimeType.SDP.getStrValue();
		final int expBodyLen = 62;
		final String expLine3 = "content-length:" + expBodyLen;
		final String expBodyStr = "v=0\r\no=mhandley 2890844526 IN IP4 126.16.64.4\r\ns=SDP Seminar\r\n";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue() + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		assertEquals(3, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(Arrays.asList(expBodyStr.split("\\r\\n")), msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());

		System.out.println(msgStructured);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_describe_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.DESCRIBE);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_getParam_ok_wrongContTypeButNoContLength() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.SDP.getStrValue();
		final String expBodyStr = "";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";  // <-- no CRLF at the end
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		System.out.println(msgStructured);
	}

	@Test
	void structuredRequest_getParam_missingContType() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final int expBodyLen = 62;
		final String expLine2 = "Content-Length: " + expBodyLen;
		final String expBodyStr = "v=0\r\no=mhandley 2890844526 IN IP4 126.16.64.4\r\ns=SDP Seminar\r\n";  // <-- with CRLF at the end

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
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

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expBodyLen, msgParsedRaw.body.length());
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());
	}

	@Test
	void structuredRequest_getParam_wrongContType() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.SDP;
		final int expBodyLen = 62;
		final String expLine3 = "Content-Length: " + expBodyLen;
		final String expBodyStr = "v=0\r\no=mhandley 2890844526 IN IP4 126.16.64.4\r\ns=SDP Seminar\r\n";  // <-- with CRLF at the end

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"Content-Length: 60" + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";  // <-- no CRLF at the end
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());
	}

	@Test
	void structuredRequest_getParam_ok_no_keys() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.PARAMETERS.getStrValue();
		final int expBodyLen = 0;
		final String expLine3 = "Content-Length: " + expBodyLen;
		final String expBodyStr = "";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());

		System.out.println(msgStructured);
	}

	@Test
	void structuredRequest_getParam_ok_with_keys() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsps://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.PARAMETERS.getStrValue();
		final int expBodyLen = 18;
		final String expLine3 = "Content-Length: " + expBodyLen;
		final Set<String> expBodySet = Set.of("Key1", "Key2", "kEY3");

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n" +
				"Key1\r\n" +
				"Key2\r\n" +
				"kEY3\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(3, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(expBodySet, msgStructured.bodyGetParamNames.getParamNames());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());

		System.out.println(msgStructured);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_options_ok_bodyNotAllowed() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.SDP.getStrValue();
		final int expBodyLen = 62;
		final String expLine3 = "Content-Length: " + expBodyLen;
		final String expBodyStr = "v=0\r\no=mhandley 2890844526 IN IP4 126.16.64.4\r\ns=SDP Seminar\r\n";  // <-- with CRLF at the end

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"Content-Length: 60" + "\r\n" +
				"\r\n" +
				"v=0\r\n" +
				"o=mhandley 2890844526 IN IP4 126.16.64.4\r\n" +
				"s=SDP Seminar";  // <-- no CRLF at the end
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());
		assertEquals(expBodyStr, msgParsedRaw.body);

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());
	}

	@Test
	void structuredRequest_options_ok() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expMainLine = expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue();
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.SDP.getStrValue();
		final int expBodyLen = 0;
		final String expLine3 = "Content-Length: " + expBodyLen;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMainLine + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expMainLine, msgParsedRaw.mainLine);
		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		System.out.println(msgStructured);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_pause_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.PAUSE);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_play_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.PLAY);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_redirect_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.REDIRECT);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_setParam_ok_noKeys() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue() + "\r\n" +
				expLine1 + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(1, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.getFirst());
		assertEquals(0, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());

		System.out.println(msgStructured);
	}

	@Test
	void structuredRequest_setParam_ok_withKeys() throws Exception {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expLine1 = "CSeq: " + Long.toUnsignedString(expCseqLong);
		final String expLine2 = "Content-Type: " + RtspMimeType.PARAMETERS.getStrValue();
		final int expBodyLen = 48;
		final String expLine3 = "Content-Length: " + expBodyLen;
		final Map<String, String> expBodyMap = Map.of(
				"Key1", "Value1:With:Colons",
				"Key2", "ExtremeValue"
			);
		final Map<String, String> expQueryParams = Map.of(
				"koko", "napal",
				"ratta", "tui"
			);
		final String expAuthUser = "peTEr";
		final String expAuthPlainPw = "PaN";

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMsgType.name() + " rtsp://" + expAuthUser + ":" + expAuthPlainPw + "@some.com/stream?koko=napal&ratta=tui " + expProtoVer.getStrValue() + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				expLine3 + "\r\n" +
				"\r\n" +
				"Key1: Value1:With:Colons\r\n" +
				"Key2:\"Extreme'Value\"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(3, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.get(0));
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(expLine3, msgParsedRaw.headerLines.get(2));
		assertEquals(expBodyLen, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());
		assertEquals(expQueryParams, msgStructured.queryParams);
		assertEquals(expAuthUser, msgStructured.authUser);
		assertEquals(expAuthPlainPw, msgStructured.authPlainPassword);

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(2, msgStructured.bodySetParamKv.getParamKvsKeySet().size());
		assertEquals(expBodyMap.entrySet(), msgStructured.bodySetParamKv.getParamKvsEntrySet());

		System.out.println(msgStructured);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_setup_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.SETUP);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_teardown_ok() throws Exception {
		basic_structuredRequest_ok(RtspProtoMessageType.TEARDOWN);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull LogMsgInterface buildLogMsgIf() {
		return new TestLogs();
	}

	private static @NonNull RtspProtoLowMsgRaw sendMsgAndReadRaw(@NonNull String msgForSocketStr) throws Exception {
		try (ServerSocket serverSocket = new ServerSocket(0);
				Socket tcpSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
				Socket peerSocket = serverSocket.accept()) {

			tcpSocket.setSoTimeout(50);  // only for read()

			//
			RtxpTcpReadWrite rw = new RtxpTcpReadWrite(tcpSocket);

			//
			peerSocket.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
			peerSocket.getOutputStream().flush();

			//
			RtspProtoLowMsgReader reader = new RtspProtoLowMsgReader(buildLogMsgIf(), rw, true);
			RtspProtoLowMsgRaw msgRaw = reader.readMessage();

			rw.closeSocket();

			return msgRaw;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	void basic_structuredRequest_ok(@NonNull RtspProtoMessageType expMsgType) throws Exception {
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE * 2L;
		final String expLine1 = "cseq:" + Long.toUnsignedString(expCseqLong);
		final String expSessId = "BEEF1234";
		final String expLine2 = "SeSsIoN:" + expSessId;

		@SuppressWarnings("TextBlockMigration")
		String msgForSocketStr =
				expMsgType.name() + " " + expRequUrl + " " + expProtoVer.getStrValue() + "\r\n" +
				expLine1 + "\r\n" +
				expLine2 + "\r\n" +
				"\r\n";
		RtspProtoLowMsgRaw msgParsedRaw = sendMsgAndReadRaw(msgForSocketStr);

		//
		assertTrue(msgParsedRaw.readSuccess);
		assertEquals(2, msgParsedRaw.headerLines.size());

		assertEquals(expLine1, msgParsedRaw.headerLines.getFirst());
		assertEquals(expLine2, msgParsedRaw.headerLines.get(1));
		assertEquals(0, msgParsedRaw.body.length());

		System.out.println(msgParsedRaw);

		// --------------------------------

		//
		RtspProtoLowRequestConsumer parser = new RtspProtoLowRequestConsumer(buildLogMsgIf());
		RtspProtoHighMsgStructuredRequest msgStructured = parser.parseMessage(msgParsedRaw);

		assertEquals(expMsgType, msgStructured.messageType);
		assertEquals(RtspProtoStatusCode.OK, msgStructured.statusCode);
		assertEquals(expRequUrl, msgStructured.resourceUrl);
		assertEquals(expProtoVer, msgStructured.rtspProtoVersion);
		assertEquals(expCseqLong, msgStructured.getHeaderCseq().orElseThrow());
		assertEquals(expSessId, msgStructured.getHeaderSessionId().orElseThrow().getIdStr());

		assertEquals(0, msgStructured.bodyAnnounceSdp.getSdpLinesAllRaw().size());
		assertEquals(0, msgStructured.bodyGetParamNames.getParamNames().size());
		assertEquals(0, msgStructured.bodySetParamKv.getParamKvsKeySet().size());

		System.out.println(msgStructured);
	}

}
