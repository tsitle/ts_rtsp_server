package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
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
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.RtspProtoSdpDataMediaEntry;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.*;

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
			if (idInputSource.getIdStr().orElseThrow().contains("/")) {
				return false;
			}
			return Set.of("existing_stream_no_auth_no_encr", "existing_stream_no_auth_with_encr")
					.contains(idInputSource.getIdStr().orElse("-unset-"));
		}

		@Override
		public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
				throws RtspProtoIdInputSourceNotFoundException {
			if (! existsInputSourceId(idInputSource)) {
				throw new RtspProtoIdInputSourceNotFoundException(idInputSource.toString());
			}
			RtspProtoInputSource resObj = new RtspProtoInputSource();
			resObj.setIdInputSource(idInputSource);
			resObj.setEnabled(true);
			resObj.setNeedsAuthentication(false);
			resObj.setNeedsEncryption(idInputSource.getIdStr().orElseThrow().contains("_with_encr"));
			return resObj;
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstVideoStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			if (! existsInputSourceId(idInputSource)) {
				return Optional.empty();
			}
			RtspProtoStreamSource resObj = new RtspProtoStreamSource();
			resObj.setIdStreamSource(RtspProtoIdStreamSource.of("dummy-stream-source-video"));
			return Optional.of(resObj);
		}

		@Override
		public Optional<RtspProtoStreamSource> getFirstAudioStreamSourceObj(@NonNull RtspProtoIdInputSource idInputSource) {
			if (! existsInputSourceId(idInputSource)) {
				return Optional.empty();
			}
			RtspProtoStreamSource resObj = new RtspProtoStreamSource();
			resObj.setIdStreamSource(RtspProtoIdStreamSource.of("dummy-stream-source-audio"));
			return Optional.of(resObj);
		}

		@Override
		public @NonNull StreamSourceInfo getStreamSourceInfo(@NonNull RtspProtoIdStreamSource idStreamSource)
				throws RtspProtoIdStreamSourceNotFoundException {
			if (idStreamSource.getIdStr().orElse("-unset-").equals("dummy-stream-source-video")) {
				return new StreamSourceInfo(
						RtpPacketType.V_H264,
						true,
						false,
						URI.create("file:///dummy-file-video"),
						(byte)-1,
						-1,
						false,
						-1,
						"",
						15.0
					);
			}
			if (idStreamSource.getIdStr().orElse("-unset-").equals("dummy-stream-source-audio")) {
				return new StreamSourceInfo(
						RtpPacketType.A_LINEAR_PCM_S16_441K_MONO,
						true,
						false,
						URI.create("file:///dummy-file-audio"),
						(byte)2,
						44100,
						false,
						-1,
						"",
						-1.0
					);
			}
			throw new RtspProtoIdStreamSourceNotFoundException("ss='" + idStreamSource.getIdStr().orElse("-unset-") + "'");
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

	enum ClientType {
		SDES,
		MIKEY
	}

	private final @NonNull RtspProtoDataCntMessageTypes srvCfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	private final @NonNull TestLogs logger = new TestLogs();
	private @Nullable ServerSocket socketServer = null;
	private @Nullable Socket socketClient = null;
	private @Nullable Socket socketPeer = null;

	private RtxpTcpReadWrite srvRtxpTcpReadWrite = null;
	private RtspProtoSessionInfo srvSessionInfo = null;
	private AvailableStreamsServerSide srvAvailableStreams;
	private RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc;
	private RtspProtoRequestOutputSvc srvRequOutputSvc = null;
	private RtspProtoResponseInputSvc srvRespInputSvc = null;
	private RtspProtoRequestInputSvc srvRequInputSvc = null;
	private RtspProtoResponseOutputSvc srvRespOutputSvc = null;

	private final Map<ClientType, String> cliUserAgent = new HashMap<>() {{
			put(ClientType.SDES, "client with sdes/0.9");
			put(ClientType.MIKEY, "ModernClient/98.1.2");
		}};
	private final Map<ClientType, RtxpTcpReadWrite> cliRtxpTcpReadWrite = new HashMap<>();
	private final Map<ClientType, RtspProtoSessionInfo> cliSessionInfo = new HashMap<>();
	private final Map<ClientType, RtspProtoRequestInputSvc> cliRequInputSvc = new HashMap<>();
	private final Map<ClientType, RtspProtoResponseOutputSvc> cliRespOutputSvc = new HashMap<>();
	private final Map<ClientType, RtspProtoRequestOutputSvc> cliRequOutputSvc = new HashMap<>();
	private final Map<ClientType, RtspProtoResponseInputSvc> cliRespInputSvc = new HashMap<>();

	S2cRrSvcTest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@BeforeEach
	void setUp() throws Exception {
		initRtxpTcpReadWrite();
		initObjsServer();
		initObjsClient(ClientType.SDES);
		initObjsClient(ClientType.MIKEY);
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
	void test_announce_ok_noCrypto1() throws Exception {
		// client sends DESCRIBE request to server
		doDescribe(ClientType.SDES, "rtsp://localhost/existing_stream_no_auth_no_encr");
		// server sends ANNOUNCE request to client
		doAnnounce_noCrypto(ClientType.SDES);
	}

	@Test
	void test_announce_ok_noCrypto2() throws Exception {
		// client sends DESCRIBE request to server
		doDescribe(ClientType.MIKEY, "rtsp://localhost/existing_stream_no_auth_no_encr");
		// server sends ANNOUNCE request to client
		doAnnounce_noCrypto(ClientType.MIKEY);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_announce_ok_withCryptoSdes() throws Exception {
		final ClientType ct = ClientType.SDES;

		// client sends DESCRIBE request to server
		doDescribe(ct, "rtsp://localhost/existing_stream_no_auth_with_encr");
		// client sends ANNOUNCE request to server - which contains the client's outbound KMDs
		// @TODO sendRequest_srtxpInitialOutboundSdes()
		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfo.get(ct).getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfo.get(ct).getDescrAvailableSubStreamIds()) {
			doSetup(
					ct,
					"rtsp://localhost/existing_stream_no_auth_with_encr/" +
							tmpIdSubStream.getIdStr().orElse("-unset-"),
					true
				);
		}
		doCheckSetupClientSide(ct, true);
		// server sends OPTIONS request to client
		doOptions(ct);
		// server sends ANNOUNCE request to client
		doAnnounce_withCryptoSdes();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_setParam_ok_withCryptoMikey_udp() throws Exception {
		do_setParam_ok_withCryptoMikey(true);
	}

	@Test
	void test_setParam_ok_withCryptoMikey_tcp() throws Exception {
		do_setParam_ok_withCryptoMikey(false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void do_setParam_ok_withCryptoMikey(boolean useTransportUdp) throws Exception {
		final ClientType ct = ClientType.MIKEY;

		// client sends DESCRIBE request to server
		doDescribe(ct, "rtsp://localhost/existing_stream_no_auth_with_encr");
		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfo.get(ct).getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfo.get(ct).getDescrAvailableSubStreamIds()) {
			doSetup(
					ct,
					"rtsp://localhost/existing_stream_no_auth_with_encr/" +
							tmpIdSubStream.getIdStr().orElse("-unset-"),
					useTransportUdp
				);
		}
		doCheckSetupClientSide(ct, useTransportUdp);
		// server sends OPTIONS request to client
		doOptions(ct);
		// server sends SET_PARAMETER request to client
		doSetParam_withCryptoMikey();
	}

	private void doDescribe(ClientType ct, @NonNull String resourceUrl) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_describe(resourceUrl);
		assertEquals(RtspProtoMessageType.DESCRIBE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas, outputRequ);

		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, srvSessionInfo.getDescrAvailableSubStreamIds().size());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfo.get(ct).getServerSoftware().orElseThrow());

		assertEquals(2, cliSessionInfo.get(ct).getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, cliSessionInfo.get(ct).getDescrAvailableSubStreamIds().size());
	}

	private void doSetup(ClientType ct, @NonNull String resourceUrlSubStream, boolean useTransportUdp) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_setup(
				resourceUrlSubStream,
				useTransportUdp
			);
		assertEquals(RtspProtoMessageType.SETUP, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr(), outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas, outputRequ);

		// ----------------------------------------------------

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());
	}

	private void doCheckSetupClientSide(ClientType ct, boolean useTransportUdp) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		Set<RtspProtoIdSubStream> checkSubStrIds = cliSessionInfoPtr.getDescrSetupInfoSubStreamIds();
		assertEquals(checkSubStrIds, cliSessionInfoPtr.getDescrAvailableSubStreamIds());
		int maxTcpChann = -1;
		for (RtspProtoIdSubStream subStrId : checkSubStrIds) {
			assertTrue(
					cliSessionInfoPtr.getDescrSetupInfoHaveSetupForSubStreamId(subStrId),
					"missing HaveSetup: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			RtspProtoSetupInfoForSubStream tmpSiForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(subStrId);
			assertFalse(tmpSiForSs.getSsrcInboundPtr().isEmpty());
			assertFalse(tmpSiForSs.getSsrcOutboundPtr().isEmpty());
			assertNotEquals(tmpSiForSs.getSsrcInboundPtr(), tmpSiForSs.getSsrcOutboundPtr());
			/*System.out.println("- ss=" + subStrId.getIdStr().orElse("-unset-") + ": client inbound_ SSRC: " +
					tmpSiForSs.getSsrcInboundPtr().toHexString(true));
			System.out.println("- ss=" + subStrId.getIdStr().orElse("-unset-") + ": client outbound SSRC: " +
					tmpSiForSs.getSsrcOutboundPtr().toHexString(true));*/
			assertTrue(
					tmpSiForSs.getKmdInboundCurPtr().isKmdSet(),
					"missing KmdInbound: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSs.getSsrcInboundPtr(), tmpSiForSs.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSs.getKmdInboundCurPtr().getKmd().orElseThrow().mki());
			}
			assertTrue(
					tmpSiForSs.getKmdOutboundPtr().isKmdSet(),
				"missing KmdOutbound: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSs.getSsrcOutboundPtr(), tmpSiForSs.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSs.getKmdOutboundPtr().getKmd().orElseThrow().mki());
			} else {
				assertEquals(1, tmpSiForSs.getKmdOutboundPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
			}
			assertTrue(tmpSiForSs.getRtpSeqNrT0Ptr().isEmpty());  // we need to make a PLAY request first
			assertEquals(useTransportUdp, tmpSiForSs.getSubStreamTpPtr().getIsUdp());
			assertTrue(tmpSiForSs.getSubStreamTpPtr().getIsEncr());
			assertTrue(tmpSiForSs.getSubStreamTpPtr().getIsUnicast());
			assertNotEquals(useTransportUdp, tmpSiForSs.getSubStreamTpPtr().getIsInterleaved());
			if (useTransportUdp) {
				assertNotNull(tmpSiForSs.getClientUdpSocketRtpPtr());
				assertNotNull(tmpSiForSs.getClientUdpSocketRtcpPtr());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getServerUdpPortRtpPtr().isEmpty());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getServerUdpPortRtcpPtr().isEmpty());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty());
			} else {
				assertNull(tmpSiForSs.getClientUdpSocketRtpPtr());
				assertNull(tmpSiForSs.getClientUdpSocketRtcpPtr());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getServerUdpPortRtpPtr().isEmpty());
				assertTrue(tmpSiForSs.getSubStreamTpPtr().getServerUdpPortRtcpPtr().isEmpty());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty());
				assertFalse(tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty());
				assertNotEquals(
						tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtpPtr().getChannel8bit().orElseThrow(),
						tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow()
					);
				assertNotEquals(maxTcpChann, tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow());
				if (tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow() > maxTcpChann) {
					maxTcpChann = tmpSiForSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow();
				}
			}
		}
	}

	private void doAnnounce_noCrypto(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_announce(
				"rtsp://localhost/existing_stream_no_auth_no_encr"
			);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer(outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		Optional<RtspProtoSdpDataMediaEntry> tmpMediaEntry = outputRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.VIDEO
			);
		assertTrue(tmpMediaEntry.isPresent());
		tmpMediaEntry = outputRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.AUDIO
			);
		assertTrue(tmpMediaEntry.isPresent());

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas, outputRequ);
		assertEquals("server name and version", cliSessionInfo.get(ct).getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void doOptions(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_options(
				"rtsp://localhost/existing_stream_no_auth_with_encr"
			);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer(outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas, outputRequ);
		assertEquals("server name and version", cliSessionInfo.get(ct).getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void doAnnounce_withCryptoSdes() throws Exception {
		final ClientType ct = ClientType.SDES;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfo.get(ct).getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		RtspProtoKmdsStream kmdsOutbound = new RtspProtoKmdsStream();

		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : srvSessionInfo.getDescrSetupInfoSubStreamIds()) {
			SrtxpKmd kmdOutboundSs = srvRequOutputSvc.generateNewOutboundKmdForRekeying(tmpIdSubStream);

			kmdsOutbound.putKmdForSubStream(kmdOutboundSs, tmpIdSubStream);
		}

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_srtxpRekeyOutboundSdes(
				"rtsp://localhost/existing_stream_no_auth_with_encr",
				kmdsOutbound
			);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer(outputRequ);
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		Optional<RtspProtoSdpDataMediaEntry> tmpMediaEntry = outputRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.VIDEO
			);
		assertTrue(tmpMediaEntry.isPresent());
		RtspProtoIdSubStream tmpIdSs = outputRequ.requAnnouncedSdpStc.extractMediaEntryControlId(tmpMediaEntry.get()).orElseThrow();
		assertTrue(cliSessionInfo.get(ct).getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSs));
		Optional<SrtxpKmd> tmpSrtxpKmd = outputRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(
				tmpMediaEntry.get()
			);
		assertTrue(tmpSrtxpKmd.isPresent());
		assertTrue(tmpSrtxpKmd.get().getMetaIsForLegacySdes());
		assertEquals(2, tmpSrtxpKmd.get().getMetaTagForLegacySdes().orElseThrow());

		tmpMediaEntry = outputRequ.requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.AUDIO
			);
		assertTrue(tmpMediaEntry.isPresent());
		tmpIdSs = outputRequ.requAnnouncedSdpStc.extractMediaEntryControlId(tmpMediaEntry.get()).orElseThrow();
		assertTrue(cliSessionInfo.get(ct).getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSs));
		tmpSrtxpKmd = outputRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(
				tmpMediaEntry.get()
			);
		assertTrue(tmpSrtxpKmd.isPresent());
		assertTrue(tmpSrtxpKmd.get().getMetaIsForLegacySdes());
		assertEquals(2, tmpSrtxpKmd.get().getMetaTagForLegacySdes().orElseThrow());

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas, outputRequ);
		assertEquals("server name and version", cliSessionInfo.get(ct).getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void doSetParam_withCryptoMikey() throws Exception {
		final ClientType ct = ClientType.MIKEY;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : srvSessionInfo.getDescrSetupInfoSubStreamIds()) {
			SrtxpKmd kmdOutboundSs = srvRequOutputSvc.generateNewOutboundKmdForRekeying(tmpIdSubStream);

			RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_srtxpRekeyOutboundMikey(
					"rtsp://localhost/existing_stream_no_auth_with_encr/" +
							tmpIdSubStream.getIdStr().orElseThrow(),
					tmpIdSubStream,
					kmdOutboundSs
				);
			assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

			// ----------------------------------------------------

			RtspProtoDataRequest outputRequ = new RtspProtoDataRequest();
			RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer(outputRequ);
			assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);
			assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().ssrcId());
			assertEquals(SrtxpMki.of(2, 4), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().mki());

			// ----------------------------------------------------

			cliRespOutputSvc.get(ct).sendResponse(resRequBas, outputRequ);
			assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

			// ----------------------------------------------------

			RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
			assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
			assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());

			RtspProtoSetupInfoForSubStream tmpSiForSsSrv = srvSessionInfo.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);
			assertEquals(tmpSiForSsSrv.getSsrcOutboundPtr(), tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
			assertEquals(SrtxpMki.of(2, 4), tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow().mki());

			assertEquals(tmpSiForSsSrv.getSsrcOutboundPtr(), tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId());
			assertEquals(tmpSiForSsSrv.getSsrcOutboundPtr(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().ssrcId());

			assertEquals(tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow());

			assertTrue(tmpSiForSsSrv.getKmdInboundCurPtr().isKmdSet());
			assertEquals(tmpSiForSsSrv.getKmdInboundCurPtr().getKmd().orElseThrow(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow());
			assertEquals(tmpSiForSsSrv.getSsrcInboundPtr(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
		}
	}

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

		srvRtxpTcpReadWrite = new RtxpTcpReadWrite(socketPeer);

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
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SETUP);

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
				"test_substream_id_prefix",
				false,
				true,
				false,
				srvSessionInfo,
				srvAvailableStreams,
				srvGlobalSessionInfoSvc,
				null,
				(@NonNull String clientUserAgent) -> clientUserAgent.startsWith("client with sdes"),
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

	private void initObjsClient(ClientType ct) {
		Objects.requireNonNull(socketClient);

		cliRtxpTcpReadWrite.put(ct, new RtxpTcpReadWrite(socketClient));

		cliSessionInfo.put(ct, new RtspProtoSessionInfo());

		initObjsClient_toServer(ct);
		initObjsClient_fromServer(ct);
	}

	private void initObjsClient_toServer(ClientType ct) {
		cliRequOutputSvc.put(ct, new RtspProtoRequestOutputSvc(
				logger,
				true,
				cliUserAgent.get(ct),
				"en",
				false,
				true,
				Objects.requireNonNull(cliSessionInfo.get(ct)),
				Objects.requireNonNull(cliRtxpTcpReadWrite.get(ct)),
				null,
				null
			));

		cliRespInputSvc.put(ct, new RtspProtoResponseInputSvc(
				logger,
				false,
				true,
				Objects.requireNonNull(cliSessionInfo.get(ct)),
				Objects.requireNonNull(cliRtxpTcpReadWrite.get(ct))
			));
	}

	private void initObjsClient_fromServer(ClientType ct) {
		RtspProtoDataCntMessageTypes cliCfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
		cliCfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		cliCfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		cliCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);

		cliRequInputSvc.put(ct, new RtspProtoRequestInputSvc(
				logger,
				false,
				RtxpLogLevel.DEBUG,
				cliCfgSupportedMessageTypes,
				Set.of(),
				Set.of(),
				true,
				false,
				Objects.requireNonNull(cliSessionInfo.get(ct)),
				null,
				null,
				null,
				null,
				Objects.requireNonNull(cliRtxpTcpReadWrite.get(ct))
			));

		cliRespOutputSvc.put(ct, new RtspProtoResponseOutputSvc(
				logger,
				true,
				cliUserAgent.get(ct),
				"en",
				cliCfgSupportedMessageTypes,
				"",
				false,
				true,
				false,
				Objects.requireNonNull(cliSessionInfo.get(ct)),
				null,
				null,
				null,
				null,
				Objects.requireNonNull(cliRtxpTcpReadWrite.get(ct))
			));
	}

}
