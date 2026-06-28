package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdStreamSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.ids.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpTransport;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.RtspProtoSdpDataMediaEntry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RtspProtoHighRequestConsumerTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class AvailableStreams implements RtspProtoAvailableStreamsInterface {
		@Override
		public boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource) {
			return idInputSource.getIdStr().orElse("-unset-").equals("existing_stream");
		}

		@Override
		public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
				throws RtspProtoIdInputSourceNotFoundException {
			if (! existsInputSourceId(idInputSource)) {
				throw new RtspProtoIdInputSourceNotFoundException("");
			}
			RtspProtoInputSource resObj = new RtspProtoInputSource();
			resObj.setIdInputSource(idInputSource);
			resObj.setEnabled(true);
			return resObj;
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstVideoStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			return Optional.empty();
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstAudioStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			return Optional.empty();
		}

		@Override
		public @NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource)
				throws RtspProtoIdStreamSourceNotFoundException {
			throw new RtspProtoIdStreamSourceNotFoundException("");
		}

		@Override
		public int getStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdStreamSource idStreamSource, double videoFps)
				throws RtspProtoIdStreamSourceNotFoundException {
			throw new RtspProtoIdStreamSourceNotFoundException("");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final static String TEST_SUB_STREAM_ID_PREFIX = "test_sub_stream_id_prefix";

	private RtspProtoIdSubStream generatedSubStreamId = RtspProtoIdSubStream.ofEmpty();
	private final RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc = buildGlobalSessionInfoSvc();

	RtspProtoHighRequestConsumerTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_announce_ok() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException, SrtxpSecurityException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.ANNOUNCE;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";
		final List<String> expSdpLines = List.of(
				"v=0",
				"o=- 1781786500001 1781786500002 IN IP4 127.0.0.1",
				"s=Just A Session",
				"i=demo.stream",
				"u=http://www.cs.ucl.ac.uk/staff/M.Handley/sdp.03.ps",
				"e=mjh@isi.edu (Mark Handley)",
				"p=001-555-123456",
				"t=0 0",
				"t=3034423619 3042462419",
				"r=604800 3600 0 90000",
				"r=7d 1h 0 25h",
				"c=IN IP4 224.2.1.1/127/3",
				"b=X-YZ:128",
				"z=2882844526 -1h 2898848070 0",
				"a=recvonly",
				"a=tool:TS RTSP Server/1.0",
				"a=type:broadcast",
				"a=control:*",
				"a=range:npt=0-",
				//
				"m=video 65535/987 RTP/SAVP 112 113 114",
				"i=The First Media",
				"c=IN IP4 224.2.1.5",
				"a=rtpmap:112 H264/90000",
				"a=rtpmap:113 H265/80000",
				"a=rtpmap:114 H266/70000",
				"a=fmtp:112 packetization-mode=1 some-other=non-sense",
				"a=control:substreamidf528764d_dbbd5deb",
				"a=key-mgmt:mikey AQAFAElq4SIBAAA6zCzrAAAAAAsA7d5mHVUyqcsKEP9Ynnh3rIApS64s5GNDOb4BAAAAHgABAQEBEAIBAQMBFA" +
						"QBDgUBAAcBAQgBAQoBAQsBCgAAACcAIQAepnM1qcQLCtXMUZ8imXhhE4K37b1PTMal3WhDgdssBAAAAAEA",
				//
				"m=audio 0 RTP/SAVP 101",
				"b=AS:128000",
				"a=rtpmap:101 L16/8000/1",
				"a=control:substreamidf528764d_081eb523",
				"a=crypto:707 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7",
				//
				"m=audio 1000 RTP/AVP 101",
				"a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7|2^31|123456789:4",
				//
				"m=audio 2000 RTP/AVP 101",
				"a=crypto:909 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7|2147483648|1234567890123456:8",
				//
				"m=application 32416 udp wb",
				"a=orient:portrait"
			);

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_TYPE);
			hdEntry.hdValContType.contentType = RtspMimeType.SDP;
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LEN);
			hdEntry.hdValContLen.contentLen.setLen32bit(100L);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_BASE);
			hdEntry.hdValContBase.contentBaseStr = expRequUrl + "/";
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LANG);
			hdEntry.hdValContLang.contentLangStr = "de";
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		msgStructured.bodyAnnounceSdp.addAllSdpLinesAllRaw(expSdpLines);

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));

		assertEquals(expRequUrl + "/", outputDataRequ.requAnnouncedSdpRaw.getContentBase());
		assertEquals("de", outputDataRequ.requAnnouncedSdpRaw.getContentLang());
		assertEquals(expSdpLines, outputDataRequ.requAnnouncedSdpRaw.getSdpLinesAllRaw());

		//System.out.println(outputDataRequ.requAnnouncedSdpStc);

		assertEquals(expRequUrl + "/", outputDataRequ.requAnnouncedSdpStc.getContentBase().orElseThrow());
		assertEquals("de", outputDataRequ.requAnnouncedSdpStc.getContentLang().orElseThrow());

		assertEquals("0", outputDataRequ.requAnnouncedSdpStc.getCommonVersion().orElseThrow());
		assertEquals("-", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().username());
		assertEquals("1781786500001", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().sessionId());
		assertEquals("1781786500002", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().version());
		assertEquals("IN", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().connInfo().nwType());
		assertEquals("IP4", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().connInfo().addrType());
		assertEquals("127.0.0.1", outputDataRequ.requAnnouncedSdpStc.getCommonOrigin().orElseThrow().connInfo().addrVal());
		assertEquals("Just A Session", outputDataRequ.requAnnouncedSdpStc.getCommonSessionName().orElseThrow());
		assertEquals("demo.stream", outputDataRequ.requAnnouncedSdpStc.getCommonSessionInfo().orElseThrow());
		assertEquals("http://www.cs.ucl.ac.uk/staff/M.Handley/sdp.03.ps", outputDataRequ.requAnnouncedSdpStc.getCommonUriDescr().orElseThrow());
		assertEquals("mjh@isi.edu (Mark Handley)", outputDataRequ.requAnnouncedSdpStc.getCommonEmail().orElseThrow());
		assertEquals("001-555-123456", outputDataRequ.requAnnouncedSdpStc.getCommonPhone().orElseThrow());
		assertEquals("IN", outputDataRequ.requAnnouncedSdpStc.getCommonConnInfo().orElseThrow().nwType());
		assertEquals("IP4", outputDataRequ.requAnnouncedSdpStc.getCommonConnInfo().orElseThrow().addrType());
		assertEquals("224.2.1.1/127/3", outputDataRequ.requAnnouncedSdpStc.getCommonConnInfo().orElseThrow().addrVal());
		assertEquals("X-YZ:128", outputDataRequ.requAnnouncedSdpStc.getCommonBandwidth().orElseThrow());
		assertEquals("2882844526 -1h 2898848070 0", outputDataRequ.requAnnouncedSdpStc.getCommonTzAdj().orElseThrow());
		assertEquals(5, outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().size());
		assertEquals("recvonly", outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().get(0));
		assertEquals("tool:TS RTSP Server/1.0", outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().get(1));
		assertEquals("type:broadcast", outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().get(2));
		assertEquals("control:*", outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().get(3));
		assertEquals("range:npt=0-", outputDataRequ.requAnnouncedSdpStc.getCommonSessionAttrs().get(4));
		assertEquals(2, outputDataRequ.requAnnouncedSdpStc.getCommonTimeActive().size());
		assertEquals("0 0", outputDataRequ.requAnnouncedSdpStc.getCommonTimeActive().get(0));
		assertEquals("3034423619 3042462419", outputDataRequ.requAnnouncedSdpStc.getCommonTimeActive().get(1));
		assertEquals(2, outputDataRequ.requAnnouncedSdpStc.getCommonRepeatTimes().size());
		assertEquals("604800 3600 0 90000", outputDataRequ.requAnnouncedSdpStc.getCommonRepeatTimes().get(0));
		assertEquals("7d 1h 0 25h", outputDataRequ.requAnnouncedSdpStc.getCommonRepeatTimes().get(1));

		// --------------------------------------------------------------------

		/*for (RtspProtoSdpDataMediaEntry mediaEntry : outputDataRequ.requAnnouncedSdpStc.getMediaEntries()) {
			System.out.println(mediaEntry);
		}*/

		assertEquals(5, outputDataRequ.requAnnouncedSdpStc.getMediaEntries().size());

		RtspProtoSdpDataMediaEntry mediaEntry = outputDataRequ.requAnnouncedSdpStc.getMediaEntries().getFirst();
		assertEquals(RtspProtoSdpMediaType.VIDEO, mediaEntry.header().mediaType());
		assertEquals(65535, mediaEntry.header().portNr().getPort16bit().orElse(0));
		assertEquals(987, mediaEntry.header().portCount());
		assertEquals(RtspProtoSdpTransport.RTP_SAVP, mediaEntry.header().transport());
		assertEquals(List.of("112", "113", "114"), mediaEntry.header().formatList());
		assertEquals("The First Media", mediaEntry.title());
		assertEquals("IN", mediaEntry.connectionInfo().nwType());
		assertEquals("IP4", mediaEntry.connectionInfo().addrType());
		assertEquals("224.2.1.5", mediaEntry.connectionInfo().addrVal());
		assertEquals("", mediaEntry.bandwidth());
		assertEquals(6, mediaEntry.attributes().size());
		assertEquals("rtpmap:112 H264/90000", mediaEntry.attributes().get(0));
		assertEquals("rtpmap:113 H265/80000", mediaEntry.attributes().get(1));
		assertEquals("rtpmap:114 H266/70000", mediaEntry.attributes().get(2));
		assertEquals("fmtp:112 packetization-mode=1 some-other=non-sense", mediaEntry.attributes().get(3));
		assertEquals("control:substreamidf528764d_dbbd5deb", mediaEntry.attributes().get(4));
		assertEquals("key-mgmt:mikey AQAFAElq4SIBAAA6zCzrAAAAAAsA7d5mHVUyqcsKEP9Ynnh3rIApS64s5GNDOb4BAAAAHgABA" +
				"QEBEAIBAQMBFAQBDgUBAAcBAQgBAQoBAQsBCgAAACcAIQAepnM1qcQLCtXMUZ8imXhhE4K37b1PTMal3WhDgdssBAAAAAEA",
				mediaEntry.attributes().get(5));
		SrtxpKmd srtxpKmd = outputDataRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(mediaEntry).orElseThrow();
		assertFalse(srtxpKmd.getMetaIsForLegacySdes());
		assertFalse(srtxpKmd.getMetaTagForLegacySdes().isPresent());
		assertEquals(
				RtspProtoIdSubStream.of("substreamidf528764d_dbbd5deb"),
				mediaEntry.controlId()
			);

		mediaEntry = outputDataRequ.requAnnouncedSdpStc.getMediaEntries().get(1);
		assertEquals(RtspProtoSdpMediaType.AUDIO, mediaEntry.header().mediaType());
		assertEquals(0, mediaEntry.header().portNr().getPort16bit().orElse(0));
		assertEquals(1, mediaEntry.header().portCount());
		assertEquals(RtspProtoSdpTransport.RTP_SAVP, mediaEntry.header().transport());
		assertEquals(List.of("101"), mediaEntry.header().formatList());
		assertEquals("", mediaEntry.title());
		assertTrue(mediaEntry.connectionInfo().isEmpty());
		assertEquals("", mediaEntry.connectionInfo().nwType());
		assertEquals("", mediaEntry.connectionInfo().addrType());
		assertEquals("", mediaEntry.connectionInfo().addrVal());
		assertEquals("AS:128000", mediaEntry.bandwidth());
		assertEquals(3, mediaEntry.attributes().size());
		assertEquals("rtpmap:101 L16/8000/1", mediaEntry.attributes().get(0));
		assertEquals("control:substreamidf528764d_081eb523", mediaEntry.attributes().get(1));
		assertEquals("crypto:707 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7",
				mediaEntry.attributes().get(2));
		srtxpKmd = outputDataRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(mediaEntry).orElseThrow();
		assertTrue(srtxpKmd.getMetaIsForLegacySdes());
		assertEquals(707, srtxpKmd.getMetaTagForLegacySdes().orElseThrow());
		assertTrue(srtxpKmd.mki().isEmpty());
		assertTrue(srtxpKmd.kdr().isEmpty());
		assertEquals(
				RtspProtoIdSubStream.of("substreamidf528764d_081eb523"),
				mediaEntry.controlId()
			);

		mediaEntry = outputDataRequ.requAnnouncedSdpStc.getMediaEntries().get(2);
		assertEquals(1000, mediaEntry.header().portNr().getPort16bit().orElse(0));
		assertEquals(1, mediaEntry.header().portCount());
		assertEquals(RtspProtoSdpTransport.RTP_AVP, mediaEntry.header().transport());
		assertEquals(List.of("101"), mediaEntry.header().formatList());
		assertEquals(1, mediaEntry.attributes().size());
		assertEquals("crypto:1 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7|2^31|123456789:4",
				mediaEntry.attributes().getFirst());
		srtxpKmd = outputDataRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(mediaEntry).orElseThrow();
		assertTrue(srtxpKmd.getMetaIsForLegacySdes());
		assertEquals(1, srtxpKmd.getMetaTagForLegacySdes().orElseThrow());
		assertEquals(BufferExt.decodeHexString("0xB4653A2F8BEE8BBBC5BE26BF6CFA3AB9"), srtxpKmd.masterKey());
		assertEquals(BufferExt.decodeHexString("0x78970E907F0FD858DF47ECE3E53B"), srtxpKmd.masterSalt());
		assertEquals(123456789L, srtxpKmd.mki().getValue().orElseThrow());
		assertEquals(Math.powExact(2L, 31), srtxpKmd.kdr().getValue().orElseThrow());

		mediaEntry = outputDataRequ.requAnnouncedSdpStc.getMediaEntries().get(3);
		assertEquals(2000, mediaEntry.header().portNr().getPort16bit().orElse(0));
		assertEquals(1, mediaEntry.header().portCount());
		assertEquals(RtspProtoSdpTransport.RTP_AVP, mediaEntry.header().transport());
		assertEquals(List.of("101"), mediaEntry.header().formatList());
		assertEquals(1, mediaEntry.attributes().size());
		assertEquals("crypto:909 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7|2147483648|1234567890123456:8",
				mediaEntry.attributes().getFirst());
		srtxpKmd = outputDataRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(mediaEntry).orElseThrow();
		assertEquals(1234567890123456L, srtxpKmd.mki().getValue().orElseThrow());
		assertEquals(2147483648L, srtxpKmd.kdr().getValue().orElseThrow());

		mediaEntry = outputDataRequ.requAnnouncedSdpStc.getMediaEntries().get(4);
		assertEquals(RtspProtoSdpMediaType.APPLICATION, mediaEntry.header().mediaType());
		assertEquals(32416, mediaEntry.header().portNr().getPort16bit().orElse(0));
		assertEquals(1, mediaEntry.header().portCount());
		assertEquals(RtspProtoSdpTransport.UDP, mediaEntry.header().transport());
		assertEquals(List.of("wb"), mediaEntry.header().formatList());
		assertEquals("", mediaEntry.title());
		assertTrue(mediaEntry.connectionInfo().isEmpty());
		assertEquals("", mediaEntry.bandwidth());
		assertEquals(1, mediaEntry.attributes().size());
		assertEquals("orient:portrait", mediaEntry.attributes().getFirst());

		// --------------------------------------------------------------------

		mediaEntry = outputDataRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(RtspProtoSdpMediaType.VIDEO).orElseThrow();
		assertEquals(3, mediaEntry.header().formatList().size());
		String meFmt = mediaEntry.header().formatList().getFirst();
		String meRtpMap = outputDataRequ.requAnnouncedSdpStc.extractMediaEntryRtpMapForFormat(mediaEntry, meFmt).orElseThrow();
		String meFmtp = outputDataRequ.requAnnouncedSdpStc.extractMediaEntryFormatSpecificParamsForFormat(mediaEntry, meFmt).orElseThrow();
		assertEquals("H264/90000", meRtpMap);
		assertEquals("packetization-mode=1 some-other=non-sense", meFmtp);

		// --------------------------------------------------------------------

		mediaEntry = outputDataRequ.requAnnouncedSdpStc
				.findMediaEntryForControlId(RtspProtoIdSubStream.of("substreamidf528764d_081eb523"))
				.orElseThrow();
		assertEquals("AS:128000", mediaEntry.bandwidth());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_describe_wrongCseq() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.DESCRIBE;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(101L);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataCntCseqRequInp ioCseqRequ = new RtspProtoDataCntCseqRequInp();
		ioCseqRequ.cseqNr_expected.setCseq32bit(102L);  // <-- CSeq is higher than the value in the message
		ioCseqRequ.cseqNr_lastRcvd.clear();  // <-- this is not an input value

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of("doesnt matter when cseq is wrong"),
				ioCseqRequ,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, requBasics.statusCode);

		assertTrue(outputDataRequ.rrCseqNrLastRcvd.getCseq32bit().isEmpty());
	}

	@Test
	void structuredRequest_describe_ok_higherCseq() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.DESCRIBE;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(101L);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataCntCseqRequInp ioCseqRequ = new RtspProtoDataCntCseqRequInp();
		ioCseqRequ.cseqNr_expected.setCseq32bit(71L);  // <-- CSeq is lower than the value in the message
		ioCseqRequ.cseqNr_lastRcvd.clear();  // <-- this is not an input value

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				ioCseqRequ,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));

		assertEquals(101L, outputDataRequ.rrCseqNrLastRcvd.getCseq32bit().orElseThrow());
	}

	@Test
	void structuredRequest_describe_ok() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.DESCRIBE;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataCntCseqRequInp ioCseqRequ = new RtspProtoDataCntCseqRequInp();
		ioCseqRequ.cseqNr_expected.setCseq32bit(expCseqLong);  // <-- CSeq is equal to the value in the message
		ioCseqRequ.cseqNr_lastRcvd.clear();  // <-- this is not an input value

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(""),
				ioCseqRequ,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expCseqLong, outputDataRequ.rrCseqNrLastRcvd.getCseq32bit().orElseThrow());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_getParam_ok_unknownParam() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_TYPE);
			hdEntry.hdValContType.contentType = RtspMimeType.PARAMETERS;
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LEN);
			hdEntry.hdValContLen.contentLen.setLen32bit(1000L);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		msgStructured.bodyGetParamNames.putParamName("strange_param");
		msgStructured.bodyGetParamNames.putParamName("weird");

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));

		assertEquals(Set.of("strange_param", "weird"), outputDataRequ.rrGetParamNames.getParamNames());
	}

	@Test
	void structuredRequest_getParam_ok() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.GET_PARAMETER;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_options_missingCseq() throws RtspProtoSessionInfoException, RtspProtoNumberRangeException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expSessionId = "";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr("this is a different session id but it will not be stored");
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse(""));
	}

	@Test
	void structuredRequest_options_notFound() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "192837465";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));
	}

	@Test
	void structuredRequest_options_featureNotSupp() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://existing.mil/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspHeaderKey hdKeyEn = RtspHeaderKey.CSEQ;
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(hdKeyEn);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdKeyEn, hdEntry);
		}
		{
			RtspHeaderKey hdKeyEn = RtspHeaderKey.REQUIRE;
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(hdKeyEn);
			hdEntry.hdValRequire.requiredFeatures.add("this_does_not_exist");
			msgStructured.headers.put(hdKeyEn, hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(""),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, requBasics.statusCode);

		assertTrue(outputDataRequ.rrIdSession.isEmpty());

		assertEquals("this_does_not_exist", outputDataRequ.getUnsupportedFeatureName());
	}

	@Test
	void structuredRequest_options_ok() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));

		assertEquals(expRequUrl, requBasics.rscUrl.getUrlStr());
		assertEquals("existing_stream", requBasics.rscUrl.idInputSource.getIdStr().orElse("-unset-"));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_pause() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		RtspProtoMessageType msgTp = RtspProtoMessageType.PAUSE;
		test_pause_play_teardown(msgTp, RtspProtoSessionState.INIT, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.READY, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.PLAYING, RtspProtoStatusCode.OK);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_play() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		RtspProtoMessageType msgTp = RtspProtoMessageType.PLAY;
		test_pause_play_teardown(msgTp, RtspProtoSessionState.INIT, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.READY, RtspProtoStatusCode.OK);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.PLAYING, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_setup_subStreamNotFound() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/" + TEST_SUB_STREAM_ID_PREFIX + "notexists";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = 707L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.ofEmpty(),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertTrue(requBasics.rscUrl.isEmpty());
		assertTrue(requBasics.rscUrl.idInputSource.isEmpty());
		assertTrue(requBasics.rscUrl.idSubStream.isEmpty());
	}

	@Test
	void structuredRequest_setup_invalidSubStreamPrefix() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/we_have_no_proper_substreamidprefix-" +
				generatedSubStreamId.getIdStr().orElse("-unset-");
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = 707L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.ofEmpty(),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertTrue(requBasics.rscUrl.isEmpty());
		assertTrue(requBasics.rscUrl.idInputSource.isEmpty());
		assertTrue(requBasics.rscUrl.idSubStream.isEmpty());
	}

	@Test
	void structuredRequest_setup_ok_queryParamSrtp() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/" +
				generatedSubStreamId.getIdStr().orElse("-unset-") +
				"?" + RtspProtoHighConstants.URL_QUERY_PARAM_SRTP + "=1";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;
		msgStructured.queryParams.put(RtspProtoHighConstants.URL_QUERY_PARAM_SRTP, "1");

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.ofEmpty(),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expRequUrl, requBasics.rscUrl.getUrlStr());
		assertEquals("existing_stream", requBasics.rscUrl.idInputSource.getIdStr().orElse("-unset-"));
		assertEquals(generatedSubStreamId, requBasics.rscUrl.idSubStream);
		assertTrue(outputDataRequ.rrStreamTpMain.getForceRtpRtcpEncryption());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_teardown() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		RtspProtoMessageType msgTp = RtspProtoMessageType.TEARDOWN;
		test_pause_play_teardown(msgTp, RtspProtoSessionState.INIT, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.READY, RtspProtoStatusCode.OK);
		test_pause_play_teardown(msgTp, RtspProtoSessionState.PLAYING, RtspProtoStatusCode.OK);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_xxx_missingSessionId() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		Set<RtspProtoMessageType> mts = Set.of(
				RtspProtoMessageType.GET_PARAMETER,
				RtspProtoMessageType.PAUSE,
				RtspProtoMessageType.PLAY,
				RtspProtoMessageType.SET_PARAMETER,
				RtspProtoMessageType.TEARDOWN
			);
		for (RtspProtoMessageType mt : mts) {
			test_missingSessionId(mt, RtspProtoStatusCode.SESSION_NOT_FOUND);
		}

		mts = Set.of(
				RtspProtoMessageType.DESCRIBE,
				RtspProtoMessageType.OPTIONS,
				RtspProtoMessageType.REDIRECT
			);
		for (RtspProtoMessageType mt : mts) {
			test_missingSessionId(mt, RtspProtoStatusCode.OK);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void test_missingSessionId(RtspProtoMessageType expMsgType, RtspProtoStatusCode expStatCode)
			throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				RtspProtoSessionState.INIT,
				msgStructured,
				RtspProtoIdSession.of("asasd"),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(expStatCode, requBasics.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	void test_pause_play_teardown(RtspProtoMessageType msgTp, RtspProtoSessionState sessionState, RtspProtoStatusCode expStatCode)
			throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final String expRequUrl = "rtsp://some.com/existing_stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";
		final String expPlayRange = "npt=7.123-9.876";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = msgTp;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(expSessionId);
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		if (msgTp == RtspProtoMessageType.PLAY) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.RANGE);
			hdEntry.hdValRange.rangeStr = expPlayRange;
			msgStructured.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// --------------------------------

		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				sessionState,
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(msgTp, requBasics.messageType);
		assertEquals(expStatCode, requBasics.statusCode);

		if (expStatCode == RtspProtoStatusCode.OK) {
			assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr().orElse("-unset-"));
			assertEquals(expRequUrl, requBasics.rscUrl.getUrlStr());
			assertEquals("existing_stream", requBasics.rscUrl.idInputSource.getIdStr().orElse("-unset-"));

			if (msgTp == RtspProtoMessageType.PLAY) {
				assertEquals(expPlayRange, outputDataRequ.getPlaybackRangeValue());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics wrapperProcessRequest(
				@NonNull RtspProtoSessionState sessionState,
				@NonNull RtspProtoHighMsgStructuredRequest inputMsgStructured,
				@NonNull RtspProtoIdSession currentSessionId,
				@NonNull RtspProtoDataCntCseqRequInp ioCseqRequ,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoSessionInfoException, RtspProtoNumberRangeException {
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo(sessionState);
		RtspProtoDataCntStreamTpMain inpStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		if (! generatedSubStreamId.isEmpty()) {
			RtspProtoRscUrl tmpRscUrlObj = RtspProtoRscUrl.of(
					inputMsgStructured.resourceUrl,
					RtspProtoIdInputSource.of("someInSo"),
					generatedSubStreamId
				);
			ioSetupInfosStream.createAndAddSetupSubStream(
					tmpRscUrlObj,
					RtspProtoIdXsrc.of(1001L),
					new RtspProtoKmdForSubStream()
				);
		}
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		return proc.processRequest(
				currentSessionId,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				sessionInfo.getClientIpAddr(),
				ioCseqRequ,
				ioSetupInfosStream,
				Set.of(generatedSubStreamId),
				inpStreamTpMain,
				inputMsgStructured,
				outputDataRequ
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull LogMsgInterface buildLogMsgIf() {
		return new TestLogs();
	}

	private static @NonNull RtspProtoSessionInfo buildRtspSessionInfo(@NonNull RtspProtoSessionState sessionState)
			throws RtspProtoSessionInfoException {
		RtspProtoSessionInfo resObj = new RtspProtoSessionInfo();

		RtspProtoIpAddr tmpIp = RtspProtoIpAddr.ofLoopback();
		resObj.setClientIpAddr(tmpIp);
		//
		resObj.setIsRtspsConnection(false);
		//
		if (sessionState == RtspProtoSessionState.PLAYING) {
			resObj.moveToNextSessionState(RtspProtoMessageType.PLAY);
		} else if (sessionState == RtspProtoSessionState.READY) {
			resObj.moveToNextSessionState(RtspProtoMessageType.SETUP);
		}
		return resObj;
	}

	private static @NonNull RtspProtoSdpConsumerInterface buildSdpConsumer() {
		return new RtspProtoSdpConsumer();
	}

	private @NonNull RtspProtoGlobalSessionInfoSvc buildGlobalSessionInfoSvc() {
		RtspProtoGlobalSessionInfoSvc resObj = new RtspProtoGlobalSessionInfoSvc();
		generatedSubStreamId = resObj.createSubStreamId(
				TEST_SUB_STREAM_ID_PREFIX,
				RtspProtoIdInputSource.of("existing_stream"),
				RtspProtoIdStreamSource.of("exists_12345_streamsource"),
				RtspProtoIpAddr.ofLoopback()
			);
		return resObj;
	}

	private @NonNull RtspProtoHighRequestConsumer buildRtspProtoHighRequestConsumer() {
		final Set<RtspProtoMessageType> TEMP_SUPPORTED_INCOMING_MESSAGE_TYPES = Set.of(
				RtspProtoMessageType.ANNOUNCE,
				RtspProtoMessageType.DESCRIBE,
				RtspProtoMessageType.GET_PARAMETER,
				RtspProtoMessageType.OPTIONS,
				RtspProtoMessageType.PAUSE,
				RtspProtoMessageType.PLAY,
				RtspProtoMessageType.REDIRECT,
				RtspProtoMessageType.SET_PARAMETER,
				RtspProtoMessageType.SETUP,
				RtspProtoMessageType.TEARDOWN
			);

		RtspProtoDataCntMessageTypes cfgSrvSuppIncomingMts = new RtspProtoDataCntMessageTypes();
		cfgSrvSuppIncomingMts.putAllMts(TEMP_SUPPORTED_INCOMING_MESSAGE_TYPES);
		cfgSrvSuppIncomingMts.writeProtect();

		final boolean cfgIsDebugDisableTransportUdp = false;

		return new RtspProtoHighRequestConsumer(
				buildLogMsgIf(),
				true,
				cfgSrvSuppIncomingMts,
				Set.of(),
				Set.of(),
				cfgIsDebugDisableTransportUdp,
				buildSdpConsumer(),
				new AvailableStreams(),
				globalSessionInfoSvc,
				null
			);
	}

}
