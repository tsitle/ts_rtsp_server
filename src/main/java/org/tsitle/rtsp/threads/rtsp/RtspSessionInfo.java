package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RtspSessionInfo {

	public static class AuthInfo {
		/** Authentication credentials: username (from URL or WWW-Authenticate header) */
		public @NonNull String authUser = "";
		/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
		public @NonNull String authPlainPassword = "";
		/** Authentication credentials: realm from the server */
		public @NonNull String authRealmServer = "";
		/** Authentication credentials: realm from the client */
		public @NonNull String authRealmClient = "";
		/** Authentication credentials: nonce from the server */
		public @NonNull String authNonceServer = "";
		/** Authentication credentials: nonce from the client */
		public @NonNull String authNonceClient = "";
		/** Authentication credentials: URI */
		public @NonNull String authUri = "";
		/** Authentication credentials: response */
		public @NonNull String authResp = "";

		public void resetPerRequest() {
			authUser = "";
			authRealmClient = "";
			authNonceClient = "";
			authUri = "";
			authResp = "";
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static class DataSdp {
		/** Content language(s) (e.g. 'de') */
		public @NonNull String contentLang = "";

		/** Content base (e.g. 'rtsp://some.com/camera.stream/') */
		public @NonNull String contentBase = "";

		/** Session Description Protocol (SDP) data */
		public final @NonNull List<@NonNull String> sdpLinesAllRaw = new ArrayList<>();

		public void resetPerRequest() {
			contentLang = "";
			contentBase = "";
			sdpLinesAllRaw.clear();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static class DataGetSetParamKvs {
		/** Content language(s) (e.g. 'de') */
		public @NonNull String contentLang = "";

		/** Parameter key-value-pairs */
		public final @NonNull Map<@NonNull String, @NonNull String> paramKvs = new HashMap<>();

		public void resetPerRequest() {
			contentLang = "";
			paramKvs.clear();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static class DataGetSetParamNames {
		/** Parameter names */
		public final @NonNull Set<@NonNull String> paramNames = new HashSet<>();

		public void resetPerRequest() {
			paramNames.clear();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Authentication-related info */
	public @NonNull AuthInfo authInfo = new AuthInfo();

	/** RTSP message parameters that have been requested by the remote host in the last request */
	public final @NonNull DataGetSetParamNames requRequestedGetParamNames = new DataGetSetParamNames();
	/** RTSP message parameters that have been sent by the remote host in the last request */
	public final @NonNull DataGetSetParamKvs requReceivedSetParamValues = new DataGetSetParamKvs();
	/** RTSP message parameters that have been sent by the remote host in the last request but are invalid */
	public final @NonNull DataGetSetParamNames requReceivedInvalidParamNames = new DataGetSetParamNames();
	/** SDP that has been announced by the remote host in the last request */
	public final @NonNull DataSdp requAnnouncedSdp = new DataSdp();

	/** RTSP message parameters that the remote host has sent in the last response */
	public final @NonNull DataGetSetParamKvs respReceivedGetParamValues = new DataGetSetParamKvs();
	/** RTSP message parameters that the remote host does not support as received in the last response */
	public final @NonNull DataGetSetParamNames respReceivedInvalidParamNames = new DataGetSetParamNames();
	/** SDP that has been announced by the remote host in the last response */
	public final @NonNull DataSdp respDescribeSdp = new DataSdp();

	// ----------------------------------------------------------------

	/** Server IP address */
	private @Nullable InetAddress serverIpAddr = null;
	/** Client IP address */
	private @Nullable InetAddress clientIpAddr = null;

	/** Has the client requested UDP transport? */
	public boolean isTransportUdp = false;
	/** Has the client requested RTP/RTCP encryption transport? */
	public boolean isTransportSrtpSrtcp = false;

	/** Are we using an RTSPS connection (with SSL/TLS)? */
	public boolean isRtspsConnection = false;
	/** Is RTP/RTCP encryption required? */
	public boolean isRtpRtcpEncryptionRequired = false;
	/** Force RTP/RTCP encryption (aka SRTP/SRTCP)? */
	public boolean forceRtpRtcpEncryption = false;

	/** RTSP Session ID */
	public @NonNull String rtspSessionId = "";

	/** Request from remote host: Last received RTSP message Sequence Number */
	public long seqNr_requRem_lastRcvd = -1L;
	/** Request from remote host: Expected RTSP message Sequence Number */
	public long seqNr_requRem_expected = 0L;
	/** Response from remote host: Expected RTSP message Sequence Number */
	public long seqNr_respRem_expected = 0L;

	/** Playback range request value from the client */
	public @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version to be used */
	public @NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** RTSP message types that are supported by the remote host */
	public final @NonNull Set<@NonNull RtspMessageType> rhSupportedMessageTypes = new HashSet<>();

	// ----------------------------------------------------------------

	/** Sub-Stream IDs that a successful SETUP request has been received for */
	public final @NonNull Set<@NonNull String> subStreamIdsSetup = new HashSet<>();

	/** Resource URL per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspMessageType, @NonNull String> resourceUrlPerMtMap_nonSetup = new ConcurrentHashMap<>();
	/** Resource URL per Sub-Stream for SETUP request */
	private final @NonNull Map<@NonNull String, @NonNull String> resourceUrlPerMtMap_onlySetup = new ConcurrentHashMap<>();

	/** Input Source objects per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspMessageType, @NonNull RtspInputSource> inputSourceObjPerMtMap_nonSetup = new ConcurrentHashMap<>();

	// ----------------------------------------------------------------

	/** Current state of the RTSP session */
	public @NonNull RtspSessionState sessionState = RtspSessionState.INIT;
	/** Has the client requested PAUSE? */
	public boolean isPlaybackPaused = false;

	// ----------------------------------------------------------------

	/** Track 'Thread-Is-Ready-For-Playback' states per stream source */
	public final @NonNull Map<@NonNull Integer, @NonNull Boolean> threadReadyStates = new ConcurrentHashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void resetPerRequest() {
		authInfo.resetPerRequest();
		requRequestedGetParamNames.resetPerRequest();
		requReceivedSetParamValues.resetPerRequest();
		requReceivedInvalidParamNames.resetPerRequest();
		requAnnouncedSdp.resetPerRequest();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<InetAddress> getServerIpAddr() {
		return Optional.ofNullable(serverIpAddr);
	}

	@SuppressWarnings("unused")
	public void setServerIpAddr(@NonNull InetAddress ipAddr) {
		this.serverIpAddr = ipAddr;
	}

	public Optional<InetAddress> getClientIpAddr() {
		return Optional.ofNullable(clientIpAddr);
	}

	public void setClientIpAddr(@NonNull InetAddress ipAddr) {
		this.clientIpAddr = ipAddr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void setResourceUrlForMt_nonSetup(@NonNull RtspMessageType mt, @NonNull String url) {
		if (mt == RtspMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot set Resource URL for UNKNOWN message type");
		}
		if (mt == RtspMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot set Resource URL for SETUP message type");
		}
		this.resourceUrlPerMtMap_nonSetup.put(mt, url);
	}

	public Optional<String> getResourceUrlForMt_nonSetup(@NonNull RtspMessageType mt) {
		if (mt == RtspMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot get Resource URL for UNKNOWN message type");
		}
		if (mt == RtspMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot get Resource URL for SETUP message type");
		}
		return Optional.ofNullable(this.resourceUrlPerMtMap_nonSetup.get(mt));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean existsResourceUrlForMt_nonSetup(@NonNull RtspMessageType mt) {
		return this.resourceUrlPerMtMap_nonSetup.containsKey(mt);
	}

	public void setResourceUrlForMt_onlySetup(@NonNull String subStreamId, @NonNull String url) {
		this.resourceUrlPerMtMap_onlySetup.put(subStreamId, url);
	}

	public Optional<String> getResourceUrlForMt_onlySetup(@NonNull String subStreamId) {
		return Optional.ofNullable(this.resourceUrlPerMtMap_onlySetup.get(subStreamId));
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void setInputSourceObjForMt_nonSetup(@NonNull RtspMessageType mt, @NonNull RtspInputSource inputSourceObj) {
		if (mt == RtspMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot set Input Source for UNKNOWN message type");
		}
		if (mt == RtspMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot set Input Source for SETUP message type");
		}
		this.inputSourceObjPerMtMap_nonSetup.put(mt, inputSourceObj);
	}

	public Optional<RtspInputSource> getInputSourceObjForMt_nonSetup(@NonNull RtspMessageType mt) {
		if (mt == RtspMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot get Input Source for UNKNOWN message type");
		}
		if (mt == RtspMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot get Input Source for SETUP message type");
		}
		return Optional.ofNullable(this.inputSourceObjPerMtMap_nonSetup.get(mt));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean existsInputSourceObjForMt_nonSetup(@NonNull RtspMessageType mt) {
		return this.inputSourceObjPerMtMap_nonSetup.containsKey(mt);
	}

}
