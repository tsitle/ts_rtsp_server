package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.helpers.HashMd5Helper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.SrtxpKmd;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Static RTSP session information storage.
 */
public class RtspStaticSessionInfo {

	public static class StreamKmds {
		public final int rtspSsrcId;
		public @Nullable SrtxpKmd kmdInbound = new SrtxpKmd();
		public @Nullable SrtxpKmd kmdOutbound = null;

		public StreamKmds(int rtspSsrcId) {
			this.rtspSsrcId = rtspSsrcId;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
		/** Requested transport type protocol (true: UDP, false: TCP) */
		public boolean tpIsUdp = false;
		/** Requested transport casting type (true: unicast, false: multicast) */
		public boolean tpIsUnicast = false;
		/** Requested transport interleaved mode (true: interleaved (requires TCP), false: separate (requires UDP)) */
		public boolean tpIsInterleaved = true;
		/** Requested transport encryption type (true: SRTP/SRTCP, false: plain RTP/RTCP) */
		public boolean tpIsEncr = false;

		public RtspStaticSessionInfo.@NonNull StreamKmds streamKmds;

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
				if (isRtspsConnection && ! tpIsEncr) {
					throw new Exception("UDP cannot be used with RTSPS w/o SRTP");
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

		public StreamInfo(RtspStaticSessionInfo.@NonNull StreamKmds streamKmds) {
			this.streamKmds = streamKmds;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public record SubStreamInfo(@NonNull String clientIpAddrStr, @NonNull String inputSourceId, int streamSourceId) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int MAX_SUB_STREAM_INFOS = 10_000;
	private static final int MAX_AUTH_SERVER_NONCES = 10_000;
	private static final int MAX_STREAM_INFOS_AND_KMDS = MAX_AUTH_SERVER_NONCES * 2;
	private static final int MAX_UNAUTHORIZED_ENTRIES = 1_000_000;
	private static final int HASH_LEN = 8;

	private static final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private static final Lock theReadLock = theLock.readLock();
	private static final Lock theWriteLock = theLock.writeLock();

	private static final List<@NonNull String> subStreamInfoIds = new ArrayList<>();
	private static final Map<@NonNull String, @NonNull SubStreamInfo> subStreamInfoMap = new HashMap<>();

	private static final List<@NonNull String> authServerNonceList = new ArrayList<>();

	private static final List<@NonNull String> streamKmdsIds = new ArrayList<>();
	private static final Map<@NonNull String, @NonNull StreamKmds> streamKmdsMap = new HashMap<>();

	private static final List<@NonNull String> streamsSetupIds = new ArrayList<>();
	private static final @NonNull Map<@NonNull String, @NonNull StreamInfo> streamsSetupMap = new HashMap<>();

	private static final List<@NonNull String> unauthorizedIds = new ArrayList<>();
	private static final @NonNull Map<@NonNull String, @NonNull Integer> unauthorizedMap = new HashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Adds a new Sub-Stream ID.<br />
	 * A Sub-Stream ID is unique per DESCRIBE request per Stream Source. Even if the same client makes multiple DESCRIBE
	 * requests for the same Input and Stream Source, the Sub-Stream ID will change every time.
	 * @param clientIpAddr Client's IP address
	 * @param inputSourceId Input Source ID as requested from the client per DESCRIBE request
	 * @param streamSourceId Stream Source ID as requested from the client per DESCRIBE request
	 * @return Unique Sub-Stream ID
	 */
	public static @NonNull String addSubStream(
				@NonNull InetAddress clientIpAddr,
				@NonNull String inputSourceId,
				int streamSourceId
			) {
		final String ipStr = getIpStr(clientIpAddr);
		final String ipHash = getIpHash(clientIpAddr);

		theWriteLock.lock();
		try {
			String subStreamId;
			do {
				subStreamId = ipHash + "_" + HashMd5Helper.hashOfString(
						String.format("%s : %05d : %08X", inputSourceId, streamSourceId, RandomHelper.getRandomUint32(false)),
						false
					).substring(0, HASH_LEN);
			} while (subStreamInfoIds.contains(subStreamId));
			subStreamInfoIds.add(subStreamId);
			subStreamInfoMap.put(subStreamId, new SubStreamInfo(ipStr, inputSourceId, streamSourceId));
			//
			if (subStreamInfoMap.size() > MAX_SUB_STREAM_INFOS) {
				// we simply remove the first one. Maybe it is still in use, but we don't care
				subStreamInfoMap.remove(subStreamInfoIds.getFirst());
				subStreamInfoIds.removeFirst();
			}
			return subStreamId;
		} finally {
			theWriteLock.unlock();
		}
	}

	public static Optional<SubStreamInfo> getSubStreamInfo(@NonNull InetAddress clientIpAddr, @NonNull String subStreamId) {
		theReadLock.lock();
		try {
			Optional<SubStreamInfo> optRes = Optional.ofNullable(subStreamInfoMap.get(subStreamId));
			if (optRes.isEmpty()) {
				return Optional.empty();
			}
			final String ipStr = getIpStr(clientIpAddr);
			if (! optRes.get().clientIpAddrStr.equals(ipStr)) {
				return Optional.empty();
			}
			return optRes;
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String addAuthServerNonce(@NonNull InetAddress clientIpAddr) {
		final String ipHash = getIpHash(clientIpAddr);
		final String nonce = HashMd5Helper.hashOfString(
				String.format("%s : %08X", UUID.randomUUID(), RandomHelper.getRandomUint32(false)),
				false
			);

		theWriteLock.lock();
		try {
			authServerNonceList.add(ipHash + "_" + nonce);
			//
			if (authServerNonceList.size() > MAX_AUTH_SERVER_NONCES) {
				// we simply remove the first one. Maybe it is still in use, but we don't care
				authServerNonceList.removeFirst();
			}
			return nonce;
		} finally {
			theWriteLock.unlock();
		}
	}

	public static boolean existsAuthServerNonce(@NonNull InetAddress clientIpAddr, @NonNull String nonce) {
		final String ipHash = getIpHash(clientIpAddr);

		theReadLock.lock();
		try {
			return authServerNonceList.contains(ipHash + "_" + nonce);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull StreamKmds getOrAddStreamKmds(
				@NonNull InetAddress clientIpAddr,
				@NonNull String subStreamId,
				int rtspSsrcId
			) {
		if (rtspSsrcId == 0) {
			throw new IllegalArgumentException("rtspSsrcId must not be 0");
		}
		Optional<StreamKmds> optStreamKmds = getStreamKmds(clientIpAddr, subStreamId);
		if (optStreamKmds.isPresent()) {
			return optStreamKmds.get();
		}
		//
		final String ipHash = getIpHash(clientIpAddr);
		final String skId = String.format("%s : %s", ipHash, subStreamId);

		theWriteLock.lock();
		try {
			streamKmdsIds.add(skId);
			streamKmdsMap.put(skId, new StreamKmds(rtspSsrcId));
			//
			if (streamKmdsMap.size() > MAX_STREAM_INFOS_AND_KMDS) {
				// we simply remove the first one. Maybe it is still in use, but we don't care
				streamKmdsMap.remove(streamKmdsIds.getFirst());
				streamKmdsIds.removeFirst();
			}
			return streamKmdsMap.get(skId);
		} finally {
			theWriteLock.unlock();
		}
	}

	public static Optional<StreamKmds> getStreamKmds(
				@NonNull InetAddress clientIpAddr,
				@NonNull String subStreamId
			) {
		final String ipHash = getIpHash(clientIpAddr);
		final String skId = String.format("%s : %s", ipHash, subStreamId);

		theReadLock.lock();
		try {
			if (! streamKmdsMap.containsKey(skId)) {
				return Optional.empty();
			}
			return Optional.of(streamKmdsMap.get(skId));
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("UnusedReturnValue")
	public static @NonNull StreamInfo addStreamInfo(
				@NonNull String subStreamId,
				RtspStaticSessionInfo.@NonNull StreamKmds streamKmds,
				@NonNull RtspStreamSource rtspStreamSource,
				@NonNull String resourceUrl
			) {
		if (streamKmds.rtspSsrcId == 0) {
			throw new IllegalArgumentException("streamKmds.rtspSsrcId must not be 0");
		}
		theWriteLock.lock();
		try {
			StreamInfo resObj = new StreamInfo(streamKmds);
			resObj.rtspSsrcId = streamKmds.rtspSsrcId;
			resObj.rtspStreamSource = rtspStreamSource;
			resObj.inputSourceUrlSetup = resourceUrl;
			resObj.rtspRtpSeqNrT0 = RandomHelper.getRandomUint16();
			resObj.rtspRtpTimestampT0 = RandomHelper.getRandomUint32(true);
			resObj.rtspRtpGenTsT0Ns = System.nanoTime();
			streamsSetupIds.add(subStreamId);
			streamsSetupMap.put(subStreamId, resObj);
			//
			if (streamsSetupMap.size() > MAX_STREAM_INFOS_AND_KMDS) {
				// we simply remove the first one. Maybe it is still in use, but we don't care
				streamsSetupMap.remove(streamsSetupIds.getFirst());
				streamsSetupIds.removeFirst();
			}
			return resObj;
		} finally {
			theWriteLock.unlock();
		}
	}

	public static @NonNull StreamInfo getStreamInfoOrThrow(@NonNull String fncName, @NonNull String subStreamId) {
		theReadLock.lock();
		try {
			if (! streamsSetupMap.containsKey(subStreamId)) {
				throw new IllegalStateException(fncName + ": Stream info not found for Sub-Stream ID: " + subStreamId);
			}
			return streamsSetupMap.get(subStreamId);
		} finally {
			theReadLock.unlock();
		}
	}

	public static boolean existsStreamInfo(@NonNull String subStreamId) {
		theReadLock.lock();
		try {
			return streamsSetupMap.containsKey(subStreamId);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static int addUnauthorized(@NonNull InetAddress clientIpAddr, @NonNull String inputSourceId) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(inputSourceId, false)
				.substring(0, HASH_LEN);

		theWriteLock.lock();
		try {
			int resI = 1;
			if (! unauthorizedIds.contains(uaId)) {
				unauthorizedIds.add(uaId);
				unauthorizedMap.put(uaId, resI);
				//
				if (unauthorizedMap.size() > MAX_UNAUTHORIZED_ENTRIES) {
					// we simply remove the first one. Maybe it is still in use, but we don't care
					unauthorizedMap.remove(unauthorizedIds.getFirst());
					unauthorizedIds.removeFirst();
				}
			} else {
				resI = unauthorizedMap.get(uaId) + 1;
				unauthorizedMap.replace(uaId, resI);
			}
			return resI;
		} finally {
			theWriteLock.unlock();
		}
	}

	public static void resetUnauthorized(@NonNull InetAddress clientIpAddr, @NonNull String inputSourceId) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(inputSourceId, false)
				.substring(0, HASH_LEN);

		theWriteLock.lock();
		try {
			if (unauthorizedIds.contains(uaId)) {
				unauthorizedMap.remove(uaId);
				unauthorizedIds.remove(uaId);
			}
		} finally {
			theWriteLock.unlock();
		}
	}

	public static int getUnauthorized(@NonNull InetAddress clientIpAddr, @NonNull String inputSourceId) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(inputSourceId, false)
				.substring(0, HASH_LEN);

		theReadLock.lock();
		try {
			if (unauthorizedIds.contains(uaId)) {
				return unauthorizedMap.get(uaId);
			}
			return 0;
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getIpStr(@NonNull InetAddress clientIpAddr) {
		return clientIpAddr.getHostAddress();
	}

	private static @NonNull String getIpHash(@NonNull InetAddress clientIpAddr) {
		return HashMd5Helper.hashOfString(getIpStr(clientIpAddr), false).substring(0, HASH_LEN);
	}

}
