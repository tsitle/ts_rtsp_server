package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdSubStreamNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Static RTSP session information storage.
 */
public final class RtspStaticSessionInfo {

	public record SubStreamResolve(
			@NonNull String clientIpAddrStr,
			@NonNull RtspProtoIdInputSource idInputSource,
			@NonNull RtspProtoIdStreamSource idStreamSource
		) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int MAX_SUB_STREAM_IDS = 10_000;
	private static final int MAX_AUTH_SERVER_NONCES = 10_000;
	private static final int MAX_UNAUTHORIZED_ENTRIES = 1_000_000;
	private static final int HASH_LEN = 8;

	private static final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private static final Lock theReadLock = theLock.readLock();
	private static final Lock theWriteLock = theLock.writeLock();

	private static final List<@NonNull RtspProtoIdSubStream> subStreamIds = new ArrayList<>();
	private static final Map<@NonNull RtspProtoIdSubStream, @NonNull SubStreamResolve> subStreamResolveMap = new HashMap<>();

	private static final List<@NonNull String> authServerNonceList = new ArrayList<>();

	private static final List<@NonNull String> unauthorizedIds = new ArrayList<>();
	private static final @NonNull Map<@NonNull String, @NonNull Integer> unauthorizedMap = new HashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private RtspStaticSessionInfo() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new Sub-Stream ID.<br />
	 * A Sub-Stream ID is unique per DESCRIBE request per Stream Source. Even if the same client makes multiple DESCRIBE
	 * requests for the same Input and Stream Source, the Sub-Stream ID will change every time.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input Source ID as requested from the client per DESCRIBE request
	 * @param idStreamSource Stream Source ID as requested from the client per DESCRIBE request
	 * @return Unique Sub-Stream ID
	 */
	public static @NonNull RtspProtoIdSubStream createSubStreamId(
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource
			) {
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("Client IP address must be set");
		}
		if (idInputSource.isEmpty()) {
			throw new IllegalArgumentException("Input Source ID must be set");
		}
		if (idStreamSource.isEmpty()) {
			throw new IllegalArgumentException("Stream Source ID must be set");
		}

		//
		final String ipStr = clientIpAddr.getIpAddrStr().orElseThrow();
		final String ipHash = getIpHash(clientIpAddr);

		theWriteLock.lock();
		try {
			RtspProtoIdSubStream tmpIdSub = new RtspProtoIdSubStream();
			do {
				String tmpIdStr = ipHash + "_" + HashMd5Helper.hashOfString(
						String.format("%s : %5s : %08X",
								idInputSource.getIdStr(), idStreamSource.getIdStr(),
								RandomHelper.getRandomUint32(false)),
						false
					).substring(0, HASH_LEN);
				tmpIdSub.setIdStr(tmpIdStr);
			} while (subStreamIds.contains(tmpIdSub));
			tmpIdSub.writeProtect();
			subStreamIds.add(tmpIdSub);
			//
			RtspProtoIdInputSource tmpIdIs = new RtspProtoIdInputSource();
			tmpIdIs.copyFrom(idInputSource);
			tmpIdIs.writeProtect();
			RtspProtoIdStreamSource tmpIdSs = new RtspProtoIdStreamSource();
			tmpIdSs.copyFrom(idStreamSource);
			tmpIdSs.writeProtect();
			subStreamResolveMap.put(tmpIdSub, new SubStreamResolve(ipStr, tmpIdIs, tmpIdSs));
			//
			if (subStreamIds.size() > MAX_SUB_STREAM_IDS) {
				// we simply remove the first one. Maybe it is still in use, but we don't care
				subStreamResolveMap.remove(subStreamIds.getFirst());
				subStreamIds.removeFirst();
			}
			return tmpIdSub;
		} finally {
			theWriteLock.unlock();
		}
	}

	/**
	 * Get Input Source ID by Sub-Stream ID.
	 * @param idSubStream Sub-Stream ID
	 * @param clientIpAddr Client's IP Address (must match the one used to create the Sub-Stream ID)
	 * @return Input Source ID
	 * @throws RtspIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	public static @NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
		return getResolveBySubStreamId(idSubStream, clientIpAddr).idInputSource;
	}

	/**
	 * Get Stream Source ID by Sub-Stream ID.
	 * @param idSubStream Sub-Stream ID
	 * @param clientIpAddr Client's IP Address (must match the one used to create the Sub-Stream ID)
	 * @return Stream Source ID
	 * @throws RtspIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	public static @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
		return getResolveBySubStreamId(idSubStream, clientIpAddr).idStreamSource;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new Auth Server Nonce.
	 * @param clientIpAddr Client's IP address
	 * @return Nonce
	 */
	public static @NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr) {
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

	public static boolean existsAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull String nonce) {
		final String ipHash = getIpHash(clientIpAddr);

		theReadLock.lock();
		try {
			return authServerNonceList.contains(ipHash + "_" + nonce);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(idInputSource.getIdStr(), false)
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

	public static void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(idInputSource.getIdStr(), false)
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

	public static int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		final String ipHash = getIpHash(clientIpAddr);
		final String uaId = ipHash + "_" + HashMd5Helper.hashOfString(idInputSource.getIdStr(), false)
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

	private static @NonNull String getIpHash(@NonNull RtspProtoIpAddr clientIpAddr) {
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("Client IP address must be set");
		}
		final String ipStr = clientIpAddr.getIpAddrStr().orElseThrow();

		return HashMd5Helper.hashOfString(ipStr, false).substring(0, HASH_LEN);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull SubStreamResolve getResolveBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("Client IP address must be set");
		}
		final String ipStr = clientIpAddr.getIpAddrStr().orElseThrow();

		theReadLock.lock();
		try {
			if (! subStreamResolveMap.containsKey(idSubStream)) {
				throw new RtspIdSubStreamNotFoundException("Sub-Stream ID '" + idSubStream.getIdStr() + "' not found");
			}
			SubStreamResolve tmpSsr = subStreamResolveMap.get(idSubStream);
			if (! tmpSsr.clientIpAddrStr.equalsIgnoreCase(ipStr)) {
				throw new RtspIdSubStreamNotFoundException("Sub-Stream ID '" + idSubStream.getIdStr() + "' " +
						"belongs to a different IP address");
			}
			return tmpSsr;
		} finally {
			theReadLock.unlock();
		}
	}

}
