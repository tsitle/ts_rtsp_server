package org.tsitle.lib.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoIdSubStreamNotFoundException;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoIpAddr;

/**
 * Interface for global RTSP Session Information.
 */
public interface RtspProtoGlobalSessionInfoInterface {

	/**
	 * Create a new Sub-Stream ID.<br />
	 * The Sub-Stream ID will be globally unique.
	 * @param idInputSource Input Source ID
	 * @param idStreamSource Stream Source ID
	 * @param clientIpAddr Client's IP Address
	 * @return Sub-Stream ID
	 */
	@NonNull RtspProtoIdSubStream createSubStreamId(
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull RtspProtoIpAddr clientIpAddr
			);

	/**
	 * Get Input Source ID by Sub-Stream ID.
	 * @param idSubStream Sub-Stream ID
	 * @param clientIpAddr Client's IP Address (must match the one used to create the Sub-Stream ID)
	 * @return Input Source ID
	 * @throws RtspProtoIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	@NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException;

	/**
	 * Get Stream Source ID by Sub-Stream ID.
	 * @param idSubStream Sub-Stream ID
	 * @param clientIpAddr Client's IP Address (must match the one used to create the Sub-Stream ID)
	 * @return Stream Source ID
	 * @throws RtspProtoIdSubStreamNotFoundException If the Sub-Stream ID is not found
	 */
	@NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException;

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Add a new Auth Server Nonce.
	 * @param clientIpAddr Client's IP address
	 * @return Nonce
	 */
	@NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr);

	/**
	 * Check if the given Auth Server Nonce exists for the given client IP address.
	 * @param clientIpAddr Client's IP address
	 * @param nonce Nonce
	 * @return True if the nonce exists, false otherwise
	 */
	boolean existsAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull String nonce);

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Increment the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 * @return The new number of unauthorized attempts
	 */
	int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource);

	/**
	 * Reset the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 */
	void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource);

	/**
	 * Get the number of unauthorized attempts for the given client IP address and Input Source ID.
	 * @param clientIpAddr Client's IP address
	 * @param idInputSource Input source ID
	 * @return The number of unauthorized attempts
	 */
	int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource);

}
