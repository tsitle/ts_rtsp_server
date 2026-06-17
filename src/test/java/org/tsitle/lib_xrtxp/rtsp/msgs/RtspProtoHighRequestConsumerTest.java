package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdStreamSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoStreamSource;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RtspProtoHighRequestConsumerTest {

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
			return idInputSource.getIdStr().equals("existing_stream");
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

	private RtspProtoIdSubStream generatedSubStreamId = RtspProtoIdSubStream.ofEmpty();
	private final RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc = buildGlobalSessionInfoSvc();

	public RtspProtoHighRequestConsumerTest() throws RtspProtoSessionInfoException { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_announce_xxx() {
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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				ioCseqRequ,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_options_missingCseq() throws RtspProtoSessionInfoException {
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

		//
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = wrapperProcessRequest(
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());
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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());
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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

		assertEquals(expRequUrl, requBasics.rscUrl.getUrlStr());
		assertEquals("existing_stream", requBasics.rscUrl.idInputSource.getIdStr());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_pause_xxx() {
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_play_xxx() {
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_redirect_xxx() {
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_setParam_xxx() {
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_setup_subStreamNotFound() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/" + RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX + "notexists";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = 707L;
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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

		assertTrue(requBasics.rscUrl.isEmpty());
		assertTrue(requBasics.rscUrl.idInputSource.isEmpty());
		assertTrue(requBasics.rscUrl.idStreamSource.isEmpty());
		assertTrue(requBasics.rscUrl.idSubStream.isEmpty());
	}

	@Test
	void structuredRequest_setup_invalidSubStreamPrefix() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/" + RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX;
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = 707L;
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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

		assertTrue(requBasics.rscUrl.isEmpty());
		assertTrue(requBasics.rscUrl.idInputSource.isEmpty());
		assertTrue(requBasics.rscUrl.idStreamSource.isEmpty());
		assertTrue(requBasics.rscUrl.idSubStream.isEmpty());
	}

	@Test
	void structuredRequest_setup_ok_queryParamSrtp() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.SETUP;
		final String expRequUrl = "rtsp://some.com/existing_stream/" +
				RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX + generatedSubStreamId.getIdStr() +
				"?" + RtspProtoHighConstants.URL_QUERY_PARAM_SRTP + "=1";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

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
				msgStructured,
				RtspProtoIdSession.of(expSessionId),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.rrIdSession.getIdStr());

		assertEquals(expRequUrl, requBasics.rscUrl.getUrlStr());
		assertEquals("existing_stream", requBasics.rscUrl.idInputSource.getIdStr());
		assertEquals("exists_12345_streamsource", requBasics.rscUrl.idStreamSource.getIdStr());
		assertEquals(generatedSubStreamId.getIdStr(), requBasics.rscUrl.idSubStream.getIdStr());
		assertTrue(outputDataRequ.rrStreamTpMain.getForceRtpRtcpEncryption());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_teardown_xxx() {
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
				msgStructured,
				RtspProtoIdSession.of("asasd"),
				new RtspProtoDataCntCseqRequInp(),
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(expStatCode, requBasics.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics wrapperProcessRequest(
				@NonNull RtspProtoHighMsgStructuredRequest inputMsgStructured,
				@NonNull RtspProtoIdSession currentSessionId,
				@NonNull RtspProtoDataCntCseqRequInp ioCseqRequ,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoSessionInfoException {
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo();
		RtspProtoDataCntStreamTpMain inpStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		return proc.processRequest(
				currentSessionId,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				sessionInfo.getClientIpAddr(),
				ioCseqRequ,
				ioSetupInfosStream,
				inpStreamTpMain,
				inputMsgStructured,
				outputDataRequ
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull LogMsgInterface buildLogMsgIf() {
		return new TestLogs();
	}

	private static @NonNull RtspProtoSessionInfo buildRtspSessionInfo() throws RtspProtoSessionInfoException {
		RtspProtoSessionInfo resObj = new RtspProtoSessionInfo();

		RtspProtoIpAddr tmpIp = RtspProtoIpAddr.ofLoopback();
		resObj.setClientIpAddr(tmpIp);
		//
		resObj.setIsRtspsConnection(false);
		//
		//resObj.descrSetupInfosStream.createAndAddSetupSubStream();
		//
		//resObj.sessionState = RtspSessionState.PLAYING;
		return resObj;
	}

	private static @NonNull RtspProtoSdpConsumerInterface buildSdpConsumer() {
		return new RtspProtoSdpConsumer();
	}

	private @NonNull RtspProtoGlobalSessionInfoSvc buildGlobalSessionInfoSvc() throws RtspProtoSessionInfoException {
		RtspProtoGlobalSessionInfoSvc resObj = new RtspProtoGlobalSessionInfoSvc();
		generatedSubStreamId = resObj.createSubStreamId(
				RtspProtoIdInputSource.of("existing_stream"),
				RtspProtoIdStreamSource.of("exists_12345_streamsource"),
				buildRtspSessionInfo().getClientIpAddr()
			);
		return resObj;
	}

	private @NonNull RtspProtoHighRequestConsumer buildRtspProtoHighRequestConsumer() {
		RtspProtoDataCntMessageTypes cfgServerSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cfgServerSupportedMessageTypes.putAllMts(RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES_INCOMING);
		cfgServerSupportedMessageTypes.writeProtect();

		final boolean cfgIsDebugDisableTransportUdp = false;

		return new RtspProtoHighRequestConsumer(
				buildLogMsgIf(),
				cfgServerSupportedMessageTypes,
				cfgIsDebugDisableTransportUdp,
				buildSdpConsumer(),
				new AvailableStreams(),
				globalSessionInfoSvc,
				null
			);
	}

}
