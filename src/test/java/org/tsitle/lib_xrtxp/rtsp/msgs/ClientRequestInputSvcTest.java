package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoRequestInputSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataRequest;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdStreamSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoStreamSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClientRequestInputSvcTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class UserAuthImpl implements RtspProtoUserAuthInterface {
		@Override
		public boolean authenticate(@NonNull RtspProtoDataCntAuthClient requAuthClient, @NonNull RtspProtoMessageType messageType) {
			return false;
		}

		@Override
		public boolean checkAccessToInputSource(@NonNull RtspProtoDataCntAuthClient requAuthClient, @NonNull RtspProtoIdInputSource idInputSource) {
			return false;
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
	private @Nullable RtspProtoRequestInputSvc inputSvc;
	private long cseqCorrect = 1L;

	ClientRequestInputSvcTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_simple() throws Exception {
		initObjs();

		recvOptions_ok();
		recvOptions_wrongCseq();
		recvOptions_notFound();

		recvSetParam_wrongSessionId();
		recvSetParam_invalidParamKey();
		recvSetParam_invalidParamVal();
		recvSetParam_ok();

		recvGetParam_wrongSessionId();
		recvGetParam_ok();
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

	private @NonNull RtspProtoGlobalSessionInfoSvc buildGlobalSessionInfoSvc() {
		return new RtspProtoGlobalSessionInfoSvc();
	}

	private void initObjs() throws IOException {
		RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		cfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);

		RtspProtoSessionInfo rtspSessionInfo = new RtspProtoSessionInfo();

		inputSvc = new RtspProtoRequestInputSvc(
				new TestLogs(),
				false,
				RtxpLogLevel.DEBUG,
				cfgSupportedMessageTypes,
				false,
				false,
				rtspSessionInfo,
				new UserAuthImpl(),
				new AvailableStreams(),
				buildGlobalSessionInfoSvc(),
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

	private void recvOptions_ok() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect)
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

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

	private void recvOptions_notFound() throws Exception {
		final List<String> msgLines = List.of(
				"OPTIONS rtsp://localhost/this_is_bogus RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect)
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.NOT_FOUND, resRequBas.statusCode);

		++cseqCorrect;
	}

	private void recvSetParam_wrongSessionId() throws Exception {
		final List<String> msgLines = List.of(
				"SET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 39",
				"Session: bogus",
				"",
				"packets_received: 100.0",
				"jitter: 13.8"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.SESSION_NOT_FOUND, resRequBas.statusCode);

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

		assertEquals(Set.of("jitter"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("13.8", outputRequ.requSetParamValues.getParamKvsValue("jitter").orElseThrow());

		++cseqCorrect;
	}

	private void recvGetParam_wrongSessionId() throws Exception {
		final List<String> msgLines = List.of(
				"GET_PARAMETER rtsp://localhost/existing_stream RTSP/1.0",
				"CSeq: " + Long.toUnsignedString(cseqCorrect),
				"Content-Type: text/parameters",
				"Content-Length: 26",
				"Session: bogus",
				"",
				"packets_received",
				"jitter"
			);

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = recvRequest(msgLines, outputRequ);

		assertEquals(RtspProtoStatusCode.SESSION_NOT_FOUND, resRequBas.statusCode);

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

		assertEquals(Set.of("jitter"), outputRequ.rrGetParamNames.getParamNames());

		++cseqCorrect;
	}

}
