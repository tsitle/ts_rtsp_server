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
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.*;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;

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
		public Optional<RtspProtoElementaryStreamSource> getFirstVideoEsSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			return Optional.empty();
		}

		@Override
		public Optional<RtspProtoElementaryStreamSource> getFirstAudioEsSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			return Optional.empty();
		}

		@Override
		public @NonNull ElementaryStreamSourceInfo getElementaryStreamSourceInfo(@NonNull RtspProtoIdEsSource idEsSource)
				throws RtspProtoIdEsSourceNotFoundException {
			throw new RtspProtoIdEsSourceNotFoundException("");
		}

		@Override
		public int getElementaryStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdEsSource idEsSource, double videoFps)
				throws RtspProtoIdEsSourceNotFoundException {
			throw new RtspProtoIdEsSourceNotFoundException("");
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
		public boolean authenticate(
					@NonNull RtspProtoSessionInfo sessionInfo,
					@NonNull RtspProtoDataCntAuthClient requAuthClient,
					@NonNull RtspProtoMessageType messageType
				) {
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
		private double jitterValue = 0.0;
		private double latencyValue = 0.0;
		private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;

		ParameterGetterSetterServerSide(@NonNull RtspProtoPtrSessionInfo sessionInfoPtr) {
			this.sessionInfoPtr = sessionInfoPtr;
		}

		@Override
		public void setRtspParameter(
					boolean dryRunOnly,
					@NonNull RtspProtoIdSession idSession,
					@NonNull RtspProtoIdInputSource idInputSource,
					@NonNull RtspProtoIdSubStream idSubStream,
					@NonNull String contentLanguage,
					@NonNull String key,
					@NonNull String value
				) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException {
			if (! checkSessionId(idSession)) {
				throw new RtspProtoRtspParamUnknownException("Session ID mismatch");
			}
			if (! checkInputSource(idInputSource)) {
				throw new RtspProtoRtspParamUnknownException("Non-existing Input Source");
			}

			if (idSubStream.isEmpty() && key.equals("jitter")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					jitterValue = tmpDbl;
				}
				return;
			}
			if (idSubStream.isEmpty() && key.equals("latency")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					latencyValue = tmpDbl;
				}
				return;
			}
			if (! idSubStream.isEmpty()) {
				throw new RtspProtoRtspParamUnknownException("Sub-Stream ID should have been empty");
			}
			throw new RtspProtoRtspParamUnknownException("xxx");
		}

		@Override
		public @NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(
					@NonNull RtspProtoIdSession idSession,
					@NonNull RtspProtoIdInputSource idInputSource,
					@NonNull RtspProtoIdSubStream idSubStream
				) {
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			if (checkSessionId(idSession) && checkInputSource(idInputSource)) {
				if (idSubStream.isEmpty()) {
					resObj.putParamKvsEntry("jitter", doubleToString(jitterValue));
					resObj.putParamKvsEntry("latency", doubleToString(latencyValue));
				}
			}
			return resObj;
		}

		private @NonNull String doubleToString(double value) {
			return Double.toString(value).replace(",", ".");
		}

		private boolean checkSessionId(@NonNull RtspProtoIdSession idSession) {
			return idSession.equals(sessionInfoPtr.ptr().getIdSession());
		}

		private boolean checkInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
			Set<RtspProtoIdInputSource> availIss = new HashSet<>();
			availIss.add(RtspProtoIdInputSource.of("existing_stream"));
			availIss.add(RtspProtoIdInputSource.of("existing_stream_no_auth"));
			return availIss.contains(idInputSource);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final @NonNull RtspProtoDataCntMessageTypes srvCfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	private final @NonNull TestLogs logger = new TestLogs();
	private @Nullable ServerSocket socketServer = null;
	private @Nullable Socket socketClient = null;
	private @Nullable Socket socketPeer = null;

	private ParameterGetterSetterServerSide srvParameterGetterSetter = null;
	private RtspProtoPtrSessionInfo srvPtrSessionInfo = null;
	private RtspProtoRequestInputSvc srvInputSvc = null;
	private RtspProtoResponseOutputSvc srvOutputSvc = null;

	private RtspProtoPtrSessionInfo cliPtrSessionInfo = null;
	private RtspProtoRequestOutputSvc cliOutputSvc = null;
	private RtspProtoResponseInputSvc cliInputSvc = null;

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
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoDataCntGetSetParamNames getParamNames = new RtspProtoDataCntGetSetParamNames();
		getParamNames.putParamName("xano");
		getParamNames.putParamName("rucola");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_getParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream_no_auth"),
				getParamNames
			);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertTrue(srvPtrSessionInfo.ptr().getRhGetParamNames().isPresent());
		assertEquals(Set.of("xano", "rucola"), srvPtrSessionInfo.ptr().getRhGetParamNames().orElseThrow().getParamNames());
		assertEquals("client name and version", srvPtrSessionInfo.ptr().getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertTrue(cliPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertEquals(Set.of("xano", "rucola"), cliPtrSessionInfo.ptr().getRhInvalidParamNames().orElseThrow().getParamNames());
		assertTrue(cliPtrSessionInfo.ptr().getRhGetParamValues().isEmpty());
	}

	@Test
	void test_getParam_ok_auth() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoDataCntGetSetParamNames getParamNames = new RtspProtoDataCntGetSetParamNames();
		getParamNames.putParamName("jitter");
		getParamNames.putParamName("latency");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_getParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				getParamNames
			);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRespBas.statusCode);
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertTrue(cliPtrSessionInfo.ptr().getPermAuthServerRealm().isPresent());
		assertTrue(cliPtrSessionInfo.ptr().getPermAuthServerNonce().isPresent());

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
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				clientCredentials,
				setParamKvs
			);

		resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		srvOutputSvc.sendResponse(resRequBas);

		assertTrue(srvPtrSessionInfo.ptr().getRhSetParamValues().isPresent());
		assertEquals(Set.of("jitter", "latency"), srvPtrSessionInfo.ptr().getRhSetParamValues().orElseThrow().getParamKvsKeySet());

		// emulate the server setting its internal parameters
		Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = srvPtrSessionInfo.ptr().getRhSetParamValues();
		assertTrue(tmpOptKvs.isPresent());
		for (Map.Entry<@NonNull String, @NonNull String> entry : tmpOptKvs.get().getParamKvsEntrySet()) {
			try {
				srvParameterGetterSetter.setRtspParameter(
						false,
						srvPtrSessionInfo.ptr().getIdSession(),
						tmpOptKvs.get().getIdInputSource(),
						tmpOptKvs.get().getIdSubStream(),
						tmpOptKvs.get().getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspProtoRtspParamUnknownException | RtspProtoRtspParamInvalidValueException e) {
				// this cannot happen because the parameters have already been validated
			}
		}

		resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);

		// ----------------------------------------------------

		cliOutputSvc.sendRequest_getParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				clientCredentials,
				getParamNames
			);

		// ----------------------------------------------------

		resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertTrue(srvPtrSessionInfo.ptr().getRhGetParamNames().isPresent());
		assertEquals(Set.of("jitter", "latency"), srvPtrSessionInfo.ptr().getRhGetParamNames().orElseThrow().getParamNames());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertFalse(cliPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertTrue(cliPtrSessionInfo.ptr().getRhGetParamValues().isPresent());
		assertEquals("9.7", cliPtrSessionInfo.ptr().getRhGetParamValues().orElseThrow().getParamKvsValue("jitter").orElseThrow());
		assertEquals("18.0", cliPtrSessionInfo.ptr().getRhGetParamValues().orElseThrow().getParamKvsValue("latency").orElseThrow());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_options_unsuppFeature1() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of("hulahup"),
				Set.of()
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRespBas.statusCode);
		assertEquals("hulahup", cliPtrSessionInfo.ptr().getUnsupportedFeatureName().orElseThrow());
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
	}

	@Test
	void test_options_unsuppFeature2() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of(),
				Set.of("yolanda", "petro")
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OPTION_NOT_SUPPORTED, resRespBas.statusCode);
		if (! Set.of("yolanda", "petro").contains(cliPtrSessionInfo.ptr().getUnsupportedFeatureName().orElseThrow())) {
			fail("Unexpected unsupported feature name: " + cliPtrSessionInfo.ptr().getUnsupportedFeatureName().orElseThrow());
		}
	}

	@Test
	void test_options_ok_noAuth() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				Set.of("a-useful-feature"),
				Set.of("proxy-feature")
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertEquals("client name and version", srvPtrSessionInfo.ptr().getClientUserAgent().orElseThrow());
		assertTrue(srvPtrSessionInfo.ptr().getRhRequiredFeatures().isPresent());
		assertTrue(srvPtrSessionInfo.ptr().getRhProxyRequiredFeatures().isPresent());
		assertEquals(Set.of("a-useful-feature"), srvPtrSessionInfo.ptr().getRhRequiredFeatures().orElseThrow().getFeatureNames());
		assertEquals(Set.of("proxy-feature"), srvPtrSessionInfo.ptr().getRhProxyRequiredFeatures().orElseThrow().getFeatureNames());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertTrue(cliPtrSessionInfo.ptr().getUnsupportedFeatureName().isEmpty());
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertEquals(srvCfgSupportedMessageTypes.getMts(), cliPtrSessionInfo.ptr().getRhSupportedMessageTypes().getMts());
	}

	@Test
	void test_options_ok_auth() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoClientCredentials clientCredentials = RtspProtoClientCredentials.of(
				UserAuthServerSide.USER,
				UserAuthServerSide.PW
			);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream",
				clientCredentials
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);  // <-- the server doesn't check the credentials for OPTIONS requests

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_pause_wrongState() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_pause(
				"rtsp://localhost/existing_stream_no_auth"
			);
		assertEquals(RtspProtoMessageType.PAUSE, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_play_wrongState() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_play(
				"rtsp://localhost/existing_stream_no_auth"
			);
		assertEquals(RtspProtoMessageType.PLAY, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_teardown_wrongState() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_teardown(
				"rtsp://localhost/existing_stream_no_auth"
			);
		assertEquals(RtspProtoMessageType.TEARDOWN, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE, resRespBas.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_setParam_invalidKeys() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("xano", "9.7");
		setParamKvs.putParamKvsEntry("rucola", "no");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = srvPtrSessionInfo.ptr().getRhSetParamValues();
		assertFalse(tmpOptKvs.isPresent());
		assertTrue(srvPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertEquals(Set.of("xano", "rucola"), srvPtrSessionInfo.ptr().getRhInvalidParamNames().orElseThrow().getParamNames());
		assertEquals("client name and version", srvPtrSessionInfo.ptr().getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertTrue(cliPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertEquals(Set.of("xano", "rucola"), cliPtrSessionInfo.ptr().getRhInvalidParamNames().orElseThrow().getParamNames());
	}

	@Test
	void test_setParam_invalidVal() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("jitter", "-9.7");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRequBas.statusCode);

		Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = srvPtrSessionInfo.ptr().getRhSetParamValues();
		assertFalse(tmpOptKvs.isPresent());
		assertTrue(srvPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertEquals(Set.of("jitter"), srvPtrSessionInfo.ptr().getRhInvalidParamNames().orElseThrow().getParamNames());
		assertEquals("client name and version", srvPtrSessionInfo.ptr().getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.INVALID_PARAMETER, resRespBas.statusCode);
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertTrue(cliPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertEquals(Set.of("jitter"), cliPtrSessionInfo.ptr().getRhInvalidParamNames().orElseThrow().getParamNames());
	}

	@Test
	void test_setParam_ok_auth() throws Exception {
		Objects.requireNonNull(cliPtrSessionInfo);
		Objects.requireNonNull(srvPtrSessionInfo);

		RtspProtoDataCntGetSetParamKvs setParamKvs = new RtspProtoDataCntGetSetParamKvs();
		setParamKvs.putParamKvsEntry("jitter", "9.7");
		setParamKvs.putParamKvsEntry("latency", "18");

		RtspProtoMessageType mt = cliOutputSvc.sendRequest_setParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				setParamKvs
			);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRequBas.statusCode);

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.UNAUTHORIZED, resRespBas.statusCode);
		assertEquals("server name and version", cliPtrSessionInfo.ptr().getServerSoftware().orElseThrow());
		assertTrue(cliPtrSessionInfo.ptr().getPermAuthServerRealm().isPresent());
		assertTrue(cliPtrSessionInfo.ptr().getPermAuthServerNonce().isPresent());

		// ----------------------------------------------------
		// -- The server has sent the Auth Realm and Nonce.
		// -- We can now send an authorized request.
		// ----------------------------------------------------

		RtspProtoClientCredentials clientCredentials = RtspProtoClientCredentials.of(
				UserAuthServerSide.USER,
				UserAuthServerSide.PW
			);
		cliOutputSvc.sendRequest_setParameter(
				RtspProtoRscUrl.of("rtsp://localhost/existing_stream"),
				clientCredentials,
				setParamKvs
			);

		// ----------------------------------------------------

		resRequBas = srvInputSvc.receiveRequestFromClient();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = srvPtrSessionInfo.ptr().getRhSetParamValues();
		assertTrue(tmpOptKvs.isPresent());
		assertEquals(Set.of("jitter", "latency"), tmpOptKvs.get().getParamKvsKeySet());
		assertEquals("9.7", tmpOptKvs.get().getParamKvsValue("jitter").orElseThrow());
		assertEquals("18", tmpOptKvs.get().getParamKvsValue("latency").orElseThrow());

		// ----------------------------------------------------

		srvOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		resRespBas = cliInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertFalse(cliPtrSessionInfo.ptr().getRhInvalidParamNames().isPresent());
		assertFalse(cliPtrSessionInfo.ptr().getRhGetParamValues().isPresent());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initRtxpTcpReadWrite() throws Exception {
		socketServer = new ServerSocket(0);
		socketClient = new Socket("127.0.0.1", socketServer.getLocalPort());
		socketPeer = socketServer.accept();

		socketPeer.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite
		socketClient.setSoTimeout(5); // must be > 0 for RtxpTcpReadWrite
	}

	private void initObjsServer() throws Exception {
		Objects.requireNonNull(socketPeer);

		RtxpTcpReadWrite srvRtxpTcpReadWrite = new RtxpTcpReadWrite(socketPeer);

		srvPtrSessionInfo = RtspProtoPtrSessionInfo.ofNewSi();
		srvParameterGetterSetter = new ParameterGetterSetterServerSide(srvPtrSessionInfo);
		AvailableStreamsServerSide srvAvailableStreams = new AvailableStreamsServerSide();
		RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();
		UserAuthServerSide srvUserAuthSvc = new UserAuthServerSide(logger);

		srvPtrSessionInfo.ptr().setClientIpAddr(RtspProtoIpAddr.of(socketPeer.getInetAddress()));

		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.PAUSE);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.PLAY);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);
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
				false,
				false,
				srvPtrSessionInfo,
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
				"just_a_prefix",
				false,
				true,
				false,
				srvPtrSessionInfo,
				srvAvailableStreams,
				srvGlobalSessionInfoSvc,
				srvParameterGetterSetter,
				null,
				srvRtxpTcpReadWrite
			);
	}

	private void initObjsClient() {
		Objects.requireNonNull(socketClient);

		RtxpTcpReadWrite cliRtxpTcpReadWrite = new RtxpTcpReadWrite(socketClient);

		cliPtrSessionInfo = RtspProtoPtrSessionInfo.ofNewSi();

		cliOutputSvc = new RtspProtoRequestOutputSvc(
				logger,
				true,
				"client name and version",
				null,
				"en",
				false,
				true,
				cliPtrSessionInfo,
				cliRtxpTcpReadWrite,
				null,
				null
			);

		cliInputSvc = new RtspProtoResponseInputSvc(
				logger,
				false,
				false,
				cliPtrSessionInfo,
				cliRtxpTcpReadWrite
			);
	}

}
