package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RequestBasicInfo;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

import java.util.Objects;
import java.util.Optional;

/**
 * RTSP Request Authentication and Authorization Service
 */
public class RtspRequAuthSvc {

	/** Maximum number of unauthorized requests per client per Input Source. */
	private static final int MAX_UNAUTHORIZED_REQUESTS_PER_CLIENT_PER_IS = 50;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	private final @NonNull RtspUserAuthSvc rtspUserAuthSvc;

	public RtspRequAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;

		//
		this.rtspUserAuthSvc = new RtspUserAuthSvc(logMsgInterface, rtspConfig, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void checkAuthorization(@NonNull RequestBasicInfo requestBasicInfo) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkAuthorization()";

		final String tmpIsId = Objects.requireNonNull(requestBasicInfo.requestUrlInputOrStreamSource).inputSourceId;
		Objects.requireNonNull(tmpIsId, "requestBasicInfo inputSourceId is null");
		final Optional<RtspInputSource> tmpOptInputSource = rtspConfig.getInputSourceObj(tmpIsId);
		if (tmpOptInputSource.isEmpty()) {
			requestBasicInfo.statusCode = RtspStatusCode.BAD_REQUEST;
			logError(FNC_NAME, String.format(
					"Could not find InputSource, rejecting request with code %s", requestBasicInfo.statusCode));
			return;
		}

		final boolean couldNeedAuthentification = switch (requestBasicInfo.messageType) {
				case RtspMessageType.DESCRIBE, RtspMessageType.SETUP,
				     RtspMessageType.PLAY, RtspMessageType.PAUSE,
				     RtspMessageType.TEARDOWN, RtspMessageType.GET_PARAMETER -> true;
				default -> false;
			};
		boolean wasAuthentificationOk;

		final boolean doCheckAuthorization = (couldNeedAuthentification && tmpOptInputSource.get().getNeedsAuthentication());
		boolean wasAuthorizationOk = (! doCheckAuthorization);

		//
		if (doCheckAuthorization) {
			wasAuthentificationOk = rtspUserAuthSvc.authenticate(requestBasicInfo.messageType);
			//
			if (wasAuthentificationOk) {
				wasAuthorizationOk = rtspUserAuthSvc.checkAccessToInputSource(tmpOptInputSource.get());
				if (! wasAuthorizationOk) {
					logError(FNC_NAME, String.format(
							"User '%s' is not allowed to access IS='%s'",
							rtspSessionInfo.authInfo.authUser,
							tmpIsId));
				}
			}
		} else {
			wasAuthentificationOk = true;
		}

		//
		if (wasAuthentificationOk) {
			// check if Client IP Address is blocked
			int tmpUnauthCnt = RtspStaticSessionInfo.getUnauthorized(rtspSessionInfo.getClientIpAddr(), tmpIsId);
			if (tmpUnauthCnt >= MAX_UNAUTHORIZED_REQUESTS_PER_CLIENT_PER_IS) {
				// reject Client IP Address even if user credentials are OK
				wasAuthentificationOk = false;
			}
		}

		if (! doCheckAuthorization && wasAuthentificationOk) {
			// prevent OPTIONS request from resetting the unauthorized counter
			return;
		}
		if (wasAuthentificationOk && wasAuthorizationOk) {
			final String logMsg = String.format(
					"Accepting %s request for IS='%s' for user '%s' (client IP=%s)",
					requestBasicInfo.messageType, tmpIsId,
					rtspSessionInfo.authInfo.authUser,
					rtspSessionInfo.getClientIpAddr().getHostAddress());
			logDebug(FNC_NAME, logMsg);
			RtspStaticSessionInfo.resetUnauthorized(rtspSessionInfo.getClientIpAddr(), tmpIsId);
			return;
		}

		//
		rejectWithUnauthorized(requestBasicInfo, tmpIsId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void rejectWithUnauthorized(@NonNull RequestBasicInfo requestBasicInfo, @NonNull String inpSrcId) {
		final String FNC_NAME = getClass().getSimpleName() + ".rejectWithUnauthorized()";

		final int unauthCnt = RtspStaticSessionInfo.addUnauthorized(rtspSessionInfo.getClientIpAddr(), inpSrcId);
		//
		requestBasicInfo.statusCode = RtspStatusCode.UNAUTHORIZED;
		//
		final String logMsg = String.format(
				"Rejecting %s request for IS='%s' with code %s (failedCnt=%d, client IP=%s)",
				requestBasicInfo.messageType, inpSrcId,
				requestBasicInfo.statusCode, unauthCnt,
				rtspSessionInfo.getClientIpAddr().getHostAddress());
		if (unauthCnt > 1) {
			/*
			 * One rejection is normal due to the way RTSP clients detect the necessity of authentication.
			 * They send a request without credentials, receive a 401 Unauthorized response, and then retry with credentials.
			 * This behavior is expected and should not be logged as an error.
			 */
			logInfo(FNC_NAME, logMsg);
		} else if (rtspConfig.getLogLevel() == RtxpLogLevel.DEBUG) {
			logDebug(FNC_NAME, logMsg);
		}
		//
		if (unauthCnt > 1) {
			for (int i = 0; i < unauthCnt; i++) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}

	@SuppressWarnings("unused")
	private void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}

	@SuppressWarnings("unused")
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}

	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
