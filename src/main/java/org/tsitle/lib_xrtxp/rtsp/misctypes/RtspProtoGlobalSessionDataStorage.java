package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdSubStreamNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Storage for global RTSP Session Information.
 */
public final class RtspProtoGlobalSessionDataStorage {

	private record SubStreamResolve(
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

	private final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private final Lock theReadLock = theLock.readLock();
	private final Lock theWriteLock = theLock.writeLock();

	private final List<@NonNull RtspProtoIdSubStream> subStreamIds = new ArrayList<>();
	private final Map<@NonNull RtspProtoIdSubStream, @NonNull SubStreamResolve> subStreamResolveMap = new HashMap<>();

	private final List<@NonNull String> authServerNonceList = new ArrayList<>();

	private final List<@NonNull String> unauthorizedIds = new ArrayList<>();
	private final @NonNull Map<@NonNull String, @NonNull Integer> unauthorizedMap = new HashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoGlobalSessionDataStorage() { }

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
	public @NonNull RtspProtoIdSubStream createSubStreamId(
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
			RtspProtoIdSubStream tmpIdSub = RtspProtoIdSubStream.ofEmpty();
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
			RtspProtoIdInputSource tmpIdIs = RtspProtoIdInputSource.ofEmpty();
			tmpIdIs.copyFrom(idInputSource);
			tmpIdIs.writeProtect();
			RtspProtoIdStreamSource tmpIdSs = RtspProtoIdStreamSource.ofEmpty();
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
	 * @throws RtspProtoIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	public @NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException {
		return getResolveBySubStreamId(idSubStream, clientIpAddr).idInputSource;
	}

	/**
	 * Get Stream Source ID by Sub-Stream ID.
	 * @param idSubStream Sub-Stream ID
	 * @param clientIpAddr Client's IP Address (must match the one used to create the Sub-Stream ID)
	 * @return Stream Source ID
	 * @throws RtspProtoIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	public @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException {
		return getResolveBySubStreamId(idSubStream, clientIpAddr).idStreamSource;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new Auth Server Nonce.
	 * @param clientIpAddr Client's IP address
	 * @return Nonce
	 */
	public @NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr) {
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

	/**
	 * Check if the given Auth Server Nonce exists for the given client IP address.
	 * @param clientIpAddr Client's IP address
	 * @param nonce Nonce
	 * @return True if the nonce exists, false otherwise
	 */
	public boolean existsAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull String nonce) {
		final String ipHash = getIpHash(clientIpAddr);

		theReadLock.lock();
		try {
			return authServerNonceList.contains(ipHash + "_" + nonce);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Increment the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 * @return The new number of unauthorized attempts
	 */
	public int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
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

	/**
	 * Reset the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 */
	public void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
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

	/**
	 * Get the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 * @return The number of unauthorized attempts
	 */
	public int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
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

	private @NonNull SubStreamResolve getResolveBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException {
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("Client IP address must be set");
		}
		final String ipStr = clientIpAddr.getIpAddrStr().orElseThrow();

		theReadLock.lock();
		try {
			if (! subStreamResolveMap.containsKey(idSubStream)) {
				throw new RtspProtoIdSubStreamNotFoundException("Sub-Stream ID '" + idSubStream.getIdStr() + "' not found");
			}
			SubStreamResolve tmpSsr = subStreamResolveMap.get(idSubStream);
			if (! tmpSsr.clientIpAddrStr.equalsIgnoreCase(ipStr)) {
				throw new RtspProtoIdSubStreamNotFoundException("Sub-Stream ID '" + idSubStream.getIdStr() + "' " +
						"belongs to a different IP address");
			}
			return tmpSsr;
		} finally {
			theReadLock.unlock();
		}
	}

}
