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

	public final static class PermDataCntAuthSrv {
		/** Authentication credentials: realm */
		public @NonNull String authRealm = "";
		/** Authentication credentials: nonce */
		public @NonNull String authNonce = "";
	}

	public final static class PermDataCntAuthClient {
		/** Authentication credentials: username */
		public @NonNull String authUser = "";
		/** Authentication credentials: password */
		public @NonNull String authPlainPassword = "";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Authentication-related info from the server */
	public final @NonNull PermDataCntAuthSrv permAuthServer = new PermDataCntAuthSrv();
	/** Authentication-related info for the client */
	public final @NonNull PermDataCntAuthClient permAuthClient = new PermDataCntAuthClient();

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
