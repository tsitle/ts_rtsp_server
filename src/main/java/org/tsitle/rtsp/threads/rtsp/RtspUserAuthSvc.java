package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthClient;
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

	public boolean authenticate(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspMessageType messageType
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".authenticate()";

		if (requAuthClient.authUser.isBlank()) {
			// fail silently since missing at least the username is normal for the first unauthorized request
			return false;
		}
		if (requAuthClient.authPlainPassword.isBlank() &&
				(requAuthClient.authRealm.isBlank() ||
					requAuthClient.authNonce.isBlank() || requAuthClient.authResp.isBlank())) {
			// fail silently
			return false;
		}
		if (requAuthClient.authPlainPassword.isBlank() &&
				! requAuthClient.authRealm.equals(rtspSessionInfo.permAuthServer.authRealm)) {
			logDebug(FNC_NAME, "Invalid realm");
			return false;
		}
		if (requAuthClient.authPlainPassword.isBlank() &&
				! (requAuthClient.authNonce.equals(rtspSessionInfo.permAuthServer.authNonce) &&
						RtspStaticSessionInfo.existsAuthServerNonce(
								getClientIpAddr(rtspSessionInfo), rtspSessionInfo.permAuthServer.authNonce
							))) {
			logDebug(FNC_NAME, "Invalid nonce");
			return false;
		}
		final Optional<String> tmpOptUserPw = rtspConfig.getUserPassword(requAuthClient.authUser);
		if (tmpOptUserPw.isEmpty()) {
			logDebug(FNC_NAME, "Invalid username");
			return false;
		}
		//
		if (requAuthClient.authPlainPassword.isBlank()) {
			final String expectedResponse;
			try {
				expectedResponse = RtspProtoAuthDigest.computeAuthResponse(
						requAuthClient.authUser,
						tmpOptUserPw.get(),
						requAuthClient.authUri,
						messageType,
						requAuthClient.authRealm,
						requAuthClient.authNonce
					);
			} catch (IllegalArgumentException e) {
				logDebug(FNC_NAME, "Invalid authentication parameters: " + e.getMessage());
				return false;
			}
			if (! requAuthClient.authResp.equalsIgnoreCase(expectedResponse)) {
				logDebug(FNC_NAME, "Invalid challenge-response");
				return false;
			}
		} else if (! requAuthClient.authPlainPassword.equals(tmpOptUserPw.get())) {
			logDebug(FNC_NAME, "Invalid plain password");
			return false;
		}
		//
		return true;
	}

	public boolean checkAccessToInputSource(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspInputSource inputSource
			) {
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
		return tmpUsers.contains(requAuthClient.authUser.toLowerCase());
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
