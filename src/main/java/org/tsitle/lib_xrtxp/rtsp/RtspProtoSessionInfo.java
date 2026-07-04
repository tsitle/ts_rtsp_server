package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HostnameHelper;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class RtspProtoSessionInfo {

	private final ReadWriteLock theLockFields = new ReentrantReadWriteLock();
	private final Lock theReadLockFields = theLockFields.readLock();
	private final Lock theWriteLockFields = theLockFields.writeLock();

	private final ReadWriteLock globalLock = new ReentrantReadWriteLock();
	private final Lock globalWriteLock = globalLock.writeLock();

	/** Authentication-related info from the server */
	private final @NonNull RtspProtoDataCntAuthSrv permAuthServer = new RtspProtoDataCntAuthSrv();

	/** Client IP address */
	private final @NonNull RtspProtoIpAddr clientIpAddr = new RtspProtoIpAddr();

	/** Main stream transport info */
	private final @NonNull RtspProtoDataCntStreamTpMain streamTpMain = new RtspProtoDataCntStreamTpMain();
	private boolean haveSetIsRtspsConnection = false;

	/** RTSP Session ID */
	private final @NonNull RtspProtoIdSession idSession = RtspProtoIdSession.ofEmpty();

	/** Request from remote host: Last received RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requFromRem_lastRcvd = RtspProtoCseqNr.ofEmpty();
	/** Request from remote host: Expected RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requFromRem_expected = RtspProtoCseqNr.ofZero();
	/** Request to remote host: Last sent RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requToRem_lastSent = RtspProtoCseqNr.ofZero();

	/** Name of an unsupported feature that has been requested in an OPTIONS request */
	private @NonNull String unsupportedFeatureName = "";
	/** Features that have been requested in an OPTIONS request */
	private final @NonNull RtspProtoDataCntGetRequFeat rhRequiredFeatures = new RtspProtoDataCntGetRequFeat();
	/** Proxy features that have been requested in an OPTIONS request */
	private final @NonNull RtspProtoDataCntGetRequFeat rhProxyRequiredFeatures = new RtspProtoDataCntGetRequFeat();

	/** Parameter names that were rejected by the remote host, either due to their name or value */
	private final @NonNull RtspProtoDataCntGetSetParamNames rhInvalidParamNamesObj = new RtspProtoDataCntGetSetParamNames();
	private boolean rhInvalidParamNamesIsSet = false;
	/** GET_PARAMETER names that have been requested by the remote host */
	private final @NonNull RtspProtoDataCntGetSetParamNames rhGetParamNamesObj = new RtspProtoDataCntGetSetParamNames();
	private boolean rhGetParamNamesIsSet = false;
	/** GET_PARAMETER values that have been received from the remote host */
	private final @NonNull RtspProtoDataCntGetSetParamKvs rhGetParamValuesObj = new RtspProtoDataCntGetSetParamKvs();
	private boolean rhGetParamValuesIsSet = false;
	/** SET_PARAMETER values that have been received from the remote host */
	private final @NonNull RtspProtoDataCntGetSetParamKvs rhSetParamValuesObj = new RtspProtoDataCntGetSetParamKvs();
	private boolean rhSetParamValuesIsSet = false;

	/** Playback range request value from the client */
	private @NonNull String clientPlaybackRangeValue = "";
	/** Playback range response value from the server */
	private @NonNull String serverPlaybackRangeValue = "";

	/** Client's User-Agent */
	private @NonNull String clientUserAgent = "";
	/** Server's software name and version */
	private @NonNull String serverSoftware = "";

	/** RTSP protocol version to be used */
	private @NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** RTSP message types that are supported by the remote host */
	private final @NonNull RtspProtoDataCntMessageTypes rhSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	// ----------------------------------------------------------------

	/** Stream info from DESCRIBE/SETUP responses */
	private final @NonNull RtspProtoSetupInfosStream descrSetupInfosStream = new RtspProtoSetupInfosStream();

	/** Available Sub-Stream IDs from a DESCRIBE response */
	private final @NonNull Set<@NonNull RtspProtoIdSubStream> descrAvailableSubStreamIds = new HashSet<>();

	// ----------------------------------------------------------------

	/** SDP structured data from a DESCRIBE response */
	private final @NonNull RtspProtoDataCntSdpStructured rhDescribeSdpStcObj = new RtspProtoDataCntSdpStructured();
	private boolean rhDescribeSdpStcIsSet = false;

	/** SDP structured data from an ANNOUNCE request */
	private final @NonNull RtspProtoDataCntSdpStructured rhAnnouncedSdpStcObj = new RtspProtoDataCntSdpStructured();
	private boolean rhAnnouncedSdpStcIsSet = false;

	// ----------------------------------------------------------------

	/** Connection policy as requested by the remote host */
	private @NonNull RtspConnectionPolicy rhConnectionPolicy = RtspConnectionPolicy.NONE;

	// ----------------------------------------------------------------

	/** Current state of the RTSP session */
	private @NonNull RtspProtoSessionState sessionState = RtspProtoSessionState.INIT;

	// ----------------------------------------------------------------

	/** Resource URL that has been used in the last incoming DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull RtspProtoRscUrl lastRequestRscUrl_mainStream = RtspProtoRscUrl.ofEmpty();

	/** Internal only: Results from the last incoming request */
	private final @NonNull RtspProtoDataRequest lastIncomingRequestData = new RtspProtoDataRequest();
	/** Time of the last incoming request */
	private @Nullable Instant lastIncomingRequestTime = null;
	/** Last used outgoing request Resource URL object */
	private final @NonNull RtspProtoRscUrl lastUsedOutgoingRequestResourceUrlObj = RtspProtoRscUrl.ofEmpty();
	/** Last used outgoing request message type */
	private @NonNull RtspProtoMessageType lastUsedOutgoingRequestMsgType = RtspProtoMessageType.UNKNOWN;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void globalWriteLock() {
		globalWriteLock.lock();
	}

	public void globalWriteUnlock() {
		globalWriteLock.unlock();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getPermAuthServerRealm() {
		theReadLockFields.lock();
		try {
			if (permAuthServer.getAuthRealm().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(permAuthServer.getAuthRealm());
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<String> getPermAuthServerNonce() {
		theReadLockFields.lock();
		try {
			if (permAuthServer.getAuthNonce().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(permAuthServer.getAuthNonce());
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoIpAddr getClientIpAddr() {
		theReadLockFields.lock();
		try {
			return clientIpAddr.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}
	public void setClientIpAddr(@NonNull RtspProtoIpAddr value) throws RtspProtoSessionInfoException {
		theWriteLockFields.lock();
		try {
			if (clientIpAddr.equals(value)) {
				return;
			}
			if (! clientIpAddr.isEmpty()) {
				throw new RtspProtoSessionInfoException("Client IP address already set to '" + clientIpAddr.getIpAddrStr().orElseThrow() +
						"' (attempted to change it to '" + value.getIpAddrStr().orElseThrow() + "')");
			}
			clientIpAddr.copyFrom(value);
			clientIpAddr.writeProtect();
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public boolean getIsRtspsConnection() {
		theReadLockFields.lock();
		try {
			return streamTpMain.getIsRtspsConnection();
		} finally {
			theReadLockFields.unlock();
		}
	}
	public void setIsRtspsConnection(boolean value) throws RtspProtoSessionInfoException {
		theWriteLockFields.lock();
		try {
			if (haveSetIsRtspsConnection && value == streamTpMain.getIsRtspsConnection()) {
				return;
			}
			if (haveSetIsRtspsConnection) {
				throw new RtspProtoSessionInfoException("IsRtspsConnection already set to " +
						(streamTpMain.getIsRtspsConnection() ? "T" : "F") + " (attempted to change it to " +
						(value ? "T" : "F") + ")");
			}
			streamTpMain.setIsRtspsConnection(value);
			haveSetIsRtspsConnection = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	public boolean getIsTransportSrtpSrtcp() {
		theReadLockFields.lock();
		try {
			return streamTpMain.getIsTransportSrtpSrtcp();
		} finally {
			theReadLockFields.unlock();
		}
	}

	public boolean getIsTransportUdp() {
		theReadLockFields.lock();
		try {
			return streamTpMain.getIsTransportUdp();
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoIdSession getIdSession() {
		theReadLockFields.lock();
		try {
			return idSession.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getUnsupportedFeatureName() {
		theReadLockFields.lock();
		try {
			if (unsupportedFeatureName.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(unsupportedFeatureName);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetRequFeat> getRhRequiredFeatures() {
		theReadLockFields.lock();
		try {
			if (rhRequiredFeatures.isFeatureNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetRequFeat resObj = new RtspProtoDataCntGetRequFeat();
			resObj.copyFrom(rhRequiredFeatures);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetRequFeat> getRhProxyRequiredFeatures() {
		theReadLockFields.lock();
		try {
			if (rhProxyRequiredFeatures.isFeatureNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetRequFeat resObj = new RtspProtoDataCntGetRequFeat();
			resObj.copyFrom(rhProxyRequiredFeatures);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoDataCntGetSetParamNames> getRhInvalidParamNames() {
		theReadLockFields.lock();
		try {
			if (! rhInvalidParamNamesIsSet || rhInvalidParamNamesObj.isParamNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamNames resObj = new RtspProtoDataCntGetSetParamNames();
			resObj.copyFrom(rhInvalidParamNamesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamNames> getRhGetParamNames() {
		theReadLockFields.lock();
		try {
			if (! rhGetParamNamesIsSet || rhGetParamNamesObj.isParamNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamNames resObj = new RtspProtoDataCntGetSetParamNames();
			resObj.copyFrom(rhGetParamNamesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamKvs> getRhGetParamValues() {
		theReadLockFields.lock();
		try {
			if (! rhGetParamValuesIsSet || rhGetParamValuesObj.isParamKvsEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.copyFrom(rhGetParamValuesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamKvs> getRhSetParamValues() {
		theReadLockFields.lock();
		try {
			if (! rhSetParamValuesIsSet || rhSetParamValuesObj.isParamKvsEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.copyFrom(rhSetParamValuesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getClientPlaybackRangeValue() {
		theReadLockFields.lock();
		try {
			if (clientPlaybackRangeValue.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(clientPlaybackRangeValue);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<String> getServerPlaybackRangeValue() {
		theReadLockFields.lock();
		try {
			if (serverPlaybackRangeValue.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(serverPlaybackRangeValue);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getClientUserAgent() {
		theReadLockFields.lock();
		try {
			if (clientUserAgent.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(clientUserAgent);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<String> getServerSoftware() {
		theReadLockFields.lock();
		try {
			if (serverSoftware.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(serverSoftware);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoDataCntMessageTypes getRhSupportedMessageTypes() {
		theReadLockFields.lock();
		try {
			RtspProtoDataCntMessageTypes resObj = new RtspProtoDataCntMessageTypes();
			resObj.copyFrom(rhSupportedMessageTypes);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull Set<RtspProtoIdSubStream> getDescrSetupInfoSubStreamIds() {
		theReadLockFields.lock();
		try {
			return descrSetupInfosStream.getSubStreamIds();
		} finally {
			theReadLockFields.unlock();
		}
	}

	public @NonNull Set<RtspProtoRscUrl> getDescrSetupInfoRscUrls() {
		theReadLockFields.lock();
		try {
			return descrSetupInfosStream.getRscUrls();
		} finally {
			theReadLockFields.unlock();
		}
	}

	public @NonNull RtspProtoSetupInfoForSubStream getDescrSetupInfoBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		theReadLockFields.lock();
		try {
			RtspProtoSetupInfoForSubStream tmpSiPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" +
							idSubStream.getIdStr().orElse("-unset-") + "'")
				);
			RtspProtoSetupInfoForSubStream resObj = new RtspProtoSetupInfoForSubStream(tmpSiPtr);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLockFields.unlock();
		}
	}

	public @NonNull RtspProtoIdXsrc getDescrSetupInfoSsrcOutboundBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		theReadLockFields.lock();
		try {
			return descrSetupInfosStream.getSsrcOutboundBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" +
							idSubStream.getIdStr().orElse("-unset-") + "'")
				).clone();
		} finally {
			theReadLockFields.unlock();
		}
	}

	public boolean getDescrSetupInfoHaveSetupForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLockFields.lock();
		try {
			return descrSetupInfosStream.haveSetupForSubStreamId(idSubStream);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<SrtxpKmd> getDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLockFields.lock();
		try {
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSsPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream);
			if (tmpOptSiSsPtr.isEmpty()) {
				return Optional.empty();
			}
			return tmpOptSiSsPtr.get().getKmdInboundNextPtr().getKmd();
		} finally {
			theReadLockFields.unlock();
		}
	}

	public void clearDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theWriteLockFields.lock();
		try {
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSsPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream);
			if (tmpOptSiSsPtr.isEmpty()) {
				return;
			}
			tmpOptSiSsPtr.get().getKmdInboundNextPtr().clear();
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// --------------------------------------------------

	public @NonNull Set<@NonNull RtspProtoIdSubStream> getDescrAvailableSubStreamIds() {
		theReadLockFields.lock();
		try {
			Set<RtspProtoIdSubStream> resSet = new HashSet<>();
			for (RtspProtoIdSubStream idSubStream : descrAvailableSubStreamIds) {
				resSet.add(idSubStream.clone());
			}
			return resSet;
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoDataCntSdpStructured> getRhDescribeSdpStc() {
		theReadLockFields.lock();
		try {
			if (! rhDescribeSdpStcIsSet) {
				return Optional.empty();
			}
			RtspProtoDataCntSdpStructured resObj = new RtspProtoDataCntSdpStructured();
			resObj.copyFrom(rhDescribeSdpStcObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoDataCntSdpStructured> getRhAnnouncedSdpStc() {
		theReadLockFields.lock();
		try {
			if (! rhAnnouncedSdpStcIsSet) {
				return Optional.empty();
			}
			RtspProtoDataCntSdpStructured resObj = new RtspProtoDataCntSdpStructured();
			resObj.copyFrom(rhAnnouncedSdpStcObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspConnectionPolicy> getRhConnectionPolicy() {
		theReadLockFields.lock();
		try {
			if (rhConnectionPolicy == RtspConnectionPolicy.NONE) {
				return Optional.empty();
			}
			return Optional.of(rhConnectionPolicy);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoSessionState getSessionState() {
		theReadLockFields.lock();
		try {
			return sessionState;
		} finally {
			theReadLockFields.unlock();
		}
	}

	/**
	 * Moves to the next Session State after a successful request based on the given message type.
	 * @param messageType The type of the last successful request.
	 * @return True if the Session State was changed, false otherwise.
	 */
	public boolean moveToNextSessionState(@NonNull RtspProtoMessageType messageType) {
		theWriteLockFields.lock();
		try {
			RtspProtoSessionState nextState = sessionState;
			switch (messageType) {
				case RtspProtoMessageType.SETUP, RtspProtoMessageType.PAUSE -> nextState = RtspProtoSessionState.READY;
				case RtspProtoMessageType.PLAY -> nextState = RtspProtoSessionState.PLAYING;
				case RtspProtoMessageType.TEARDOWN -> nextState = RtspProtoSessionState.INIT;
			}
			if (sessionState == nextState) {
				return false;
			}
			sessionState = nextState;
			return true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoRscUrl> getLastRequestResourceUrl_mainStream() {
		theReadLockFields.lock();
		try {
			if (lastRequestRscUrl_mainStream.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(lastRequestRscUrl_mainStream.clone());
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoRscUrl> getRequestResourceUrl_subStream(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLockFields.lock();
		try {
			return descrSetupInfosStream.getResourceUrlBySubStreamId(idSubStream);
		} finally {
			theReadLockFields.unlock();
		}
	}

	public long getLastIncomingRequestTimeDeltaSeconds() {
		theReadLockFields.lock();
		try {
			if (lastIncomingRequestTime == null) {
				return -1;
			}
			return Duration.between(lastIncomingRequestTime, Instant.now()).toSeconds();
		} finally {
			theReadLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoRscUrl> getLastUsedOutgoingRequestResourceUrl() {
		theReadLockFields.lock();
		try {
			if (lastUsedOutgoingRequestResourceUrlObj.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(lastUsedOutgoingRequestResourceUrlObj.clone());
		} finally {
			theReadLockFields.unlock();
		}
	}

	public Optional<RtspProtoMessageType> getLastUsedOutgoingRequestMsgType() {
		theReadLockFields.lock();
		try {
			if (lastUsedOutgoingRequestMsgType == RtspProtoMessageType.UNKNOWN) {
				return Optional.empty();
			}
			return Optional.of(lastUsedOutgoingRequestMsgType);
		} finally {
			theReadLockFields.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clearAfterTeardown() {
		theWriteLockFields.lock();
		try {
			boolean isRtsps = streamTpMain.getIsRtspsConnection();
			boolean isUdp = streamTpMain.getIsTransportUdp();
			streamTpMain.clear();
			streamTpMain.setIsRtspsConnection(isRtsps);
			streamTpMain.setIsTransportUdp(isUdp);

			descrSetupInfosStream.clear();
			descrAvailableSubStreamIds.clear();
			rhDescribeSdpStcObj.clear();
			rhDescribeSdpStcIsSet = false;
			lastRequestRscUrl_mainStream.clear();
			sessionState = RtspProtoSessionState.INIT;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------
	// ----------------------------------------------------

	@NonNull RtspProtoDataCntAuthSrv getPermAuthServer() {
		theReadLockFields.lock();
		try {
			return permAuthServer.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setPermAuthServer(@NonNull RtspProtoDataCntAuthSrv value) {
		theWriteLockFields.lock();
		try {
			permAuthServer.copyFrom(value);
			permAuthServer.writeProtect();
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoDataCntStreamTpMain getStreamTpMain() {
		theReadLockFields.lock();
		try {
			RtspProtoDataCntStreamTpMain resObj = new RtspProtoDataCntStreamTpMain();
			resObj.copyFrom(streamTpMain);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setStreamTpMainForceRtpRtcpEncryption() {
		theWriteLockFields.lock();
		try {
			streamTpMain.setForceRtpRtcpEncryption(true);
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void setStreamTpMainRtpRtcpEncryptionRequired() {
		theWriteLockFields.lock();
		try {
			streamTpMain.setRtpRtcpEncryptionRequired(true);
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void setStreamTpMainIsTransportUdp() {
		theWriteLockFields.lock();
		try {
			streamTpMain.setIsTransportUdp(true);
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void setStreamTpMainIsTransportTcp() {
		theWriteLockFields.lock();
		try {
			streamTpMain.setIsTransportUdp(false);
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void setStreamTpMainIsTransportSrtpSrtcp() {
		theWriteLockFields.lock();
		try {
			streamTpMain.setIsTransportSrtpSrtcp(true);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setSessionId(@NonNull RtspProtoIdSession value) {
		theWriteLockFields.lock();
		try {
			idSession.copyFrom(value);
			idSession.writeProtect();
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoCseqNr getCseqNr_requFromRem_lastRcvd() {
		theReadLockFields.lock();
		try {
			return cseqNr_requFromRem_lastRcvd.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setCseqNr_requFromRem_lastRcvd(@NonNull RtspProtoCseqNr value) {
		theWriteLockFields.lock();
		try {
			cseqNr_requFromRem_lastRcvd.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	@NonNull RtspProtoCseqNr getCseqNr_requFromRem_expected() {
		theReadLockFields.lock();
		try {
			return cseqNr_requFromRem_expected.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setCseqNr_requFromRem_expected(@NonNull RtspProtoCseqNr value) {
		theWriteLockFields.lock();
		try {
			cseqNr_requFromRem_expected.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	@NonNull RtspProtoCseqNr getCseqNr_requToRem_lastSent() {
		theReadLockFields.lock();
		try {
			return cseqNr_requToRem_lastSent.clone();
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setCseqNr_requToRem_lastSent(@NonNull RtspProtoCseqNr value) {
		theWriteLockFields.lock();
		try {
			cseqNr_requToRem_lastSent.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setUnsupportedFeatureName(@NonNull String value) {
		theWriteLockFields.lock();
		try {
			unsupportedFeatureName = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhRequiredFeatures(@NonNull RtspProtoDataCntGetRequFeat value) {
		theWriteLockFields.lock();
		try {
			rhRequiredFeatures.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhProxyRequiredFeatures(@NonNull RtspProtoDataCntGetRequFeat value) {
		theWriteLockFields.lock();
		try {
			rhProxyRequiredFeatures.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhInvalidParamNames(@NonNull RtspProtoDataCntGetSetParamNames value) {
		theWriteLockFields.lock();
		try {
			rhInvalidParamNamesObj.copyFrom(value);
			rhInvalidParamNamesIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhGetParamNames(@NonNull RtspProtoDataCntGetSetParamNames value) {
		theWriteLockFields.lock();
		try {
			rhGetParamNamesObj.copyFrom(value);
			rhGetParamNamesIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void clearRhGetParamNames() {
		theWriteLockFields.lock();
		try {
			rhGetParamNamesObj.clear();
			rhGetParamNamesIsSet = false;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhGetParamValues(@NonNull RtspProtoDataCntGetSetParamKvs value) {
		theWriteLockFields.lock();
		try {
			rhGetParamValuesObj.copyFrom(value);
			rhGetParamValuesIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhSetParamValues(@NonNull RtspProtoDataCntGetSetParamKvs setParamKvs) {
		theWriteLockFields.lock();
		try {
			rhSetParamValuesObj.copyFrom(setParamKvs);
			rhSetParamValuesIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void clearRhSetParamValues() {
		theWriteLockFields.lock();
		try {
			rhSetParamValuesObj.clear();
			rhSetParamValuesIsSet = false;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setClientPlaybackRangeValue(@NonNull String value) {
		theWriteLockFields.lock();
		try {
			clientPlaybackRangeValue = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setServerPlaybackRangeValue(@NonNull String value) {
		theWriteLockFields.lock();
		try {
			serverPlaybackRangeValue = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setClientUserAgent(@NonNull String value) {
		theWriteLockFields.lock();
		try {
			clientUserAgent = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setServerSoftware(@NonNull String value) {
		theWriteLockFields.lock();
		try {
			serverSoftware = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		theReadLockFields.lock();
		try {
			return rtspProtoVersionToUse;
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setRtspProtoVersionToUse(@NonNull RtspProtocolVersion value) {
		theWriteLockFields.lock();
		try {
			rtspProtoVersionToUse = value;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhSupportedMessageTypes(@NonNull RtspProtoDataCntMessageTypes value) {
		theWriteLockFields.lock();
		try {
			rhSupportedMessageTypes.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoSetupInfosStream getDescrSetupInfosStream() {
		theReadLockFields.lock();
		try {
			RtspProtoSetupInfosStream resObj = new RtspProtoSetupInfosStream();
			resObj.copyFrom(descrSetupInfosStream);
			return resObj;
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setDescrSetupInfosStream(@NonNull RtspProtoSetupInfosStream value) {
		theWriteLockFields.lock();
		try {
			descrSetupInfosStream.copyFrom(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}
	void setDescrSetupInfosForSubStream(@NonNull RtspProtoIdSubStream idSubStream, @NonNull RtspProtoSetupInfoForSubStream value) {
		theWriteLockFields.lock();
		try {
			descrSetupInfosStream.replaceSiForSubStream(idSubStream, value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setDescrAvailableSubStreamIds(@NonNull Set<@NonNull RtspProtoIdSubStream> value) {
		theWriteLockFields.lock();
		try {
			descrAvailableSubStreamIds.clear();
			descrAvailableSubStreamIds.addAll(value);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhDescribeSdpStc(@NonNull RtspProtoDataCntSdpStructured sdpStructured) {
		theWriteLockFields.lock();
		try {
			rhDescribeSdpStcObj.copyFrom(sdpStructured);
			rhDescribeSdpStcIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setRhAnnouncedSdpStc(@NonNull RtspProtoDataCntSdpStructured sdpStructured) {
		theWriteLockFields.lock();
		try {
			rhAnnouncedSdpStcObj.copyFrom(sdpStructured);
			rhAnnouncedSdpStcIsSet = true;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhConnectionPolicy(@NonNull RtspConnectionPolicy connectionPolicy) {
		theWriteLockFields.lock();
		try {
			rhConnectionPolicy = connectionPolicy;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setLastRequestRscUrl_mainStream(@NonNull RtspProtoRscUrl rscUrl) {
		if (! rscUrl.idSubStream.isEmpty()) {
			return;
		}
		theWriteLockFields.lock();
		try {
			lastRequestRscUrl_mainStream.copyFrom(rscUrl);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	@NonNull RtspProtoDataRequest getLastIncomingRequestData() {
		theReadLockFields.lock();
		try {
			RtspProtoDataRequest resObj = new RtspProtoDataRequest();
			resObj.copyFrom(lastIncomingRequestData);
			return resObj;
		} finally {
			theReadLockFields.unlock();
		}
	}
	void setLastIncomingRequestData(@NonNull RtspProtoDataRequest data) {
		theWriteLockFields.lock();
		try {
			lastIncomingRequestData.copyFrom(data);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void updateLastIncomingRequestTime() {
		theWriteLockFields.lock();
		try {
			lastIncomingRequestTime = Instant.now();
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// ----------------------------------------------------

	void setLastUsedOutgoingRequestResourceUrl(@NonNull RtspProtoRscUrl rscUrl) {
		theWriteLockFields.lock();
		try {
			lastUsedOutgoingRequestResourceUrlObj.copyFrom(rscUrl);
		} finally {
			theWriteLockFields.unlock();
		}
	}

	void setLastUsedOutgoingRequestMsgType(@NonNull RtspProtoMessageType requestMessageType) {
		theWriteLockFields.lock();
		try {
			lastUsedOutgoingRequestMsgType = requestMessageType;
		} finally {
			theWriteLockFields.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspProtoIpAddr findRtspIpFromResourceUrl(@NonNull RtspProtoRscUrl rscUrl)
			throws RtspProtoCannotFindIpFromRscUrlException {
		final String FNC_NAME = RtspProtoSessionInfo.class.getSimpleName() + ".findRtspIpFromResourceUrl()";

		if (rscUrl.getUrlStr().isBlank()) {
			throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": No Resource URL set");
		}
		String tmpRtspHostname;
		try {
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(rscUrl.getUrlStr());
			tmpRtspHostname = rscUriObj.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": Could not parse URL: " + e.getMessage());
		}
		if (tmpRtspHostname.isBlank()) {
			throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(
					tmpRtspHostname,
					true
				);
			if (optRtspHostIp.isEmpty()) {
				throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" +
						tmpRtspHostname + "'");
			}

			RtspProtoIpAddr resObj = RtspProtoIpAddr.of(optRtspHostIp.get());
			resObj.writeProtect();
			return resObj;
		} catch (UnknownHostException | SocketException e) {
			throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

}
