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
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoStreamSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CsRrSvcTest {

	static class TestLogs implements LogMsgInterface {
		@Override
		public void addMsgForLogThread(@NonNull RtxpLogLevel logLevel, @NonNull String threadId, @NonNull String msg) {
			System.out.println(logLevel + " - " + threadId + ": " + msg);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class ParameterNotify implements RtspProtoParameterNotifyInvalidInterface, RtspProtoParameterNotifyRcvdInterface {
		@Override
		public void notifyInvalidRtspParameters(@NonNull RtspProtoIdSession idSession, @NonNull RtspProtoDataCntGetSetParamNames invalidParams) {
			System.out.println("Notify Invalid parameters");
			for (String paramName : invalidParams.getParamNames()) {
				System.out.println("- '" + paramName + "'");
			}
		}

		@Override
		public void notifyReceivedRtspParameters(@NonNull RtspProtoIdSession idSession, @NonNull RtspProtoDataCntGetSetParamKvs paramKvs) {
			System.out.println("Notify Received parameters");
			for (Map.Entry<@NonNull String, @NonNull String> paramKv : paramKvs.getParamKvsEntrySet()) {
				System.out.println("- '" + paramKv.getKey() + "' = '" + paramKv.getValue() + "'");
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static class AvailableStreamsServerSide implements RtspProtoAvailableStreamsInterface {
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

	static class UserAuthServerSide implements RtspProtoUserAuthInterface {
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

	static class ParameterGetterSetterServerSide implements RtspProtoParameterGetterInterface, RtspProtoParameterSetterInterface {
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
			resObj.putParamKvsEntry("jitter", Double.toString(jitterValue));
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
	private @Nullable RtspProtoSessionInfo srvSessionInfo = null;
	private @Nullable RtspProtoSessionInfo cliSessionInfo = null;
	private @Nullable UserAuthServerSide srvUserAuthSvc = null;
	private @Nullable ParameterGetterSetterServerSide srvParameterGetterSetter = null;
	private @Nullable AvailableStreamsServerSide srvAvailableStreams = null;
	private @Nullable RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc = null;
	private @Nullable RtspProtoRequestInputSvc srvInputSvc = null;
	private @Nullable RtspProtoResponseOutputSvc srvOutputSvc = null;
	private @Nullable RtspProtoRequestOutputSvc cliOutputSvc = null;
	private @Nullable RtspProtoResponseInputSvc cliInputSvc = null;

	CsRrSvcTest() { }

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
		srvAvailableStreams = new AvailableStreamsServerSide();
		srvGlobalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();
		srvUserAuthSvc = new UserAuthServerSide();

		Objects.requireNonNull(srvRtxpTcpReadWrite);
		Objects.requireNonNull(socketPeer);

		srvSessionInfo.setClientIpAddr(RtspProtoIpAddr.of(socketPeer.getInetAddress()));

		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);

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

		ParameterNotify parameterNotify = new ParameterNotify();

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
				cliRtxpTcpReadWrite,
				parameterNotify,
				parameterNotify
			);
	}

}
