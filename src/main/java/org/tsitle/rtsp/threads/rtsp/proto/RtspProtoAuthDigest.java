package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;

/**
 * Utility class for handling RTSP Digest Authentication.
 */
public final class RtspProtoAuthDigest {

	private RtspProtoAuthDigest() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Compute the authentication response based on provided parameters.
	 * @param authUserName Username
	 * @param authUserPwPlain Plain password
	 * @param authUri URI that the request is targeting
	 * @param messageType Type of the RTSP message
	 * @param authRealmServer Server's authentication realm
	 * @param authNonce Nonce value provided by the server
	 * @return Authentication response as MD5 hash
	 * @throws IllegalArgumentException If any of the provided parameters are invalid
	 */
	public static @NonNull String computeAuthResponse(
				@NonNull String authUserName,
				@NonNull String authUserPwPlain,
				@NonNull String authUri,
				@NonNull RtspProtoMessageType messageType,
				@NonNull String authRealmServer,
				@NonNull String authNonce
			) throws IllegalArgumentException {
		if (authUserName.isBlank()) {
			throw new IllegalArgumentException("Auth User Name cannot be blank");
		}
		if (authUserPwPlain.isBlank()) {
			throw new IllegalArgumentException("Auth User Password Plain cannot be blank");
		}
		if (authUri.isBlank()) {
			throw new IllegalArgumentException("Auth URI cannot be blank");
		}
		if (messageType == RtspProtoMessageType.UNKNOWN) {
			throw new IllegalArgumentException("MethodType cannot be UNKNOWN");
		}
		if (authRealmServer.isBlank()) {
			throw new IllegalArgumentException("Auth Server Realm cannot be blank");
		}
		if (authNonce.isBlank()) {
			throw new IllegalArgumentException("Auth Nonce cannot be blank");
		}

		String tmpHa1 = HashMd5Helper.hashOfString(
				authUserName + ":" + authRealmServer + ":" + authUserPwPlain,
				false
			);
		String tmpHa2 = HashMd5Helper.hashOfString(
				messageType.name() + ":" + authUri,
				false
			);
		return HashMd5Helper.hashOfString(
				tmpHa1 + ":" + authNonce + ":" + tmpHa2,
				false
			);
	}

}
