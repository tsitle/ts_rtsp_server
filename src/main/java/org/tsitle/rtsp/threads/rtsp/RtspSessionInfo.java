package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
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
	public long seqNr_requFromRem_lastRcvd = -1L;
	/** Request from remote host: Expected RTSP message Sequence Number */
	public long seqNr_requFromRem_expected = 0L;
	/** Request to remote host: Last sent RTSP message Sequence Number */
	public long seqNr_requToRem_lastSent = 0L;

	/** Playback range request value from the client */
	public @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version to be used */
	public @NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** RTSP message types that are supported by the remote host */
	public final @NonNull RtspProtoDataCntMessageTypes rhSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

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

	public @NonNull String findRtspIpFromResourceUrl(
				@NonNull RtspMessageType messageType,
				@Nullable String subStreamIdForSetup
			) throws RtspCannotFindIpFromRscUrlException {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspHostIp()";

		if (messageType == RtspMessageType.SETUP && (subStreamIdForSetup == null || subStreamIdForSetup.isBlank())) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Sub-Stream ID is blank for SETUP message type");
		}

		String tmpRtspHostname;
		try {
			Optional<String> tmpOptRscUrl = (messageType == RtspMessageType.SETUP ?
					getResourceUrlForMt_onlySetup(subStreamIdForSetup)
					: getResourceUrlForMt_nonSetup(messageType));
			if (tmpOptRscUrl.isEmpty()) {
				throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": No Resource URL found for message type: " + messageType);
			}
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(tmpOptRscUrl.get());
			tmpRtspHostname = rscUriObj.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not parse URL: " + e.getMessage());
		}
		if (tmpRtspHostname.isBlank()) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(
					tmpRtspHostname,
					true
				);
			if (optRtspHostIp.isEmpty()) {
				throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" +
						tmpRtspHostname + "'");
			}
			return optRtspHostIp.get().getHostAddress();
		} catch (UnknownHostException | SocketException e) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
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
