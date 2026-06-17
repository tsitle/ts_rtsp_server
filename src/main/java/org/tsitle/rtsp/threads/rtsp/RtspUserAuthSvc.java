package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoAuthDigest;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;

import java.util.Optional;
import java.util.Set;

/**
 * RTSP User Authentication and Authorization Service
 */
final class RtspUserAuthSvc implements RtspProtoUserAuthInterface {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;

	public RtspUserAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean authenticate(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspProtoMessageType messageType
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".authenticate()";

		if (requAuthClient.getAuthUser().isBlank()) {
			// fail silently since missing at least the username is normal for the first unauthorized request
			return false;
		}
		if (requAuthClient.getAuthPlainPassword().isBlank() &&
				(requAuthClient.getAuthRealm().isBlank() ||
					requAuthClient.getAuthNonce().isBlank() || requAuthClient.getAuthResp().isBlank())) {
			// fail silently
			return false;
		}
		if (requAuthClient.getAuthPlainPassword().isBlank() &&
				! requAuthClient.getAuthRealm().equals(rtspSessionInfo.getPermAuthServerRealm())) {
			logDebug(FNC_NAME, "Invalid realm");
			return false;
		}
		if (requAuthClient.getAuthPlainPassword().isBlank() &&
				! (requAuthClient.getAuthNonce().equals(rtspSessionInfo.getPermAuthServerNonce()) &&
						globalSessionInfoInterface.existsAuthServerNonce(
								rtspSessionInfo.getClientIpAddr(), rtspSessionInfo.getPermAuthServerNonce()
							))) {
			logDebug(FNC_NAME, "Invalid nonce");
			return false;
		}
		final Optional<String> tmpOptUserPw = rtspConfig.getUserPassword(requAuthClient.getAuthUser());
		if (tmpOptUserPw.isEmpty()) {
			logDebug(FNC_NAME, "Invalid username");
			return false;
		}
		//
		if (requAuthClient.getAuthPlainPassword().isBlank()) {
			final String expectedResponse;
			try {
				expectedResponse = RtspProtoAuthDigest.computeAuthResponse(
						requAuthClient.getAuthUser(),
						tmpOptUserPw.get(),
						requAuthClient.getAuthUri(),
						messageType,
						requAuthClient.getAuthRealm(),
						requAuthClient.getAuthNonce()
					);
			} catch (IllegalArgumentException e) {
				logDebug(FNC_NAME, "Invalid authentication parameters: " + e.getMessage());
				return false;
			}
			if (! requAuthClient.getAuthResp().equalsIgnoreCase(expectedResponse)) {
				logDebug(FNC_NAME, "Invalid challenge-response");
				return false;
			}
		} else if (! requAuthClient.getAuthPlainPassword().equals(tmpOptUserPw.get())) {
			logDebug(FNC_NAME, "Invalid plain password");
			return false;
		}
		//
		return true;
	}

	@Override
	public boolean checkAccessToInputSource(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		RtspProtoInputSource tmpIsObj;
		try {
			tmpIsObj = availableStreamsInterface.getInputSourceObj(idInputSource);
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			return false;
		}
		if (! tmpIsObj.getEnabled()) {
			return false;
		}
		if (! tmpIsObj.getNeedsAuthentication()) {
			return true;
		}

		Set<String> tmpUsers = rtspConfig.getUsersAllowedToAccessInputSource(idInputSource);
		if (tmpUsers.isEmpty()) {
			return false;
		}
		return tmpUsers.contains(requAuthClient.getAuthUser().toLowerCase());
	}

	// -----------------------------------------------------------------------------------------------------------------
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
