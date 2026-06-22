package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdStreamSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoStreamSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class S2cRrSvcTest {

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
			return idInputSource.getIdStr().orElse("-unset-").equals("existing_stream_no_auth");
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
			resObj.setNeedsAuthentication(false);
			resObj.setNeedsEncryption(false);
			return resObj;
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstVideoStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			if (! idInputSource.getIdStr().orElse("-unset-").equals("existing_stream_no_auth")) {
				return Optional.empty();
			}
			RtspProtoStreamSource resObj = new RtspProtoStreamSource();
			resObj.setIdStreamSource(RtspProtoIdStreamSource.of("dummy-stream-source"));
			return Optional.of(resObj);
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstAudioStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			return Optional.empty();
		}

		@Override
		public @NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource)
				throws RtspProtoIdStreamSourceNotFoundException {
			if (! idStreamSource.getIdStr().orElse("-unset-").equals("dummy-stream-source")) {
				throw new RtspProtoIdStreamSourceNotFoundException("ss='" + idStreamSource.getIdStr().orElse("-unset-") + "'");
			}
			return new StreamSourceInfo(
					RtpPacketType.V_H264,
					true,
					false,
					URI.create("file:///dummy-file"),
					(byte)-1,
					-1,
					false,
					-1,
					"",
					15.0
				);
		}

		@Override
		public int getStreamSourceRtpAudioSamplesPerFrame(@NonNull RtspProtoIdStreamSource idStreamSource, double videoFps)
				throws RtspProtoIdStreamSourceNotFoundException {
			throw new RtspProtoIdStreamSourceNotFoundException("getStreamSourceRtpAudioSamplesPerFrame not implemented");
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
			return true;
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
	private @Nullable AvailableStreamsServerSide srvAvailableStreams;
	private @Nullable RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc;
	private @Nullable RtspProtoSessionInfo cliSessionInfo = null;
	private @Nullable RtspProtoRequestOutputSvc srvRequOutputSvc = null;
	private @Nullable RtspProtoResponseInputSvc srvRespInputSvc = null;
	private @Nullable RtspProtoRequestInputSvc srvRequInputSvc = null;
	private @Nullable RtspProtoResponseOutputSvc srvRespOutputSvc = null;
	private @Nullable RtspProtoRequestInputSvc cliRequInputSvc = null;
	private @Nullable RtspProtoResponseOutputSvc cliRespOutputSvc = null;
	private @Nullable RtspProtoRequestOutputSvc cliRequOutputSvc = null;
	private @Nullable RtspProtoResponseInputSvc cliRespInputSvc = null;

	S2cRrSvcTest() { }

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
	void test_announce_ok() throws Exception {
		// client sends DESCRIBE request to server
		doDescribe();
		// server sends DESCRIBE request to client
		doAnnounce();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void doDescribe() throws Exception {
		Objects.requireNonNull(cliRespInputSvc);
		Objects.requireNonNull(cliRequOutputSvc);
		Objects.requireNonNull(srvRequInputSvc);
		Objects.requireNonNull(srvRespOutputSvc);
		Objects.requireNonNull(srvSessionInfo);
		Objects.requireNonNull(cliSessionInfo);

		RtspProtoMessageType mt = cliRequOutputSvc.sendRequest_describe("rtsp://localhost/existing_stream_no_auth");
		assertEquals(RtspProtoMessageType.DESCRIBE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());
	}

	private void doAnnounce() throws Exception {
		Objects.requireNonNull(cliRequInputSvc);
		Objects.requireNonNull(cliRespOutputSvc);
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(srvRespInputSvc);
		Objects.requireNonNull(srvRequOutputSvc);
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_announce(
				"rtsp://localhost/existing_stream_no_auth",
				RtspProtoIdInputSource.of("existing_stream_no_auth")
			);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = cliRequInputSvc.receiveRequestFromServer(outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		cliRespOutputSvc.sendResponse(resRequBas, outputRequ);
		assertEquals("server name and version", cliSessionInfo.getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse(mt);
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("client name and version", srvSessionInfo.getClientUserAgent().orElseThrow());
	}

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
		Objects.requireNonNull(socketPeer);

		srvSessionInfo = new RtspProtoSessionInfo();
		srvSessionInfo.setClientIpAddr(RtspProtoIpAddr.of(socketPeer.getInetAddress()));

		srvAvailableStreams = new AvailableStreamsServerSide();
		srvGlobalSessionInfoSvc = new RtspProtoGlobalSessionInfoSvc();

		initObjsServer_fromClient();
		initObjsServer_toClient();
	}

	private void initObjsServer_fromClient() {
		Objects.requireNonNull(srvSessionInfo);
		Objects.requireNonNull(srvRtxpTcpReadWrite);

		UserAuthServerSide srvUserAuthSvc = new UserAuthServerSide();

		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.DESCRIBE);

		Set<String> cfgSupportedFeatures = Set.of();
		Set<String> cfgProxySupportedFeatures = Set.of();

		srvRequInputSvc = new RtspProtoRequestInputSvc(
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
				null,
				srvRtxpTcpReadWrite
			);

		srvRespOutputSvc = new RtspProtoResponseOutputSvc(
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
				null,
				srvRtxpTcpReadWrite
			);
	}

	private void initObjsServer_toClient() {
		Objects.requireNonNull(srvRtxpTcpReadWrite);
		Objects.requireNonNull(srvSessionInfo);

		srvRequOutputSvc = new RtspProtoRequestOutputSvc(
				logger,
				false,
				"server name and version",
				"en",
				false,
				true,
				srvSessionInfo,
				srvRtxpTcpReadWrite,
				srvAvailableStreams,
				srvGlobalSessionInfoSvc
			);

		srvRespInputSvc = new RtspProtoResponseInputSvc(
				logger,
				true,
				true,
				srvSessionInfo,
				srvRtxpTcpReadWrite
			);
	}

	private void initObjsClient() {
		cliSessionInfo = new RtspProtoSessionInfo();

		initObjsClient_toServer();
		initObjsClient_fromServer();
	}

	private void initObjsClient_toServer() {
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(cliRtxpTcpReadWrite);

		cliRequOutputSvc = new RtspProtoRequestOutputSvc(
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

		cliRespInputSvc = new RtspProtoResponseInputSvc(
				logger,
				false,
				true,
				cliSessionInfo,
				cliRtxpTcpReadWrite
			);
	}

	private void initObjsClient_fromServer() {
		Objects.requireNonNull(cliSessionInfo);
		Objects.requireNonNull(cliRtxpTcpReadWrite);

		RtspProtoDataCntMessageTypes cliCfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cliCfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);

		cliRequInputSvc = new RtspProtoRequestInputSvc(
				logger,
				false,
				RtxpLogLevel.DEBUG,
				cliCfgSupportedMessageTypes,
				Set.of(),
				Set.of(),
				true,
				false,
				cliSessionInfo,
				null,
				null,
				null,
				null,
				cliRtxpTcpReadWrite
			);

		cliRespOutputSvc = new RtspProtoResponseOutputSvc(
				logger,
				true,
				"client name and version",
				"en",
				new RtspProtoDataCntMessageTypes(),
				false,
				true,
				false,
				cliSessionInfo,
				null,
				null,
				null,
				cliRtxpTcpReadWrite
			);
	}

}
