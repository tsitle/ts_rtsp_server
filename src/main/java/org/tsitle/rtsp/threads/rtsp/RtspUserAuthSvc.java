package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.helpers.HashMd5Helper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.util.Optional;
import java.util.Set;

/**
 * RTSP User Authentication and Authorization Service
 */
public class RtspUserAuthSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	public RtspUserAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean authenticate(@NonNull RtspMessageType messageType) {
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
				! rtspSessionInfo.authInfo.authRealmClient.equals(RtspProtoConstants.RTSP_AUTH_REALM)) {
			logDebug(FNC_NAME, "Invalid realm");
			return false;
		}
		if (rtspSessionInfo.authInfo.authPlainPassword.isBlank() &&
				! (rtspSessionInfo.authInfo.authNonceClient.equalsIgnoreCase(rtspSessionInfo.authInfo.authNonceServer) ||
						RtspStaticSessionInfo.existsAuthServerNonce(
								rtspSessionInfo.getClientIpAddr(), rtspSessionInfo.authInfo.authNonceClient
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
			final String expectedResponse = computeExpectedAuthResponse(messageType.name(), tmpOptUserPw.get());
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

	public boolean checkAccessToInputSource(@NonNull RtspInputSource inputSource) {
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

	private @NonNull String computeExpectedAuthResponse(@NonNull String method, @NonNull String userPwPlain) {
		String tmpHa1 = HashMd5Helper.hashOfString(
				rtspSessionInfo.authInfo.authUser + ":" + RtspProtoConstants.RTSP_AUTH_REALM + ":" + userPwPlain,
				false
			);
		String tmpHa2 = HashMd5Helper.hashOfString(
				method + ":" + rtspSessionInfo.authInfo.authUri,
				false
			);
		// we have already verified that the Nonce the client has sent is valid
		return HashMd5Helper.hashOfString(
				tmpHa1 + ":" + rtspSessionInfo.authInfo.authNonceClient + ":" + tmpHa2,
				false
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
