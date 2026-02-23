package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspStreamSource;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RtspSessionInfo {

	/**
	 * One StreamInfo object per SETUP request.
	 */
	public static class StreamInfo {
		/** Stream Source object */
		RtspStreamSource rtspStreamSource = null;

		/** URL of the Stream Source as requested from the client per SETUP */
		public String inputSourceUrlSetup = "";

		/** RTSP Synchronization Source Identifier (random number. one per session/client and per stream) */
		public int rtspSsrcId = 0;
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
		public DatagramSocket tpServerSrcSocketRtp = null;
		/** Server's outgoing/incoming UDP socket for RTCP packets */
		public DatagramSocket tpServerSocketRtcp = null;
		/** Requested transport type protocol */
		public boolean tpIsUdp = false;
		/** Requested transport casting type */
		public boolean tpIsUnicast = false;
		/** Requested transport interleaved mode */
		public boolean tpIsInterleaved = true;

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public boolean isTransportValid() {
			return (tpClientDestPortRtp > 0 && tpClientDestPortRtcp > 0 &&
					tpIsUdp && tpIsUnicast && ! tpIsInterleaved);
		}
	}

	/** Client IP address */
	public InetAddress clientIpAddr = null;
	/** RTSP Session ID */
	public String rtspSessionId = "";
	/** Sequence Number of RTSP messages within the session */
	public int rtspSeqNr = 0;

	/** Playback range request value from client */
	public String clientPlaybackRangeValue = "";

	/** RTSP protocol version used by the client in the last request (e.g. 'RTSP/1.0') */
	public String lastRequestRtspProtoVersion = "-";

	/** Streams info - one per SETUP request (the map keys are unique Stream Source identifiers) */
	public Map<Integer, StreamInfo> streamsMapSetup = new ConcurrentHashMap<>();
	/** URL of the Input Source as requested from the client per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public Map<ServerMessageType, String> inputSourceUrlPerSmtMap = new ConcurrentHashMap<>();
	/** Input Source objects per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN request */
	public Map<ServerMessageType, RtspInputSource> inputSourceObjPerSmtMap = new ConcurrentHashMap<>();

	/** Current state of the RTSP session */
	public SessionState sessionState = SessionState.INIT;

	/** Track 'Thread-Is-Ready-For-Playback' states per stream source */
	public Map<Integer, Boolean> threadReadyStates = new ConcurrentHashMap<>();

	public StreamInfo getStreamInfoOrThrow(String fncName, int streamSourceId) {
		RtspSessionInfo.StreamInfo tmpStreamInfo = streamsMapSetup.getOrDefault(streamSourceId, null);
		if (tmpStreamInfo == null) {
			throw new IllegalStateException(fncName + ": Stream info not found for stream source ID: " + streamSourceId);
		}
		return tmpStreamInfo;
	}

	public RtspInputSource getInputSourceForSmtOrThrow(String fncName, ServerMessageType smt) {
		RtspInputSource tmpIs = inputSourceObjPerSmtMap.getOrDefault(smt, null);
		if (tmpIs == null) {
			throw new IllegalStateException(fncName + ": Input Source not found for SMT: " + smt);
		}
		return tmpIs;
	}

}
