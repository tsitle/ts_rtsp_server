package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.SrtxpKmd;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RtspSessionInfo {

	public static class StreamKmds {
		public @Nullable SrtxpKmd kmdOutbound = null;
		public @Nullable SrtxpKmd kmdInbound = null;
	}

	/**
	 * One StreamInfo object per SETUP request.
	 */
	public static class StreamInfo {
		/** Stream Source object */
		@Nullable RtspStreamSource rtspStreamSource = null;

		/** URL of the Stream Source as requested from the client per SETUP */
		public @NonNull String inputSourceUrlSetup = "";

		/** RTSP Synchronization Source Identifier (random number. one per session/client and per stream) */
		public int rtspSsrcId;
		/** Initial RTP Sequence Number within the session (random number, 16 bits unsigned) */
		public short rtspRtpSeqNrT0 = 0;
		/** Initial RTP Timestamp within the session (random number) */
		public int rtspRtpTimestampT0 = 0;
		/** System.nanoTime when the RTP TS T0 was generated (in nanoseconds) */
		public long rtspRtpGenTsT0Ns = 0L;

		/** Client's incoming UDP port for RTP packets (audio and video), provided by the RTSP Client */
		public int tpClientDestUdpPortRtp = 0;
		/** Client's outgoing UDP port for RTCP packets (meta information), provided by the RTSP Client */
		public int tpClientDestUdpPortRtcp = 0;
		/** Server's outgoing UDP socket for RTP packets */
		public @Nullable DatagramSocket tpServerSrcUdpSocketRtp = null;
		/** Server's outgoing/incoming UDP socket for RTCP packets */
		public @Nullable DatagramSocket tpServerUdpSocketRtcp = null;
		/** Client's incoming TCP channel for RTP packets (audio and video), provided by the RTSP Client */
		public int tpClientDestTcpChannRtp = -1;
		/** Client's outgoing TCP channel for RTCP packets (meta information), provided by the RTSP Client */
		public int tpClientDestTcpChannRtcp = -1;
		/** Requested transport type protocol */
		public boolean tpIsUdp = false;
		/** Requested transport casting type */
		public boolean tpIsUnicast = false;
		/** Requested transport interleaved mode */
		public boolean tpIsInterleaved = true;
		/** Requested transport encryption type */
		public boolean tpIsEncr = false;

		/** SRTxP KMDs */
		public @NonNull StreamKmds streamKmds = new StreamKmds();

		public void isTransportValid(
					boolean needsEncryption,
					boolean isRtspsConnection,
					boolean isTransportUdpDisabled
				) throws Exception {
			if (! tpIsEncr && needsEncryption && ! isRtspsConnection) {
				throw new Exception("Client requested unencrypted transport, but encryption is required");
			}
			if (tpIsEncr && ! needsEncryption) {
				throw new Exception("Client requested encrypted transport, but encryption is disabled");
			}
			if (! tpIsUnicast) {
				throw new Exception("Multicast is not supported");
			}
			if (tpIsUdp) {
				if (tpIsInterleaved) {
					throw new Exception("Interleaved mode is not supported for UDP");
				}
				if (tpClientDestUdpPortRtp <= 0 || tpClientDestUdpPortRtcp <= 0) {
					throw new Exception("Client UDP ports not set");
				}
				if (isTransportUdpDisabled) {
					throw new Exception("UDP is disabled");
				}
				return;
			}
			if (! tpIsInterleaved) {
				throw new Exception("Interleaved mode must be used for TCP");
			}
			if (tpClientDestTcpChannRtp < 0 || tpClientDestTcpChannRtcp < 0) {
				throw new Exception("Client TCP channel IDs not set");
			}
			if (tpClientDestTcpChannRtp == tpClientDestTcpChannRtcp) {
				throw new Exception("Client TCP channel IDs for RTP and RTCP cannot be the same");
			}
		}

		public StreamInfo() {
			rtspSsrcId = RandomHelper.getRandomUint32();
		}
	}

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

	public static class DescribeIsSs {
		@NonNull String inputSourceId = "";
		int streamSourceId = -1;
	}

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

	/** RTSP Session ID */
	public @NonNull String rtspSessionId = "";
	/** Last received Sequence Number of RTSP messages within the session from the client */
	public int rtspSeqNrLastRcvd = -1;
	/** Expected Sequence Number of RTSP messages within the session to receive from the client */
	public int rtspSeqNrExpected = 0;
	/** Sequence Number of RTSP messages within the session in responses from the server */
	public int rtspSeqNrResponse = 0;

	/** Playback range request value from client */
	public @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version used by the client in the last request (e.g. 'RTSP/1.0') */
	public @NonNull String lastRequestRtspProtoVersion = "-";

	/** Authentication-related info */
	public @NonNull AuthInfo authInfo = new AuthInfo();

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** Store for Sub-Stream IDs ('Input Stream and Stream Source' combinations) as announced per DESCRIBE (the map keys are hash sums) */
	public @NonNull Map<@NonNull String, @NonNull DescribeIsSs> describeMapSubStreamId = new ConcurrentHashMap<>();
	/** Streams info - one per SETUP request (the map keys are unique Stream Source identifiers) */
	public @NonNull Map<@NonNull Integer, @NonNull StreamInfo> streamsMapSetup = new ConcurrentHashMap<>();
	/** Stream Source identifiers that a successful SETUP request has been received for */
	public @NonNull List<@NonNull Integer> streamSourceIdsSetup = new ArrayList<>();
	/** URL of the Input Source as requested from the client per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public @NonNull Map<@NonNull ServerMessageType, @NonNull String> inputSourceUrlPerSmtMap = new ConcurrentHashMap<>();
	/** Input Source objects per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public @NonNull Map<@NonNull ServerMessageType, @NonNull RtspInputSource> inputSourceObjPerSmtMap = new ConcurrentHashMap<>();

	/** Current state of the RTSP session */
	public @NonNull SessionState sessionState = SessionState.INIT;
	/** Has the client requested PAUSE? */
	public boolean isPlaybackPaused = false;

	/** Track 'Thread-Is-Ready-For-Playback' states per stream source */
	public @NonNull Map<@NonNull Integer, @NonNull Boolean> threadReadyStates = new ConcurrentHashMap<>();

	public @NonNull StreamInfo getStreamInfoOrThrow(@NonNull String fncName, int streamSourceId) {
		if (! streamsMapSetup.containsKey(streamSourceId)) {
			throw new IllegalStateException(fncName + ": Stream info not found for stream source ID: " + streamSourceId);
		}
		return streamsMapSetup.get(streamSourceId);
	}

	public @NonNull RtspInputSource getInputSourceForSmtOrThrow(String fncName, ServerMessageType smt) {
		if (! inputSourceObjPerSmtMap.containsKey(smt)) {
			throw new IllegalStateException(fncName + ": Input Source not found for SMT: " + smt);
		}
		return inputSourceObjPerSmtMap.get(smt);
	}

}
