package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.SrtxpKmd;

import java.net.DatagramSocket;
import java.net.InetAddress;
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

		/** Client's incoming port for RTP packets (audio and video), provided by the RTSP Client */
		public int tpClientDestPortRtp = 0;
		/** Client's outgoing port for RTCP packets (meta information), provided by the RTSP Client */
		public int tpClientDestPortRtcp = 0;
		/** Server's outgoing UDP socket for RTP packets */
		public @Nullable DatagramSocket tpServerSrcSocketRtp = null;
		/** Server's outgoing/incoming UDP socket for RTCP packets */
		public @Nullable DatagramSocket tpServerSocketRtcp = null;
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

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public boolean isTransportValid(boolean needsEncryption) {
			return (tpClientDestPortRtp > 0 && tpClientDestPortRtcp > 0 &&
					tpIsUdp && tpIsUnicast && ! tpIsInterleaved &&
					tpIsEncr == needsEncryption);
		}

		public StreamInfo() {
			rtspSsrcId = RandomHelper.getRandomUint32();
		}
	}

	public static class AuthInfo {
		/** Authentication credentials: username */
		public @NonNull String authUser = "";
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

	/** Client IP address */
	public @Nullable InetAddress clientIpAddr = null;
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

	/** Is RTP/RTCP encryption enabled? */
	public boolean isRtxpEncryptionEnabled = false;  // @TODO

	/** Authentication-related info */
	public @NonNull AuthInfo authInfo = new AuthInfo();

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** Streams info - one per SETUP request (the map keys are unique Stream Source identifiers) */
	public @NonNull Map<@NonNull Integer, @NonNull StreamInfo> streamsMapSetup = new ConcurrentHashMap<>();
	/** URL of the Input Source as requested from the client per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public @NonNull Map<@NonNull ServerMessageType, @NonNull String> inputSourceUrlPerSmtMap = new ConcurrentHashMap<>();
	/** Input Source objects per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public @NonNull Map<@NonNull ServerMessageType, @NonNull RtspInputSource> inputSourceObjPerSmtMap = new ConcurrentHashMap<>();

	/** Current state of the RTSP session */
	public @NonNull SessionState sessionState = SessionState.INIT;

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
