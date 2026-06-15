package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthClient;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdInputSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoUserAuthInterface;

/**
 * RTSP Request Authentication and Authorization Service
 */
public class RtspProtoRequAuthSvc {

	/** Maximum number of unauthorized requests per client per Input Source. */
	private static final int MAX_UNAUTHORIZED_REQUESTS_PER_CLIENT_PER_IS = 50;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtxpLogLevel rtxpLogLevel;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspProtoUserAuthInterface userAuthInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;

	public RtspProtoRequAuthSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpLogLevel rtxpLogLevel,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtspProtoUserAuthInterface userAuthInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtxpLogLevel = rtxpLogLevel;
		this.rtspSessionInfo = rtspSessionInfo;
		this.userAuthInterface = userAuthInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void checkAuthorization(
				@NonNull RtspRequestBasics ioRequestBasics,
				@NonNull RtspProtoDataCntAuthClient requAuthClient
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkAuthorization()";

		if (ioRequestBasics.rscUrl.idInputSource.isEmpty()) {
			ioRequestBasics.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logError(FNC_NAME, String.format(
					"Input Source ID is empty, rejecting request with code %s", ioRequestBasics.statusCode));
			return;
		}

		final RtspProtoIdInputSource tmpIdIs = ioRequestBasics.rscUrl.idInputSource;
		RtspProtoInputSource tmpIsObj;
		try {
			tmpIsObj = availableStreamsInterface.getInputSourceObj(ioRequestBasics.rscUrl.idInputSource);
		} catch (RtspIdInputSourceNotFoundException e) {
			ioRequestBasics.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logError(FNC_NAME, String.format(
					"Input Source not found, rejecting request with code %s", ioRequestBasics.statusCode));
			return;
		}

		final boolean couldNeedAuthentification = switch (ioRequestBasics.messageType) {
				case RtspMessageType.UNKNOWN, RtspMessageType.OPTIONS -> false;
				default -> true;
			};
		boolean wasAuthentificationOk;

		final boolean doCheckAuthorization = (couldNeedAuthentification && tmpIsObj.getNeedsAuthentication());
		boolean wasAuthorizationOk = (! doCheckAuthorization);

		//
		if (doCheckAuthorization) {
			wasAuthentificationOk = userAuthInterface.authenticate(requAuthClient, ioRequestBasics.messageType);
			//
			if (wasAuthentificationOk) {
				wasAuthorizationOk = userAuthInterface.checkAccessToInputSource(requAuthClient, tmpIdIs);
				if (! wasAuthorizationOk) {
					logError(FNC_NAME, String.format(
							"User '%s' is not allowed to access IS='%s'",
							requAuthClient.getAuthUser(),
							tmpIdIs.getIdStr()));
				}
			}
		} else {
			wasAuthentificationOk = true;
		}

		//
		if (wasAuthentificationOk) {
			// check if Client IP Address is blocked
			int tmpUnauthCnt = globalSessionInfoInterface.getUnauthorized(rtspSessionInfo.clientIpAddr, tmpIdIs);
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
					ioRequestBasics.messageType, tmpIdIs.getIdStr(),
					requAuthClient.getAuthUser(),
					rtspSessionInfo.clientIpAddr.getIpAddrStr().orElseThrow());
			logDebug(FNC_NAME, logMsg);
			globalSessionInfoInterface.resetUnauthorized(rtspSessionInfo.clientIpAddr, tmpIdIs);
			return;
		}

		//
		rejectWithUnauthorized(ioRequestBasics, tmpIdIs);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void rejectWithUnauthorized(@NonNull RtspRequestBasics rtspRequestBasics, @NonNull RtspProtoIdInputSource idInputSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".rejectWithUnauthorized()";

		final int unauthCnt = globalSessionInfoInterface.incrementUnauthorized(rtspSessionInfo.clientIpAddr, idInputSource);
		//
		rtspRequestBasics.statusCode = RtspStatusCode.UNAUTHORIZED;
		//
		final String logMsg = String.format(
				"Rejecting %s request for IS='%s' with code %s (failedCnt=%d, client IP=%s)",
				rtspRequestBasics.messageType, idInputSource.getIdStr(),
				rtspRequestBasics.statusCode, unauthCnt,
				rtspSessionInfo.clientIpAddr.getIpAddrStr().orElseThrow());
		if (unauthCnt > 1) {
			/*
			 * One rejection is normal due to the way RTSP clients detect the necessity of authentication.
			 * They send a request without credentials, receive a 401 Unauthorized response, and then retry with credentials.
			 * This behavior is expected and should not be logged as an error.
			 */
			logInfo(FNC_NAME, logMsg);
		} else if (rtxpLogLevel == RtxpLogLevel.DEBUG) {
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

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logInfo(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.INFO, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
