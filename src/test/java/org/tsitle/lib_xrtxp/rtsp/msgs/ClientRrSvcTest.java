package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataRequest;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterGetterInterface;
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

public class ClientRrSvcTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class ParameterGetterSetter implements RtspProtoParameterGetterInterface, RtspProtoParameterSetterInterface {
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

		@Override
		public @NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull RtspProtoIdSession idSession) {
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.putParamKvsEntry("jitter", Double.toString(jitterValue).replace(",", "."));
			return resObj;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @Nullable RtxpTcpReadWrite rtxpTcpReadWrite = null;
	private @Nullable ServerSocket socketServer = null;
	private @Nullable Socket socketClient = null;
	private @Nullable Socket socketPeer = null;
	private @Nullable RtspProtoSessionInfo rtspSessionInfo = null;
	private @Nullable ParameterGetterSetter parameterGetterSetter = null;
	private @Nullable RtspProtoRequestInputSvc inputSvc = null;
	private @Nullable RtspProtoResponseOutputSvc outputSvc = null;

	ClientRrSvcTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@BeforeEach
	void setUp() throws IOException {
		initRtxpTcpReadWrite();
		rtspSessionInfo = new RtspProtoSessionInfo();
		parameterGetterSetter = new ParameterGetterSetter();
		initObjsInput();
		initObjsOutput();
	}

	@AfterEach
	void tearDown() throws IOException {
		if (socketClient != null) {
			socketClient.close();
		}
		if (socketPeer != null) {
			socketPeer.close();
		}
		if (socketServer != null) {
			socketServer.close();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void recv_announce_ok() throws Exception {
		Objects.requireNonNull(outputSvc);

		final List<String> msgLines = List.of(
				"ANNOUNCE rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: 1",
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

		// ----------------------------------------------------

		outputSvc.sendResponse(resRequBas, outputRequ);
	}

	@Test
	void recv_options_unsuppFeature1() throws Exception {
		Objects.requireNonNull(outputSvc);

		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: 9876",
				"Require: a-neat-feature"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);
		assertEquals("a-neat-feature", outputRequ.getUnsupportedFeatureName());

		// ----------------------------------------------------

		outputSvc.sendResponse(resRequBas, outputRequ);
	}

	@Test
	void recv_setParam_invalidParamKey() throws Exception {
		Objects.requireNonNull(outputSvc);

		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: 53",
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

		// ----------------------------------------------------

		outputSvc.sendResponse(resRequBas, outputRequ);
	}

	@Test
	void recv_setParam_getParam_ok() throws Exception {
		Objects.requireNonNull(outputSvc);
		Objects.requireNonNull(parameterGetterSetter);

		final List<String> msgLinesSet = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: 29",
				"Content-Type: text/parameters",
				"Content-Length: 14",
				"",
				"jitter: 13.8"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLinesSet, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, resRequBas.messageType);

		assertEquals(Set.of("jitter"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("13.8", outputRequ.requSetParamValues.getParamKvsValue("jitter").orElseThrow());

		// ----------------------------------------------------

		outputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		// emulate actually setting the parameter value
		for (String paramKey : outputRequ.requSetParamValues.getParamKvsKeySet()) {
			parameterGetterSetter.setRtspParameter(
					false,
					RtspProtoIdSession.of("11111"),
					"",
					paramKey,
					outputRequ.requSetParamValues.getParamKvsValue(paramKey).orElseThrow()
				);
		}

		// ----------------------------------------------------

		final List<String> msgLinesGet = List.of(
				"GET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: 30",
				"Content-Type: text/parameters",
				"Content-Length: 8",
				"",
				"jitter"
			);

		outputRequ = new RtspProtoDataRequest();
		resRequBas = recvRequest(msgLinesGet, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, resRequBas.messageType);

		assertEquals(Set.of("jitter"), outputRequ.rrGetParamNames.getParamNames());

		// ----------------------------------------------------

		outputSvc.sendResponse(resRequBas, outputRequ);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initRtxpTcpReadWrite() throws IOException {
		socketServer = new ServerSocket(0);
		socketClient = new Socket("127.0.0.1", socketServer.getLocalPort());
		socketPeer = socketServer.accept();

		socketPeer.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite
		socketClient.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite

		rtxpTcpReadWrite = new RtxpTcpReadWrite(socketClient);
	}

	private void initObjsInput() {
		Objects.requireNonNull(rtspSessionInfo);
		Objects.requireNonNull(rtxpTcpReadWrite);

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
				rtspSessionInfo,
				null,
				null,
				null,
				parameterGetterSetter,
				rtxpTcpReadWrite
			);
	}

	private void initObjsOutput() {
		Objects.requireNonNull(rtspSessionInfo);
		Objects.requireNonNull(rtxpTcpReadWrite);

		RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);

		outputSvc = new RtspProtoResponseOutputSvc(
				new TestLogs(),
				true,
				"client name and version",
				"en",
				cfgSupportedMessageTypes,
				"",
				false,
				true,
				false,
				rtspSessionInfo,
				null,
				null,
				parameterGetterSetter,
				rtxpTcpReadWrite
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

}
