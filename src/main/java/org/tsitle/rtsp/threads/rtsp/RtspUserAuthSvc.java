package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.net.InetAddress;
import java.util.Optional;
import java.util.Set;

/**
 * RTSP User Authentication and Authorization Service
 */
public class RtspUserAuthSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;

	public RtspUserAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean authenticate(@NonNull RtspSessionInfo rtspSessionInfo, @NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".authenticate()";

		if (rtspSessionInfo.authInfo.authUser.isBlank()) {
			// fail silently since missing at least the username is normal for the first unauthorized request
			return false;
		}
		if (rtspSessionInfo.authInfo.authPlainPassword.isBlank() &&
				(rtspSessionInfo.authInfo.authRealmClient.isBlank() ||
					rtspSessionInfo.authInfo.authNonceClient.isBlank() || rtspSessionInfo.authInfo.authResp.isBlank())) {
			// fail silently
			return false;
		}
		if (rtspSessionInfo.authInfo.authPlainPassword.isBlank() &&
				! rtspSessionInfo.authInfo.authRealmClient.equals(RtspProtoHighConstants.DEFAULT_RTSP_AUTH_REALM)) {
			logDebug(FNC_NAME, "Invalid realm");
			return false;
		}
		if (rtspSessionInfo.authInfo.authPlainPassword.isBlank() &&
				! (rtspSessionInfo.authInfo.authNonceClient.equals(rtspSessionInfo.authInfo.authNonceServer) ||
						RtspStaticSessionInfo.existsAuthServerNonce(
								getClientIpAddr(rtspSessionInfo), rtspSessionInfo.authInfo.authNonceClient
							))) {
			logDebug(FNC_NAME, "Invalid nonce");
			return false;
		}
		final Optional<String> tmpOptUserPw = rtspConfig.getUserPassword(rtspSessionInfo.authInfo.authUser);
		if (tmpOptUserPw.isEmpty()) {
			logDebug(FNC_NAME, "Invalid username");
			return false;
		}
		//
		if (rtspSessionInfo.authInfo.authPlainPassword.isBlank()) {
			final String expectedResponse;
			try {
				expectedResponse = RtspProtoAuthDigest.computeAuthResponse(
						rtspSessionInfo.authInfo.authUser,
						tmpOptUserPw.get(),
						rtspSessionInfo.authInfo.authUri,
						messageType,
						rtspSessionInfo.authInfo.authRealmServer,
						rtspSessionInfo.authInfo.authNonceClient
					);
			} catch (IllegalArgumentException e) {
				logDebug(FNC_NAME, "Invalid authentication parameters: " + e.getMessage());
				return false;
			}
			if (! rtspSessionInfo.authInfo.authResp.equalsIgnoreCase(expectedResponse)) {
				logDebug(FNC_NAME, "Invalid challenge-response");
				return false;
			}
		} else if (! rtspSessionInfo.authInfo.authPlainPassword.equals(tmpOptUserPw.get())) {
			logDebug(FNC_NAME, "Invalid plain password");
			return false;
		}
		//
		return true;
	}

	public boolean checkAccessToInputSource(@NonNull RtspSessionInfo rtspSessionInfo, @NonNull RtspInputSource inputSource) {
		if (! inputSource.getEnabled()) {
			return false;
		}
		if (! inputSource.getNeedsAuthentication()) {
			return true;
		}

		Set<String> tmpUsers = rtspConfig.getUsersAllowedToAccessInputSource(inputSource);
		if (tmpUsers.isEmpty()) {
			return false;
		}
		return tmpUsers.contains(rtspSessionInfo.authInfo.authUser.toLowerCase());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getClientIpAddr(@NonNull RtspSessionInfo rtspSessionInfo) {
		return rtspSessionInfo.getClientIpAddr().orElseThrow(() -> new IllegalStateException("Client IP address is not set"));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
