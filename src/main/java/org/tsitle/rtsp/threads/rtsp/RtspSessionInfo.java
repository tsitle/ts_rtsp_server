package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RtspSessionInfo {

	public static class AuthInfo {
		/** Authentication credentials: username (from URL or WWW-Authenticate header) */
		public @NonNull String authUser = "";
		/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
		public @NonNull String authPlainPassword = "";
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

	/** Client IP address */
	public @Nullable InetAddress clientIpAddr = null;
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
	/** Last received Sequence Number of RTSP messages within the session from the client for requests */
	public int rtspClientSeqNrLastRcvd = -1;
	/** Expected Sequence Number of RTSP messages within the session to receive from the client for requests */
	public int rtspClientSeqNrExpected = 0;
	/** Sequence Number of RTSP messages within the session in responses from the server */
	public int rtspClientSeqNrResponse = 0;
	/** Expected Sequence Number of RTSP messages within the session to receive from the client for responses */
	public int rtspServerSeqNrExpected = 0;
	/** Sequence Number of RTSP messages within the session in requests from the server */
	public int rtspServerSeqNrRequest = 0;

	/** Playback range request value from client */
	public @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version used by the client in the last request (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion lastRequestRtspProtoVersion = RtspProtocolVersion.NONE;

	/** Authentication-related info */
	public @NonNull AuthInfo authInfo = new AuthInfo();

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** Sub-Stream IDs that a successful SETUP request has been received for */
	public final @NonNull List<@NonNull String> subStreamIdsSetup = new ArrayList<>();
	/** URL of the Input Source as requested from the client per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	public final @NonNull Map<@NonNull RtspProtoMessageType, @NonNull String> inputSourceUrlPerMtMap = new ConcurrentHashMap<>();
	/** Input Source objects per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	public final @NonNull Map<@NonNull RtspProtoMessageType, @NonNull RtspInputSource> inputSourceObjPerMtMap = new ConcurrentHashMap<>();

	/** Current state of the RTSP session */
	public @NonNull SessionState sessionState = SessionState.INIT;
	/** Has the client requested PAUSE? */
	public boolean isPlaybackPaused = false;

	/** Track 'Thread-Is-Ready-For-Playback' states per stream source */
	public final @NonNull Map<@NonNull Integer, @NonNull Boolean> threadReadyStates = new ConcurrentHashMap<>();

}
