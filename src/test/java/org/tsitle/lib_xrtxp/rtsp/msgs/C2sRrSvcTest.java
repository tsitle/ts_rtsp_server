package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.*;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoClientCredentials;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoStreamSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class C2sRrSvcTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class AvailableStreamsServerSide implements RtspProtoAvailableStreamsInterface {
		@Override
		public boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource) {
			return (idInputSource.getIdStr().orElse("-unset-").equals("existing_stream") ||
					idInputSource.getIdStr().orElse("-unset-").equals("existing_stream_no_auth"));
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
			resObj.setNeedsAuthentication(idInputSource.getIdStr().orElse("-unset-").equals("existing_stream"));
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

	static class UserAuthServerSide implements RtspProtoUserAuthInterface {
		final static String USER = "USERabcd";
		final static String PW = "PWmnoq";

		private final @NonNull LogMsgInterface logger;

		UserAuthServerSide(@NonNull LogMsgInterface logger) {
			this.logger = logger;
		}

		@Override
		public boolean authenticate(@NonNull RtspProtoDataCntAuthClient requAuthClient, @NonNull RtspProtoMessageType messageType) {
			if (! requAuthClient.getAuthUser().equals(USER)) {
				return false;
			}
			try {
				String expectedResponse = RtspProtoAuthDigest.computeAuthResponse(
						requAuthClient.getAuthUser(),
						PW,
						requAuthClient.getAuthUri(),
						messageType,
						requAuthClient.getAuthRealm(),
						requAuthClient.getAuthNonce()
					);
				return expectedResponse.equals(requAuthClient.getAuthResp());
			} catch (IllegalArgumentException e) {
				logger.addMsgForLogThread(RtxpLogLevel.ERROR, "xxx",
						"Invalid authentication parameters: " + e.getMessage());
				return false;
			}
		}

		@Override
		public boolean checkAccessToInputSource(@NonNull RtspProtoDataCntAuthClient requAuthClient, @NonNull RtspProtoIdInputSource idInputSource) {
			return true;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class ParameterGetterSetterServerSide implements RtspProtoParameterGetterInterface, RtspProtoParameterSetterInterface {
		static double jitterValue = 0.0;
		static double latencyValue = 0.0;

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
			if (key.equals("latency")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					latencyValue = tmpDbl;
				}
				return;
			}
			throw new RtspProtoRtspParamUnknownException("xxx");
		}

		@Override
		public @NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(@NonNull RtspProtoIdSession idSession) {
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.putParamKvsEntry("jitter", Double.toString(jitterValue).replace(",", "."));
			resObj.putParamKvsEntry("latency", Double.toString(latencyValue).replace(",", "."));
			return resObj;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final @NonNull RtspProtoDataCntMessageTypes srvCfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	private final @NonNull TestLogs logger = new TestLogs();
	private @Nullable RtxpTcpReadWrite srvRtxpTcpReadWrite = null;
	private @Nullable RtxpTcpReadWrite cliRtxpTcpReadWrite = null;
	private @Nullable ServerSocket socketServer = null;
	private @Nullable Socket socketClient = null;
	private @Nullable Socket socketPeer = null;
	private @Nullable ParameterGetterSetterServerSide srvParameterGetterSetter = null;
	private @Nullable RtspProtoSessionInfo srvSessionInfo = null;
	private @Nullable RtspProtoSessionInfo cliSessionInfo = null;
	private @Nullable RtspProtoRequestInputSvc srvInputSvc = null;
	private @Nullable RtspProtoResponseOutputSvc srvOutputSvc = null;
	private @Nullable RtspProtoRequestOutputSvc cliOutputSvc = null;
	private @Nullable RtspProtoResponseInputSvc cliInputSvc = null;

	C2sRrSvcTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@BeforeEach
	void setUp() throws Exception {
		initRtxpTcpReadWrite();
		initObjsServer();
		initObjsClient();
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
	void test_getParam_invalidKeys() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoDataCntGetSetParamNames getParamNames = new RtspProtoDataCntGetSetParamNames();
		getParamNames.putParamName("xano");
		getParamNames.putParamName("rucola");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_getParameter(
				"rtsp://localhost/existing_stream_no_auth",
				getParamNames
			);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertEquals(Set.of("xano", "rucola"), outputRequ.rrGetParamNames.getParamNames());
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertEquals(Set.of("xano", "rucola"), cliSessionInfo.getRhInvalidParamNames().getParamNames());
		assertTrue(cliSessionInfo.getRhGetParamValues().getParamKvsKeySet().isEmpty());
	}

	@Test
	void test_getParam_ok() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);
		Objects.requireNonNull(srvParameterGetterSetter);

		RtspProtoDataCntGetSetParamNames getParamNames = new RtspProtoDataCntGetSetParamNames();
		getParamNames.putParamName("jitter");
		getParamNames.putParamName("latency");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_getParameter(
				"rtsp://localhost/existing_stream",
				getParamNames
			);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRequBas.statusCode);

		assertEquals(Set.of("jitter", "latency"), outputRequ.rrGetParamNames.getParamNames());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertTrue(cliSessionInfo.getPermAuthServerRealm().isPresent());
		assertTrue(cliSessionInfo.getPermAuthServerNonce().isPresent());

		// ----------------------------------------------------
		// -- The server has sent the Auth Realm and Nonce.
		// -- We can now send an authorized request.
		// ----------------------------------------------------

		RtspProtoClientCredentials clientCredentials = RtspProtoClientCredentials.of(
				UserAuthServerSide.USER,
				UserAuthServerSide.PW
			);

		// ----------------------------------------------------

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("jitter", "9.7");
		setParamKvs.putParamKvsEntry("latency", "18");
		cliOutputSvc.sendRequest_setParameter(
				"rtsp://localhost/existing_stream",
				clientCredentials,
				setParamKvs
			);

		outputRequ = new RtspProtoDataRequest();
		resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// emulate the server setting its internal parameters
		for (Map.Entry<@NonNull String, @NonNull String> entry : outputRequ.requSetParamValues.getParamKvsEntrySet()) {
			try {
				srvParameterGetterSetter.setRtspParameter(
						false,
						srvSessionInfo.getIdSession(),
						outputRequ.requSetParamValues.getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspProtoRtspParamUnknownException | RtspProtoRtspParamInvalidValueException e) {
				// this cannot happen because the parameters have already been validated
			}
		}

		resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);

		// ----------------------------------------------------

		cliOutputSvc.sendRequest_getParameter(
				"rtsp://localhost/existing_stream",
				clientCredentials,
				getParamNames
			);

		// ----------------------------------------------------

		outputRequ = new RtspProtoDataRequest();
		resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertEquals(Set.of("jitter", "latency"), outputRequ.rrGetParamNames.getParamNames());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertTrue(cliSessionInfo.getRhInvalidParamNames().getParamNames().isEmpty());
		assertEquals("9.7", cliSessionInfo.getRhGetParamValues().getParamKvsValue("jitter").orElseThrow());
		assertEquals("18.0", cliSessionInfo.getRhGetParamValues().getParamKvsValue("latency").orElseThrow());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_options_unsuppFeature1() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of("hulahup"),
				Set.of()
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);
		assertEquals("hulahup", outputRequ.getUnsupportedFeatureName());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRespBas.statusCode);
		assertEquals("hulahup", cliSessionInfo.getUnsupportedFeatureName().orElseThrow());
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
	}

	@Test
	void test_options_unsuppFeature2() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of(),
				Set.of("yolanda", "petro")
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);
		if (! Set.of("yolanda", "petro").contains(outputRequ.getUnsupportedFeatureName())) {
			fail("Unexpected unsupported feature name: " + outputRequ.getUnsupportedFeatureName());
		}

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRespBas.statusCode);
		if (! Set.of("yolanda", "petro").contains(cliSessionInfo.getUnsupportedFeatureName().orElseThrow())) {
			fail("Unexpected unsupported feature name: " + cliSessionInfo.getUnsupportedFeatureName().orElseThrow());
		}
	}

	@Test
	void test_options_ok() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of("a-useful-feature"),
				Set.of("proxy-feature")
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertEquals("client name and version", outputRequ.getClientUa());
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());
		assertEquals(Set.of("a-useful-feature"), outputRequ.requRequiredFeatures.getFeatureNames());
		assertEquals(Set.of("proxy-feature"), outputRequ.requProxyRequiredFeatures.getFeatureNames());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertTrue(cliSessionInfo.getUnsupportedFeatureName().isEmpty());
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertEquals(srvCfgSupportedMessageTypes.getMts(), cliSessionInfo.getRhSupportedMessageTypes().getMts());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_pause_wrongState() throws Exception {
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_pause("rtsp://localhost/existing_stream_no_auth");
		assertEquals(RtspProtoMessageType.PAUSE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_play_wrongState() throws Exception {
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_play("rtsp://localhost/existing_stream_no_auth");
		assertEquals(RtspProtoMessageType.PLAY, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_teardown_wrongState() throws Exception {
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_teardown("rtsp://localhost/existing_stream_no_auth");
		assertEquals(RtspProtoMessageType.TEARDOWN, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_setParam_invalidKeys() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("xano", "9.7");
		setParamKvs.putParamKvsEntry("rucola", "no");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				"rtsp://localhost/existing_stream",
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		assertEquals(Set.of("xano", "rucola"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertEquals(Set.of("xano", "rucola"), cliSessionInfo.getRhInvalidParamNames().getParamNames());
	}

	@Test
	void test_setParam_invalidVal() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("jitter", "-9.7");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				"rtsp://localhost/existing_stream",
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		assertEquals(Set.of("jitter"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertEquals(Set.of("jitter"), cliSessionInfo.getRhInvalidParamNames().getParamNames());
	}

	@Test
	void test_setParam_ok() throws Exception {
		Objects.requireNonNull(socketPeer);
		Objects.requireNonNull(cliInputSvc);
		Objects.requireNonNull(cliOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvInputSvc);
		Objects.requireNonNull(srvOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("jitter", "9.7");
		setParamKvs.putParamKvsEntry("latency", "18");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				"rtsp://localhost/existing_stream",
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRequBas.statusCode);

		assertEquals(Set.of("jitter", "latency"), outputRequ.requSetParamValues.getParamKvsKeySet());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
		assertTrue(cliSessionInfo.getPermAuthServerRealm().isPresent());
		assertTrue(cliSessionInfo.getPermAuthServerNonce().isPresent());

		// ----------------------------------------------------
		// -- The server has sent the Auth Realm and Nonce.
		// -- We can now send an authorized request.
		// ----------------------------------------------------

		RtspProtoClientCredentials clientCredentials = RtspProtoClientCredentials.of(
				UserAuthServerSide.USER,
				UserAuthServerSide.PW
			);
		cliOutputSvc.sendRequest_setParameter(
				"rtsp://localhost/existing_stream",
				clientCredentials,
				setParamKvs
			);

		// ----------------------------------------------------

		outputRequ = new RtspProtoDataRequest();
		resRequBas = srvInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertEquals(Set.of("jitter", "latency"), outputRequ.requSetParamValues.getParamKvsKeySet());
		assertEquals("9.7", outputRequ.requSetParamValues.getParamKvsValue("jitter").orElseThrow());
		assertEquals("18", outputRequ.requSetParamValues.getParamKvsValue("latency").orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		resRespBas = cliInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertTrue(cliSessionInfo.getRhInvalidParamNames().getParamNames().isEmpty());
		assertTrue(cliSessionInfo.getRhGetParamValues().getParamKvsKeySet().isEmpty());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initRtxpTcpReadWrite() throws Exception {
		socketServer = new ServerSocket(0);
		socketClient = new Socket("127.0.0.1", socketServer.getLocalPort());
		socketPeer = socketServer.accept();

		socketPeer.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite
		socketClient.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite

		cliRtxpTcpReadWrite = new RtxpTcpReadWrite(socketClient);
		srvRtxpTcpReadWrite = new RtxpTcpReadWrite(socketPeer);
	}

	private void initObjsServer() throws Exception {
		srvSessionInfo = new RtspProtoSessionInfo();
		srvParameterGetterSetter = new ParameterGetterSetterServerSide();
		AvailableStreamsServerSide srvAvailableStreams = new AvailableStreamsServerSide();
		RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();
		UserAuthServerSide srvUserAuthSvc = new UserAuthServerSide(logger);

		Objects.requireNonNull(srvRtxpTcpReadWrite);
		Objects.requireNonNull(socketPeer);

		srvSessionInfo.setClientIpAddr(RtspProtoIpAddr.of(socketPeer.getInetAddress()));

		//srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		//srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.DESCRIBE);  // @TODO
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.PAUSE);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.PLAY);
		//srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.REDIRECT);  // @TODO
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);
		//srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SETUP);  // @TODO
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.TEARDOWN);

		Set<String> cfgSupportedFeatures = Set.of("a-useful-feature");
		Set<String> cfgProxySupportedFeatures = Set.of("proxy-feature");

		srvInputSvc = new RtspProtoRequestInputSvc(
				logger,
				true,
				RtxpLogLevel.DEBUG,
				srvCfgSupportedMessageTypes,
				cfgSupportedFeatures,
				cfgProxySupportedFeatures,
				true,
				false,
				srvSessionInfo,
				srvUserAuthSvc,
				srvAvailableStreams,
				srvGlobalSessionInfoSvc,
				srvParameterGetterSetter,
				srvRtxpTcpReadWrite
			);

		srvOutputSvc = new RtspProtoResponseOutputSvc(
				logger,
				false,
				"server name and version",
				"en",
				srvCfgSupportedMessageTypes,
				false,
				true,
				false,
				srvSessionInfo,
				srvAvailableStreams,
				srvGlobalSessionInfoSvc,
				srvParameterGetterSetter,
				srvRtxpTcpReadWrite
			);
	}

	private void initObjsClient() {
		cliSessionInfo = new RtspProtoSessionInfo();

		Objects.requireNonNull(cliRtxpTcpReadWrite);

		cliOutputSvc = new RtspProtoRequestOutputSvc(
				logger,
				true,
				"client name and version",
				"en",
				false,
				true,
				cliSessionInfo,
				cliRtxpTcpReadWrite,
				null,
				null
			);

		cliInputSvc = new RtspProtoResponseInputSvc(
				logger,
				false,
				true,
				cliSessionInfo,
				cliRtxpTcpReadWrite
			);
	}

}
