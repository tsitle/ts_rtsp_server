package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.RtspStaticSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdInputSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoUserAuthInterface;

import java.util.Optional;
import java.util.Set;

/**
 * RTSP User Authentication and Authorization Service
 */
public final class RtspUserAuthSvc implements RtspProtoUserAuthInterface {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;

	public RtspUserAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
		this.availableStreamsInterface = availableStreamsInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean authenticate(
				@NonNull RtspProtoDataCntAuthClient requAuthClient,
				@NonNull RtspMessageType messageType
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
				! requAuthClient.getAuthRealm().equals(rtspSessionInfo.permAuthServer.getAuthRealm())) {
			logDebug(FNC_NAME, "Invalid realm");
			return false;
		}
		if (requAuthClient.getAuthPlainPassword().isBlank() &&
				! (requAuthClient.getAuthNonce().equals(rtspSessionInfo.permAuthServer.getAuthNonce()) &&
						RtspStaticSessionInfo.existsAuthServerNonce(
								rtspSessionInfo.clientIpAddr, rtspSessionInfo.permAuthServer.getAuthNonce()
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
		} catch (RtspIdInputSourceNotFoundException e) {
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
