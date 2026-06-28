package org.tsitle.lib_xrtxp.rtsp.msgs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.*;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterGetterInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
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

public class FullBidirRequRespSvcTest {

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

	static class ParameterGetterSetterServerSide implements RtspProtoParameterGetterInterface, RtspProtoParameterSetterInterface {
		private double jitterValue = 0.0;
		private double latencyValue = 0.0;
		private double subVideoSpeedValue = 0.0;
		private double subAudioVolumeValue = 0.0;
		private final @NonNull RtspProtoSessionInfo sessionInfo;
		private final @NonNull RtspProtoIdSubStream videoSubStreamId = RtspProtoIdSubStream.ofEmpty();
		private final @NonNull RtspProtoIdSubStream audioSubStreamId = RtspProtoIdSubStream.ofEmpty();

		ParameterGetterSetterServerSide(@NonNull RtspProtoSessionInfo sessionInfo) {
			this.sessionInfo = sessionInfo;
		}

		public void setVideoSubStreamId(@NonNull RtspProtoIdSubStream idSs) {
			this.videoSubStreamId.copyFrom(idSs);
		}

		public void setAudioSubStreamId(@NonNull RtspProtoIdSubStream idSs) {
			this.audioSubStreamId.copyFrom(idSs);
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
			if (! idSubStream.isEmpty() && idSubStream.equals(videoSubStreamId) && key.equals("speed")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					subVideoSpeedValue = tmpDbl;
				}
				return;
			}
			if (! idSubStream.isEmpty() && idSubStream.equals(audioSubStreamId) && key.equals("volume")) {
				double tmpDbl = Double.parseDouble(value);
				if (tmpDbl < 0.0) {
					throw new RtspProtoRtspParamInvalidValueException("xxx");
				}
				if (! dryRunOnly) {
					subAudioVolumeValue = tmpDbl;
				}
				return;
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
				} else if (! idSubStream.isEmpty() && idSubStream.equals(videoSubStreamId)) {
					resObj.putParamKvsEntry("speed", doubleToString(subVideoSpeedValue));
				} else if (! idSubStream.isEmpty() && idSubStream.equals(audioSubStreamId)) {
					resObj.putParamKvsEntry("volume", doubleToString(subAudioVolumeValue));
				}
			}
			return resObj;
		}

		private @NonNull String doubleToString(double value) {
			return Double.toString(value).replace(",", ".");
		}

		private boolean checkSessionId(@NonNull RtspProtoIdSession idSession) {
			return idSession.equals(sessionInfo.getIdSession());
		}

		private boolean checkInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
			Set<RtspProtoIdInputSource> availIss = new HashSet<>();
			availIss.add(RtspProtoIdInputSource.of("existing_stream_no_auth_no_encr"));
			availIss.add(RtspProtoIdInputSource.of("existing_stream_no_auth_with_encr"));
			return availIss.contains(idInputSource);
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
	private AvailableStreamsServerSide srvAvailableStreams = null;
	private RtspProtoGlobalSessionInfoSvc srvGlobalSessionInfoSvc = null;
	private ParameterGetterSetterServerSide srvParameterGetterSetter = null;
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

	FullBidirRequRespSvcTest() { }

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
		do_c2s_Describe(ClientType.SDES, "rtsp://localhost/existing_stream_no_auth_no_encr");
		// server sends ANNOUNCE request to client
		do_s2c_Announce_noCrypto(ClientType.SDES);
	}

	@Test
	void test_announce_ok_noCrypto2() throws Exception {
		// client sends DESCRIBE request to server
		do_c2s_Describe(ClientType.MIKEY, "rtsp://localhost/existing_stream_no_auth_no_encr");
		// server sends ANNOUNCE request to client
		do_s2c_Announce_noCrypto(ClientType.MIKEY);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_rekeying_ok_sdes_announceBeforeSetup() throws Exception {
		internal_test_rekeying_ok_sdes(true, true);
	}

	@Test
	void test_rekeying_ok_sdes_announceAfterSetup_udp() throws Exception {
		internal_test_rekeying_ok_sdes(true, false);
	}

	@Test
	void test_rekeying_ok_sdes_announceAfterSetup_tcp() throws Exception {
		internal_test_rekeying_ok_sdes(false, false);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_rekeying_ok_mikey_udp() throws Exception {
		internal_test_rekeying_ok_mikey(true);
	}

	@Test
	void test_rekeying_ok_mikey_tcp() throws Exception {
		internal_test_rekeying_ok_mikey(false);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_options_on_substream() throws Exception {
		final ClientType ct = ClientType.MIKEY;
		final String rscUrlStr = "rtsp://localhost/existing_stream_no_auth_with_encr";
		final boolean useTransportUdp = true;

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// client sends DESCRIBE request to server
		do_c2s_Describe(ct, rscUrlStr);

		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			do_c2s_Setup(
					ct,
					rscUrlForSs,
					useTransportUdp
				);
		}

		// client sends OPTIONS requests to server
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			do_c2s_Options(ct, rscUrlForSs.getUrlStr());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void test_setGetParam_on_substream() throws Exception {
		final ClientType ct = ClientType.MIKEY;
		final String rscUrlStr = "rtsp://localhost/existing_stream_no_auth_with_encr";
		final boolean useTransportUdp = true;

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// client sends DESCRIBE request to server
		do_c2s_Describe(ct, rscUrlStr);

		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			do_c2s_Setup(
					ct,
					rscUrlForSs,
					useTransportUdp
				);
		}

		// find Sub-Stream IDs - method 1
		RtspProtoIdSubStream idSsVideo = RtspProtoIdSubStream.ofEmpty();
		RtspProtoIdSubStream idSsAudio = RtspProtoIdSubStream.ofEmpty();
		List<RtspProtoSdpDataMediaEntry> mediaEntries = cliSessionInfoPtr.getRhDescribeSdpStc().orElseThrow().getMediaEntries();
		for (RtspProtoSdpDataMediaEntry mediaEntry : mediaEntries) {
			if (mediaEntry.header().mediaType() == RtspProtoSdpMediaType.VIDEO) {
				idSsVideo.copyFrom(mediaEntry.controlId());
			} else if (mediaEntry.header().mediaType() == RtspProtoSdpMediaType.AUDIO) {
				idSsAudio.copyFrom(mediaEntry.controlId());
			}
		}

		assertFalse(idSsVideo.isEmpty());
		assertFalse(idSsAudio.isEmpty());

		// find Sub-Stream IDs - method 2
		RtspProtoIdSubStream tmpM2IdSsVideo = cliSessionInfoPtr.getRhDescribeSdpStc().orElseThrow()
				.findFirstMediaEntryOfType(RtspProtoSdpMediaType.VIDEO).orElseThrow()
				.controlId();
		RtspProtoIdSubStream tmpM2IdSsAudio = cliSessionInfoPtr.getRhDescribeSdpStc().orElseThrow()
				.findFirstMediaEntryOfType(RtspProtoSdpMediaType.AUDIO).orElseThrow()
				.controlId();

		assertEquals(idSsVideo, tmpM2IdSsVideo);
		assertEquals(idSsAudio, tmpM2IdSsAudio);

		// client sends SET_PARAMETER request for the entire stream to server
		do_c2s_SetParam_noCrypto(
				ct,
				true,
				false,
				false,
				cliSessionInfoPtr.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow()  // <-- getResourceUrlForMt_nonSetup()
			);

		// client sends SET_PARAMETER request for VIDEO Sub-Stream to server - get Sub-Stream URL method 1
		do_c2s_SetParam_noCrypto(
				ct,
				false,
				true,
				false,
				cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(idSsVideo).getRscUrlSubStreamPtr()  // <-- getDescrSetupInfoBySubStreamsId()
			);

		// client sends SET_PARAMETER request for AUDIO Sub-Stream to server - get Sub-Stream URL method 2
		do_c2s_SetParam_noCrypto(
				ct,
				false,
				false,
				true,
				cliSessionInfoPtr.getResourceUrlForMt_onlySetup(idSsAudio).orElseThrow()  // <-- getResourceUrlForMt_onlySetup()
			);

		// client sends GET_PARAMETER request for the entire stream to server
		do_c2s_GetParam_noCrypto(
				ct,
				true,
				false,
				false,
				cliSessionInfoPtr.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow()  // <-- getResourceUrlForMt_nonSetup()
			);

		// client sends GET_PARAMETER request for VIDEO Sub-Stream to server
		do_c2s_GetParam_noCrypto(
				ct,
				false,
				true,
				false,
				cliSessionInfoPtr.getResourceUrlForMt_onlySetup(idSsVideo).orElseThrow()  // <-- getResourceUrlForMt_onlySetup()
			);

		// client sends GET_PARAMETER request for AUDIO Sub-Stream to server
		do_c2s_GetParam_noCrypto(
				ct,
				false,
				false,
				true,
				cliSessionInfoPtr.getResourceUrlForMt_onlySetup(idSsAudio).orElseThrow()  // <-- getResourceUrlForMt_onlySetup()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	@Disabled
	void test_play_pause_teardown() {
		// @TODO
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	@Disabled
	void test_redirect() {
		// @TODO
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internal_test_rekeying_ok_sdes(boolean useTransportUdp, boolean announceInitialClientKeysBeforeSetup)
			throws Exception {
		final ClientType ct = ClientType.SDES;
		final String rscUrlStr = "rtsp://localhost/existing_stream_no_auth_with_encr";

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// client sends DESCRIBE request to server
		do_c2s_Describe(ct, rscUrlStr);

		//
		if (announceInitialClientKeysBeforeSetup) {
			// client sends ANNOUNCE request to server - which contains the client's outbound KMDs
			do_c2s_InitialAnnounce_sdes(rscUrlStr);
		}

		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			assertEquals(rscUrlStr + "/" + tmpIdSubStream.getIdStr().orElse("-unset-"), rscUrlForSs.getUrlStr());
			assertFalse(rscUrlForSs.idSubStream.isEmpty());
			do_c2s_Setup(
					ct,
					rscUrlForSs,
					useTransportUdp
				);
			//
			RtspProtoRscUrl checkRscUrlForSsSrv = srvSessionInfo.getResourceUrlForMt_onlySetup(tmpIdSubStream).orElseThrow();
			assertEquals(rscUrlForSs, checkRscUrlForSsSrv);
			RtspProtoRscUrl checkRscUrlForSsClient = cliSessionInfoPtr.getResourceUrlForMt_onlySetup(tmpIdSubStream).orElseThrow();
			assertEquals(rscUrlForSs, checkRscUrlForSsClient);
		}

		//
		if (! announceInitialClientKeysBeforeSetup) {
			// client sends ANNOUNCE request to server - which contains the client's initial outbound KMDs
			do_c2s_InitialAnnounce_sdes(rscUrlStr);
		}

		//
		do_checkSetupClientSide(ct, useTransportUdp);

		// server sends OPTIONS request to client
		do_s2c_Options(ct);

		// server sends ANNOUNCE request to client - which contains the server's new outbound KMDs
		do_s2c_RekeyingAnnounce_sdes();
		do_checkKeys_afterSrvRekeying_clientAndServer(ct);

		// client sends OPTIONS request to server
		RtspProtoRscUrl rscUrl = cliSessionInfoPtr.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow();
		do_c2s_Options(ct, rscUrl.getUrlStr());

		// client sends ANNOUNCE request to server - which contains the client's new outbound KMDs
		do_c2s_RekeyingAnnounce_sdes();
		do_checkKeys_afterClientRekeying_clientAndServer(ct);
	}

	private void internal_test_rekeying_ok_mikey(boolean useTransportUdp) throws Exception {
		final ClientType ct = ClientType.MIKEY;
		final String rscUrlStr = "rtsp://localhost/existing_stream_no_auth_with_encr";

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// client sends DESCRIBE request to server
		do_c2s_Describe(ct, rscUrlStr);

		// client sends SETUP requests to server
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			do_c2s_Setup(
					ct,
					rscUrlForSs,
					useTransportUdp
				);
			//
			RtspProtoRscUrl checkRscUrlForSsSrv = srvSessionInfo.getResourceUrlForMt_onlySetup(tmpIdSubStream).orElseThrow();
			assertEquals(rscUrlForSs, checkRscUrlForSsSrv);
			RtspProtoRscUrl checkRscUrlForSsClient = cliSessionInfoPtr.getResourceUrlForMt_onlySetup(tmpIdSubStream).orElseThrow();
			assertEquals(rscUrlForSs, checkRscUrlForSsClient);
		}
		do_checkSetupClientSide(ct, useTransportUdp);

		// server sends OPTIONS request to client
		do_s2c_Options(ct);

		// server sends SET_PARAMETER request to client - which contains the server's new outbound KMDs
		do_s2c_RekeyingSetParam_mikey();
		do_checkKeys_afterSrvRekeying_clientAndServer(ct);

		// client sends OPTIONS request to server
		RtspProtoRscUrl rscUrl = cliSessionInfoPtr.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow();
		do_c2s_Options(ct, rscUrl.getUrlStr());

		// client sends SET_PARAMETER request to server - which contains the client's new outbound KMDs
		do_c2s_RekeyingSetParam_mikey();
		do_checkKeys_afterClientRekeying_clientAndServer(ct);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void do_c2s_Describe(ClientType ct, @NonNull String resourceUrl) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_describe(resourceUrl);
		assertEquals(RtspProtoMessageType.DESCRIBE, mt);

		RtspProtoRscUrl checkRscUrlForSsClient = cliSessionInfoPtr.getResourceUrlForMt_nonSetup(mt).orElseThrow();
		assertEquals(resourceUrl, checkRscUrlForSsClient.getUrlStr());

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());

		RtspProtoRscUrl checkRscUrlForSsSrv = srvSessionInfo.getResourceUrlForMt_nonSetup(mt).orElseThrow();
		assertEquals(resourceUrl, checkRscUrlForSsSrv.getUrlStr());

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, srvSessionInfo.getDescrAvailableSubStreamIds().size());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
	}

	private void do_c2s_Setup(ClientType ct, @NonNull RtspProtoRscUrl rscUrlForSs, boolean useTransportUdp) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		assertFalse(rscUrlForSs.idSubStream.isEmpty());

		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl tmpCheckRscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			assertFalse(tmpCheckRscUrlForSs.idSubStream.isEmpty());
		}

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_setup(
				rscUrlForSs,
				useTransportUdp
			);
		assertEquals(RtspProtoMessageType.SETUP, mt);

		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrAvailableSubStreamIds()) {
			RtspProtoRscUrl tmpCheckRscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();
			assertFalse(tmpCheckRscUrlForSs.idSubStream.isEmpty());
		}

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());
	}

	private void do_checkSetupClientSide(ClientType ct, boolean useTransportUdp) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		Set<RtspProtoIdSubStream> checkSubStrIds = cliSessionInfoPtr.getDescrSetupInfoSubStreamIds();
		assertEquals(2, cliSessionInfoPtr.getDescrAvailableSubStreamIds().size());
		int maxTcpChann = -1;

		for (RtspProtoIdSubStream subStrId : checkSubStrIds) {
			assertTrue(
					cliSessionInfoPtr.getDescrSetupInfoHaveSetupForSubStreamId(subStrId),
					"missing HaveSetup: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(subStrId);

			assertFalse(tmpSiForSsClient.getSsrcInboundPtr().isEmpty());
			assertFalse(tmpSiForSsClient.getSsrcOutboundPtr().isEmpty());
			assertNotEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getSsrcOutboundPtr());
			/*System.out.println("- ss=" + subStrId.getIdStr().orElse("-unset-") + ": client inbound_ SSRC: " +
					tmpSiForSsClient.getSsrcInboundPtr().toHexString(true));
			System.out.println("- ss=" + subStrId.getIdStr().orElse("-unset-") + ": client outbound SSRC: " +
					tmpSiForSsClient.getSsrcOutboundPtr().toHexString(true));*/
			assertTrue(
					tmpSiForSsClient.getKmdInboundCurPtr().isKmdSet(),
					"missing KmdInbound: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().mki());
			}
			assertTrue(
					tmpSiForSsClient.getKmdOutboundPtr().isKmdSet(),
				"missing KmdOutbound: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsClient.getSsrcOutboundPtr(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().mki());
			} else {
				assertEquals(1, tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
			}
			assertTrue(tmpSiForSsClient.getRtpSeqNrT0Ptr().isEmpty());  // we need to make a PLAY request first
			assertEquals(useTransportUdp, tmpSiForSsClient.getSubStreamTpPtr().getIsUdp());
			assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getIsEncr());
			assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getIsUnicast());
			assertNotEquals(useTransportUdp, tmpSiForSsClient.getSubStreamTpPtr().getIsInterleaved());
			if (useTransportUdp) {
				assertNotNull(tmpSiForSsClient.getClientUdpSocketRtpPtr());
				assertNotNull(tmpSiForSsClient.getClientUdpSocketRtcpPtr());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getServerUdpPortRtpPtr().isEmpty());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getServerUdpPortRtcpPtr().isEmpty());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty());
			} else {
				assertNull(tmpSiForSsClient.getClientUdpSocketRtpPtr());
				assertNull(tmpSiForSsClient.getClientUdpSocketRtcpPtr());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getServerUdpPortRtpPtr().isEmpty());
				assertTrue(tmpSiForSsClient.getSubStreamTpPtr().getServerUdpPortRtcpPtr().isEmpty());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty());
				assertFalse(tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty());
				assertNotEquals(
						tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtpPtr().getChannel8bit().orElseThrow(),
						tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow()
					);
				assertNotEquals(maxTcpChann, tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow());
				if (tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow() > maxTcpChann) {
					maxTcpChann = tmpSiForSsClient.getSubStreamTpPtr().getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow();
				}
			}
		}
	}

	@SuppressWarnings("SameParameterValue")
	private void do_c2s_InitialAnnounce_sdes(@NonNull String resourceUrl) throws Exception {
		final ClientType ct = ClientType.SDES;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		Set<RtspProtoIdSubStream> previousSubStrIds = cliSessionInfoPtr.getDescrSetupInfoSubStreamIds();
		assertEquals(2, previousSubStrIds.size());
		Set<RtspProtoIdXsrc> previousInboundSsrcs = new HashSet<>();
		Set<RtspProtoIdXsrc> previousOutboundSsrcs = new HashSet<>();
		for (RtspProtoIdSubStream subStrId : previousSubStrIds) {
			assertFalse(subStrId.isEmpty(), "Sub-Stream ID must not be empty");

			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(subStrId);

			assertFalse(tmpSiForSsClient.getSsrcInboundPtr().isEmpty());
			previousInboundSsrcs.add(tmpSiForSsClient.getSsrcInboundPtr());
			assertFalse(tmpSiForSsClient.getSsrcOutboundPtr().isEmpty());
			previousOutboundSsrcs.add(tmpSiForSsClient.getSsrcOutboundPtr());
		}

		// ----------------------------------------------------

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_srtxpInitialOutboundSdes(resourceUrl);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);

		// ----------------------------------------------------

		Set<RtspProtoIdSubStream> newSubStrIds = cliSessionInfoPtr.getDescrSetupInfoSubStreamIds();
		Set<RtspProtoIdXsrc> newInboundSsrcs = new HashSet<>();
		Set<RtspProtoIdXsrc> newOutboundSsrcs = new HashSet<>();
		for (RtspProtoIdSubStream subStrId : newSubStrIds) {
			assertFalse(subStrId.isEmpty(), "Sub-Stream ID must not be empty");

			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(subStrId);

			assertFalse(tmpSiForSsClient.getSsrcInboundPtr().isEmpty());
			newInboundSsrcs.add(tmpSiForSsClient.getSsrcInboundPtr());
			assertFalse(tmpSiForSsClient.getSsrcOutboundPtr().isEmpty());
			newOutboundSsrcs.add(tmpSiForSsClient.getSsrcOutboundPtr());
		}
		assertEquals(previousSubStrIds, newSubStrIds);
		assertEquals(previousInboundSsrcs, newInboundSsrcs);
		assertEquals(previousOutboundSsrcs, newOutboundSsrcs);

		for (RtspProtoIdSubStream subStrId : newSubStrIds) {
			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(subStrId);

			assertNotEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getSsrcOutboundPtr());
			assertTrue(
					tmpSiForSsClient.getKmdOutboundPtr().isKmdSet(),
					"missing KmdOutbound: ss=" + subStrId.getIdStr().orElse("-unset-")
				);
			assertEquals(1, tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
		}
	}

	private void do_s2c_Options(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		RtspProtoRscUrl rscUrl = srvSessionInfo.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow();

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_options(rscUrl.getUrlStr());
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void do_s2c_Announce_noCrypto(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		RtspProtoRscUrl rscUrl = srvSessionInfo.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow();

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_announce(rscUrl.getUrlStr());
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);
		assertTrue(cliSessionInfoPtr.getRhAnnouncedSdpStc().isPresent());
		RtspProtoDataCntSdpStructured requAnnouncedSdpStc = cliSessionInfoPtr.getRhAnnouncedSdpStc().orElseThrow();
		Optional<RtspProtoSdpDataMediaEntry> tmpMediaEntry = requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.VIDEO
			);
		assertTrue(tmpMediaEntry.isPresent());
		tmpMediaEntry = requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.AUDIO
			);
		assertTrue(tmpMediaEntry.isPresent());

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void do_s2c_RekeyingAnnounce_sdes() throws Exception {
		final ClientType ct = ClientType.SDES;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		RtspProtoKmdsStream kmdsOutbound = new RtspProtoKmdsStream();

		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());
		for (RtspProtoIdSubStream tmpIdSubStream : srvSessionInfo.getDescrSetupInfoSubStreamIds()) {
			SrtxpKmd kmdOutboundSs = srvRequOutputSvc.generateNewOutboundKmdForRekeying(tmpIdSubStream);

			kmdsOutbound.putKmdForSubStream(kmdOutboundSs, tmpIdSubStream);
		}

		RtspProtoRscUrl rscUrl = srvSessionInfo.getResourceUrlForMt_nonSetup(RtspProtoMessageType.DESCRIBE).orElseThrow();

		RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_srtxpRekeyOutboundSdes(
				rscUrl.getUrlStr(),
				kmdsOutbound
			);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer();
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		assertTrue(cliSessionInfoPtr.getRhAnnouncedSdpStc().isPresent());
		RtspProtoDataCntSdpStructured requAnnouncedSdpStc = cliSessionInfoPtr.getRhAnnouncedSdpStc().orElseThrow();
		Optional<RtspProtoSdpDataMediaEntry> tmpMediaEntry = requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.VIDEO
			);
		assertTrue(tmpMediaEntry.isPresent());
		RtspProtoIdSubStream tmpIdSs = tmpMediaEntry.get().controlId();
		assertTrue(cliSessionInfoPtr.getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSs));
		Optional<SrtxpKmd> tmpSrtxpKmd = requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(
				tmpMediaEntry.get()
			);
		assertTrue(tmpSrtxpKmd.isPresent());
		assertTrue(tmpSrtxpKmd.get().getMetaIsForLegacySdes());
		assertEquals(2, tmpSrtxpKmd.get().getMetaTagForLegacySdes().orElseThrow());

		tmpMediaEntry = requAnnouncedSdpStc.findFirstMediaEntryOfType(
				RtspProtoSdpMediaType.AUDIO
			);
		assertTrue(tmpMediaEntry.isPresent());
		tmpIdSs = tmpMediaEntry.get().controlId();
		assertTrue(cliSessionInfoPtr.getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSs));
		tmpSrtxpKmd = requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(
				tmpMediaEntry.get()
			);
		assertTrue(tmpSrtxpKmd.isPresent());
		assertTrue(tmpSrtxpKmd.get().getMetaIsForLegacySdes());
		assertEquals(2, tmpSrtxpKmd.get().getMetaTagForLegacySdes().orElseThrow());

		// ----------------------------------------------------

		cliRespOutputSvc.get(ct).sendResponse(resRequBas);
		assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
	}

	private void do_s2c_RekeyingSetParam_mikey() throws Exception {
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

			RtspProtoRscUrl rscUrlForSs = srvSessionInfo.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();

			RtspProtoMessageType mt = srvRequOutputSvc.sendRequest_srtxpRekeyOutboundMikey(rscUrlForSs, kmdOutboundSs);
			assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

			// ----------------------------------------------------

			RtspRequestBasics resRequBas = cliRequInputSvc.get(ct).receiveRequestFromServer();
			assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);
			assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().ssrcId());
			assertEquals(SrtxpMki.of(2, 4), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().mki());

			// ----------------------------------------------------

			cliRespOutputSvc.get(ct).sendResponse(resRequBas);
			assertEquals("server name and version", cliSessionInfoPtr.getServerSoftware().orElseThrow());

			// ----------------------------------------------------

			RtspResponseBasics resRespBas = srvRespInputSvc.receiveResponse();
			assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
			assertEquals(cliUserAgent.get(ct), srvSessionInfo.getClientUserAgent().orElseThrow());
		}
	}

	private void do_checkKeys_afterSrvRekeying_clientAndServer(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		for (RtspProtoIdSubStream tmpIdSubStream : srvSessionInfo.getDescrSetupInfoSubStreamIds()) {
			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);
			RtspProtoSetupInfoForSubStream tmpSiForSsSrv = srvSessionInfo.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);

			// check the client's inbound SSRC vs. the server's outbound SSRC
			assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsSrv.getSsrcOutboundPtr());

			// check client's current inbound KMD's SSRC + MKI
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().mki());
			}

			// check client's next inbound KMD's SSRC + MKI
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsClient.getSsrcInboundPtr(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(2, 4), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().mki());
			}

			// check client's current/next inbound KMD's Tag
			if (ct == ClientType.SDES) {
				assertEquals(1, tmpSiForSsClient.getKmdInboundCurPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
				assertEquals(2, tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
			}

			// check client's outbound KMD's SSRC
			if (ct == ClientType.MIKEY) {
				assertFalse(tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId().isEmpty());
			}

			// check server's current inbound KMD's SSRC + MKI
			if (ct == ClientType.MIKEY) {
				assertTrue(tmpSiForSsSrv.getSsrcInboundPtr().isEmpty());
				assertEquals(tmpSiForSsClient.getSsrcOutboundPtr(), tmpSiForSsSrv.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(1, 4), tmpSiForSsSrv.getKmdInboundCurPtr().getKmd().orElseThrow().mki());
			}

			// check server's current inbound KMD's Tag
			if (ct == ClientType.SDES) {
				assertEquals(1, tmpSiForSsSrv.getKmdInboundCurPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
			}

			// check server's outbound KMD's SSRC + MKI
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsSrv.getSsrcOutboundPtr(), tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(2, 4), tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow().mki());
			}

			// check server's outbound KMD vs. the client's next inbound KMD
			assertEquals(tmpSiForSsSrv.getKmdOutboundPtr().getKmd().orElseThrow(), tmpSiForSsClient.getKmdInboundNextPtr().getKmd().orElseThrow());

			// check server's current inbound KMD vs. the client's outbound KMD
			assertTrue(tmpSiForSsSrv.getKmdInboundCurPtr().isKmdSet());
			assertEquals(tmpSiForSsSrv.getKmdInboundCurPtr().getKmd().orElseThrow(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow());
		}
	}

	private void do_c2s_Options(ClientType ct, @NonNull String resourceUrl) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		// ----------------------------------------------------

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_options(resourceUrl);
		assertEquals(RtspProtoMessageType.OPTIONS, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
	}

	private void do_c2s_RekeyingAnnounce_sdes() throws Exception {
		final ClientType ct = ClientType.SDES;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		RtspProtoKmdsStream kmdsOutboundRekey = new  RtspProtoKmdsStream();
		for (RtspProtoIdSubStream tmpIdSs : cliSessionInfoPtr.getDescrSetupInfoSubStreamIds() ) {
			SrtxpKmd kmdReky = cliRequOutputSvc.get(ct).generateNewOutboundKmdForRekeying(tmpIdSs);
			kmdsOutboundRekey.putKmdForSubStream(kmdReky, tmpIdSs);
		}

		// ----------------------------------------------------

		RtspProtoRscUrl rscUrl = cliSessionInfoPtr.getResourceUrlForMt_nonSetup(RtspProtoMessageType.ANNOUNCE).orElseThrow();

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_srtxpRekeyOutboundSdes(rscUrl.getUrlStr(), kmdsOutboundRekey);
		assertEquals(RtspProtoMessageType.ANNOUNCE, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
	}

	private void do_c2s_RekeyingSetParam_mikey() throws Exception {
		final ClientType ct = ClientType.MIKEY;

		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		for (RtspProtoIdSubStream tmpIdSubStream : cliSessionInfoPtr.getDescrSetupInfoSubStreamIds()) {
			SrtxpKmd kmdOutboundSs = cliRequOutputSvc.get(ct).generateNewOutboundKmdForRekeying(tmpIdSubStream);

			RtspProtoRscUrl rscUrlForSs = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream).getRscUrlSubStreamPtr();

			RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_srtxpRekeyOutboundMikey(rscUrlForSs, kmdOutboundSs);
			assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

			// ----------------------------------------------------

			RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
			assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

			// ----------------------------------------------------

			srvRespOutputSvc.sendResponse(resRequBas);

			// ----------------------------------------------------

			RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
			assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);
		}
	}

	private void do_checkKeys_afterClientRekeying_clientAndServer(ClientType ct) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		assertEquals(2, cliSessionInfoPtr.getDescrSetupInfoSubStreamIds().size());
		assertEquals(2, srvSessionInfo.getDescrSetupInfoSubStreamIds().size());

		// ----------------------------------------------------

		for (RtspProtoIdSubStream tmpIdSubStream : srvSessionInfo.getDescrSetupInfoSubStreamIds()) {
			RtspProtoSetupInfoForSubStream tmpSiForSsClient = cliSessionInfoPtr.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);
			RtspProtoSetupInfoForSubStream tmpSiForSsSrv = srvSessionInfo.getDescrSetupInfoBySubStreamsId(tmpIdSubStream);

			// check client's current outbound KMD's SSRC + MKI
			if (ct == ClientType.MIKEY) {
				assertEquals(tmpSiForSsClient.getSsrcOutboundPtr(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().ssrcId());
				assertEquals(SrtxpMki.of(2, 4), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().mki());
			}

			// check client's outbound KMD's Tag
			if (ct == ClientType.SDES) {
				assertEquals(2, tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().orElseThrow());
			}

			// check server's next inbound KMD vs. the client's outbound KMD
			assertEquals(tmpSiForSsSrv.getKmdInboundNextPtr().getKmd().orElseThrow(), tmpSiForSsClient.getKmdOutboundPtr().getKmd().orElseThrow());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void do_c2s_SetParam_noCrypto(
				ClientType ct,
				boolean isUrlStream,
				boolean isUrlSubStreamVideo,
				boolean isUrlSubStreamAudio,
				@NonNull RtspProtoRscUrl resourceUrl
			) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		// ----------------------------------------------------

		// store the Sub-Stream IDs in the parameter Getter/Setter
		if (isUrlSubStreamVideo) {
			srvParameterGetterSetter.setVideoSubStreamId(resourceUrl.idSubStream);
		} else if (isUrlSubStreamAudio) {
			srvParameterGetterSetter.setAudioSubStreamId(resourceUrl.idSubStream);
		}

		// ----------------------------------------------------

		RtspProtoDataCntGetSetParamKvs setParameterKvs = new RtspProtoDataCntGetSetParamKvs();

		if (isUrlStream) {
			setParameterKvs.putParamKvsEntry("jitter", "1234567.89");
			setParameterKvs.putParamKvsEntry("latency", "864.2");
		} else if (isUrlSubStreamVideo) {
			setParameterKvs.putParamKvsEntry("speed", "1.5");
		} else if (isUrlSubStreamAudio) {
			setParameterKvs.putParamKvsEntry("volume", "99.9");
		} else {
			throw new IllegalArgumentException("isUrlXxx");
		}

		// ----------------------------------------------------

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_setParameter(resourceUrl, setParameterKvs);
		assertEquals(RtspProtoMessageType.SET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);

		// ----------------------------------------------------

		// emulate the server setting its internal parameters
		Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = srvSessionInfo.getRhSetParamValues();
		assertTrue(tmpOptKvs.isPresent());
		for (Map.Entry<@NonNull String, @NonNull String> entry : tmpOptKvs.get().getParamKvsEntrySet()) {
			try {
				srvParameterGetterSetter.setRtspParameter(
						false,
						srvSessionInfo.getIdSession(),
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
	}

	private void do_c2s_GetParam_noCrypto(
				ClientType ct,
				boolean isUrlStream,
				boolean isUrlSubStreamVideo,
				boolean isUrlSubStreamAudio,
				@NonNull RtspProtoRscUrl resourceUrl
			) throws Exception {
		Objects.requireNonNull(cliSessionInfo.get(ct));
		Objects.requireNonNull(srvSessionInfo);

		RtspProtoSessionInfo cliSessionInfoPtr = cliSessionInfo.get(ct);

		// ----------------------------------------------------

		// store the Sub-Stream IDs in the parameter Getter/Setter
		if (isUrlSubStreamVideo) {
			srvParameterGetterSetter.setVideoSubStreamId(resourceUrl.idSubStream);
		} else if (isUrlSubStreamAudio) {
			srvParameterGetterSetter.setAudioSubStreamId(resourceUrl.idSubStream);
		}

		// ----------------------------------------------------

		RtspProtoDataCntGetSetParamNames getParameterNames = new RtspProtoDataCntGetSetParamNames();

		if (isUrlStream) {
			getParameterNames.putParamName("jitter");
			getParameterNames.putParamName("latency");
		} else if (isUrlSubStreamVideo) {
			getParameterNames.putParamName("speed");
		} else if (isUrlSubStreamAudio) {
			getParameterNames.putParamName("volume");
		} else {
			throw new IllegalArgumentException("isUrlXxx");
		}

		// ----------------------------------------------------

		RtspProtoMessageType mt = cliRequOutputSvc.get(ct).sendRequest_getParameter(resourceUrl, getParameterNames);
		assertEquals(RtspProtoMessageType.GET_PARAMETER, mt);

		// ----------------------------------------------------

		RtspRequestBasics resRequBas = srvRequInputSvc.receiveRequestFromClient(srvSessionInfo.getClientIpAddr());
		assertEquals(RtspProtoStatusCode.OK, resRequBas.statusCode);

		// ----------------------------------------------------

		srvRespOutputSvc.sendResponse(resRequBas);

		// ----------------------------------------------------

		RtspResponseBasics resRespBas = cliRespInputSvc.get(ct).receiveResponse();
		assertEquals(RtspProtoStatusCode.OK, resRespBas.statusCode);

		// ----------------------------------------------------

		assertTrue(cliSessionInfoPtr.getRhGetParamValues().isPresent());

		Set<Map.Entry<String, String>> expKvs = new HashSet<>();
		if (isUrlStream) {
			expKvs.add(Map.entry("jitter", "1234567.89"));
			expKvs.add(Map.entry("latency", "864.2"));
		} else if (isUrlSubStreamVideo) {
			expKvs.add(Map.entry("speed", "1.5"));
		} else {
			expKvs.add(Map.entry("volume", "99.9"));
		}
		assertEquals(expKvs, cliSessionInfoPtr.getRhGetParamValues().orElseThrow().getParamKvsEntrySet());
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

		srvParameterGetterSetter = new ParameterGetterSetterServerSide(srvSessionInfo);

		initObjsServer_fromClient();
		initObjsServer_toClient();
	}

	private void initObjsServer_fromClient() {
		Objects.requireNonNull(srvSessionInfo);
		Objects.requireNonNull(srvRtxpTcpReadWrite);

		UserAuthServerSide srvUserAuthSvc = new UserAuthServerSide();

		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.ANNOUNCE);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.DESCRIBE);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.GET_PARAMETER);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.OPTIONS);
		srvCfgSupportedMessageTypes.putMt(RtspProtoMessageType.SET_PARAMETER);
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
				srvParameterGetterSetter,
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
				srvParameterGetterSetter,
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
