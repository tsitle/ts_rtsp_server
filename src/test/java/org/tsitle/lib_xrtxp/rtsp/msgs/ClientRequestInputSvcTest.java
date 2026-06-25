package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoRequestInputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataRequest;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ClientRequestInputSvcTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class ParameterSetter implements RtspProtoParameterSetterInterface {
		static double jitterValue = 0.0;

		@Override
		public void setRtspParameter(
					boolean dryRunOnly,
					@NonNull RtspProtoIdSession idSession,
					@NonNull String contentLanguage,
					@NonNull String key,
					@NonNull String value
				) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException {
			if (key.equals("jitter")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					jitterValue = tmpDbl;
				}
				return;
			}
			throw new RtspProtoRtspParamUnknownException("xxx");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @Nullable Socket socketPeer = null;
	private final @NonNull RtspProtoSessionInfo cliSessionInfo = new RtspProtoSessionInfo();
	private @Nullable RtspProtoRequestInputSvc inputSvc;
	private long cseqCorrect = 1L;

	ClientRequestInputSvcTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_series_of_requests() throws Exception {
		initObjs();

		recvOptions_unsuppFeature1();
		recvOptions_unsuppFeature2();
		recvOptions_wrongCseq();
		recvOptions_ok();

		recvSetParam_ok_unexpectedSessionId();
		recvSetParam_invalidParamKey();
		recvSetParam_invalidParamVal();
		recvSetParam_ok();

		recvGetParam_unexpectedSessionId();
		recvGetParam_ok();

		recvAnnounce_ok();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtxpTcpReadWrite buildRtxpTcpReadWrite() throws IOException {
		@SuppressWarnings("resource")
		ServerSocket socketServer = new ServerSocket(0);
		Socket socketClient = new Socket("127.0.0.1", socketServer.getLocalPort());
		socketPeer = socketServer.accept();

		socketClient.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite

		return new RtxpTcpReadWrite(socketClient);
	}

	private void initObjs() throws IOException {
		RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);

		Set<String> cfgSupportedFeatures = Set.of("a-useful-feature");

		inputSvc = new RtspProtoRequestInputSvc(
				new TestLogs(),
				false,
				RtxpLogLevel.DEBUG,
				cfgSupportedMessageTypes,
				cfgSupportedFeatures,
				Set.of(),
				false,
				false,
				cliSessionInfo,
				null,
				null,
				null,
				new ParameterSetter(),
				buildRtxpTcpReadWrite()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics recvRequest(
				@NonNull List<@NonNull String> msgLines,
				@NonNull RtspProtoDataRequest outputRequ
			) throws Exception {
		Objects.requireNonNull(inputSvc);

		// emulate the server sending a request
		assertNotNull(socketPeer);
		String msgForSocketStr = String.join("\r\n", msgLines);
		socketPeer.getOutputStream().write(msgForSocketStr.getBytes(StandardCharsets.UTF_8));
		socketPeer.getOutputStream().flush();

		return inputSvc.receiveRequestFromServer(outputRequ);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void recvOptions_unsuppFeature1() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Require: a-neat-feature"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);
		assertEquals("a-neat-feature", outputRequ.getUnsupportedFeatureName());

		++cseqCorrect;
	}

	private void recvOptions_unsuppFeature2() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Proxy-Require: first_feature, sec-ond-feature"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);
		if (! Set.of("first_feature", "sec-ond-feature").contains(outputRequ.getUnsupportedFeatureName())) {
			fail("Unexpected unsupported feature name: " + outputRequ.getUnsupportedFeatureName());
		}

		++cseqCorrect;
	}

	private void recvOptions_wrongCseq() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect - 1L)
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.BAD_REQUEST, resRequBas.statusCode);
	}

	private void recvOptions_ok() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Server: wonderful_piece_of_software/98.76",
				"Require: a-useful-feature"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.OPTIONS, resRequBas.messageType);

		assertTrue(cliSessionInfo.getServerSoftware().isPresent());
		assertEquals("wonderful_piece_of_software/98.76", cliSessionInfo.getServerSoftware().orElseThrow());

		++cseqCorrect;
	}

	private void recvSetParam_ok_unexpectedSessionId() throws Exception {
		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 39",
				"Session: bogus",
				"",
				"jitter: 13.8"
			);

		// since there was no Session ID until now, the Session ID will be accepted

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		++cseqCorrect;
	}

	private void recvSetParam_invalidParamKey() throws Exception {
		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 39",
				"",
				"packets_received: 100.0",
				"jitter: 13.8"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		assertEquals(Set.of("packets_received"), outputRequ.rrInvalidParamNames.getParamNames());

		++cseqCorrect;
	}

	private void recvSetParam_invalidParamVal() throws Exception {
		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 15",
				"",
				"jitter: -13.8"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		assertEquals(Set.of("jitter"), outputRequ.rrInvalidParamNames.getParamNames());

		++cseqCorrect;
	}

	private void recvSetParam_ok() throws Exception {
		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 14",
				"",
				"jitter: 13.8"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, resRequBas.messageType);

		assertEquals(Set.of("jitter"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("13.8", outputRequ.requSetParamValues.getParamKvsValue("jitter").orElseThrow());

		++cseqCorrect;
	}

	private void recvGetParam_unexpectedSessionId() throws Exception {
		final List<String> msgLines = List.of(
				"GET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 26",
				"Session: bogus_but_different",
				"",
				"packets_received",
				"jitter"
			);

		// since the Session ID had already been stored as "bogus", the modified Session ID won't be accepted

		assertEquals("bogus", cliSessionInfo.getIdSession().getIdStr().orElse("-unset-"));

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.SESSION_NOT_FOUND, resRequBas.statusCode);
		assertEquals("bogus", cliSessionInfo.getIdSession().getIdStr().orElse("-unset-"));

		++cseqCorrect;
	}

	private void recvGetParam_ok() throws Exception {
		final List<String> msgLines = List.of(
				"GET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 8",
				"",
				"jitter"
			);

		/*
		 * The request service doesn't check the parameter keys.
		 * That is done by response service.
		 */

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, resRequBas.messageType);

		assertEquals(Set.of("jitter"), outputRequ.rrGetParamNames.getParamNames());

		++cseqCorrect;
	}

	private void recvAnnounce_ok() throws Exception {
		final List<String> msgLines = List.of(
				"ANNOUNCE rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Base: rtsps://USER:PASS@localhost:12345/existing_stream?param=value",
				"Content-Type: " + RtspMimeType.SDP.getStrValue(),
				"Content-Length: 144",
				"",
				"v=0",
				"o=- 1781786500001 1781786500002 IN IP4 127.0.0.1",
				//
				"m=audio 0 RTP/SAVP 101",
				"a=rtpmap:101 L16/8000/1",
				"a=control:substreamidf528764d_081eb523"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.ANNOUNCE, resRequBas.messageType);

		assertEquals("rtsps://localhost:12345/existing_stream/?param=value", outputRequ.requAnnouncedSdpStc.getContentBase().orElseThrow());
		assertTrue(outputRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(RtspProtoSdpMediaType.AUDIO).isPresent());

		++cseqCorrect;
	}

}
