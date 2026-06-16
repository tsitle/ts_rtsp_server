package org.tsitle.rtsp.proto.rtsp_msgs;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.*;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.*;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoSdpConsumerInterface;

import java.net.InetAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
			return false;
		}

		@Override
		public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource) throws RtspProtoIdInputSourceNotFoundException {
			return null;
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
		public @NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource) throws RtspProtoIdStreamSourceNotFoundException {
			return null;
		}

		@Override
		public int getStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdStreamSource idStreamSource, double videoFps) throws RtspProtoIdStreamSourceNotFoundException {
			return 0;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class StaticData implements RtspProtoGlobalSessionInfoInterface {

		@Override
		public @NonNull RtspProtoIdSubStream createSubStreamId(@NonNull RtspProtoIdInputSource idInputSource, @NonNull RtspProtoIdStreamSource idStreamSource, @NonNull RtspProtoIpAddr clientIpAddr) {
			return null;
		}

		@Override
		public @NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream, @NonNull RtspProtoIpAddr clientIpAddr) throws RtspProtoIdSubStreamNotFoundException {
			return null;
		}

		@Override
		public @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream, @NonNull RtspProtoIpAddr clientIpAddr) throws RtspProtoIdSubStreamNotFoundException {
			return null;
		}

		@Override
		public @NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr) {
			return "";
		}

		@Override
		public boolean existsAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull String nonce) {
			return false;
		}

		@Override
		public int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
			return 0;
		}

		@Override
		public void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {

		}

		@Override
		public int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
			return 0;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void structuredRequest_options_missingCseq() throws RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		// --------------------------------

		//
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo();
		RtspProtoDataCntStreamTpMain ioStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = proc.processRequest(
				buildRtspProtoIdSession(expSessionId),
				sessionInfo.getClientIpAddr(),
				buildRtspProtoDataCntCseqRequInp(sessionInfo),
				ioStreamTpMain,
				ioSetupInfosStream,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				msgStructured,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.BAD_REQUEST, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.requIdSession.getIdStr());
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

		RtspProtoHeaderEntryRequest entryCseq = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
		entryCseq.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
		msgStructured.headers.put(RtspHeaderKey.CSEQ, entryCseq);

		// --------------------------------

		//
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo();
		RtspProtoDataCntStreamTpMain ioStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = proc.processRequest(
				buildRtspProtoIdSession(expSessionId),
				sessionInfo.getClientIpAddr(),
				buildRtspProtoDataCntCseqRequInp(sessionInfo),
				ioStreamTpMain,
				ioSetupInfosStream,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				msgStructured,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.NOT_FOUND, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.requIdSession.getIdStr());
	}

	@Test
	void structuredRequest_options_featureNotSupp() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://existing.mil/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

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

		//
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo();
		RtspProtoDataCntStreamTpMain ioStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = proc.processRequest(
				buildRtspProtoIdSession(expSessionId),
				sessionInfo.getClientIpAddr(),
				buildRtspProtoDataCntCseqRequInp(sessionInfo),
				ioStreamTpMain,
				ioSetupInfosStream,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				msgStructured,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.requIdSession.getIdStr());
	}

	@Test
	void structuredRequest_options_ok() throws RtspProtoNumberRangeException, RtspProtoSessionInfoException {
		final RtspProtoMessageType expMsgType = RtspProtoMessageType.OPTIONS;
		final String expRequUrl = "rtsp://some.com/stream";
		final RtspProtocolVersion expProtoVer = RtspProtocolVersion.RTSP_V1;
		final long expCseqLong = (long)Integer.MAX_VALUE + 1L;
		final String expSessionId = "some-session-id";

		RtspProtoHighMsgStructuredRequest msgStructured = new RtspProtoHighMsgStructuredRequest();

		msgStructured.messageType = expMsgType;
		msgStructured.statusCode = RtspProtoStatusCode.OK;
		msgStructured.resourceUrl = expRequUrl;
		msgStructured.rtspProtoVersion = expProtoVer;

		RtspProtoHeaderEntryRequest entryCseq = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
		entryCseq.hdValCseq.cseqNr.setCseq32bit(expCseqLong);
		msgStructured.headers.put(RtspHeaderKey.CSEQ, entryCseq);

		// --------------------------------

		//
		RtspProtoSessionInfo sessionInfo = buildRtspSessionInfo();
		RtspProtoDataCntStreamTpMain ioStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoHighRequestConsumer proc = buildRtspProtoHighRequestConsumer();
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics requBasics = proc.processRequest(
				buildRtspProtoIdSession(expSessionId),
				sessionInfo.getClientIpAddr(),
				buildRtspProtoDataCntCseqRequInp(sessionInfo),
				ioStreamTpMain,
				ioSetupInfosStream,
				new RtspProtoDataCntSessionState(sessionInfo.getSessionState()),
				msgStructured,
				outputDataRequ
			);

		assertEquals(expMsgType, requBasics.messageType);
		assertEquals(RtspProtoStatusCode.OK, requBasics.statusCode);

		assertEquals(expSessionId, outputDataRequ.requIdSession.getIdStr());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull LogMsgInterface buildLogMsgIf() {
		return new TestLogs();
	}

	private static @NonNull RtspProtoSessionInfo buildRtspSessionInfo() throws RtspProtoSessionInfoException {
		RtspProtoSessionInfo resObj = new RtspProtoSessionInfo();

		RtspProtoIpAddr tmpIp = new RtspProtoIpAddr();
		tmpIp.setIpAddr(InetAddress.getLoopbackAddress());
		resObj.setClientIpAddr(tmpIp);
		//
		resObj.setIsRtspsConnection(false);
		//
		//resObj.descrSetupInfosStream.createAndAddSetupSubStream();
		//
		//resObj.sessionState = RtspSessionState.PLAYING;
		return resObj;
	}

	private static @NonNull RtspProtoIdSession buildRtspProtoIdSession(@NonNull String sessionId) {
		RtspProtoIdSession resObj = new RtspProtoIdSession(sessionId);
		resObj.writeProtect();
		return resObj;
	}

	private static @NonNull RtspProtoDataCntCseqRequInp buildRtspProtoDataCntCseqRequInp(@NonNull RtspProtoSessionInfo sessionInfo) {
		RtspProtoDataCntCseqRequInp resObj = new RtspProtoDataCntCseqRequInp();
		resObj.writeProtect();
		return resObj;
	}

	private static @NonNull RtspProtoSdpConsumerInterface buildSdpConsumer() {
		return new RtspProtoSdpConsumer();
	}

	private static @NonNull RtspProtoHighRequestConsumer buildRtspProtoHighRequestConsumer() {
		RtspProtoDataCntMessageTypes cfgServerSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cfgServerSupportedMessageTypes.putAllMts(RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES_INCOMING);
		cfgServerSupportedMessageTypes.writeProtect();

		final boolean cfgIsDebugDisableTransportUdp = false;

		//
		return new RtspProtoHighRequestConsumer(
				buildLogMsgIf(),
				cfgServerSupportedMessageTypes,
				cfgIsDebugDisableTransportUdp,
				buildSdpConsumer(),
				new AvailableStreams(),
				new StaticData(),
				null
			);
	}

}
