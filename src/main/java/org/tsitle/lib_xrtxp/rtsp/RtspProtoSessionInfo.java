package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class RtspProtoSessionInfo {

	private final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private final Lock theReadLock = theLock.readLock();
	private final Lock theWriteLock = theLock.writeLock();

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

	/** Resource URL per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspProtoMessageType, @NonNull RtspProtoRscUrl> resourceUrlPerMtMap_nonSetup = new ConcurrentHashMap<>();

	// ----------------------------------------------------------------

	/** Last used outgoing request Resource URL object */
	private final @NonNull RtspProtoRscUrl lastUsedOutgoingRequestResourceUrlObj = RtspProtoRscUrl.ofEmpty();
	/** Last used outgoing request message type */
	private @NonNull RtspProtoMessageType lastUsedOutgoingRequestMsgType = RtspProtoMessageType.UNKNOWN;
	/** Internal only: Results from the last incoming request */
	private final @NonNull RtspProtoDataRequest lastIncomingRequestData = new RtspProtoDataRequest();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getPermAuthServerRealm() {
		theReadLock.lock();
		try {
			if (permAuthServer.getAuthRealm().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(permAuthServer.getAuthRealm());
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<String> getPermAuthServerNonce() {
		theReadLock.lock();
		try {
			if (permAuthServer.getAuthNonce().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(permAuthServer.getAuthNonce());
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoIpAddr getClientIpAddr() {
		theReadLock.lock();
		try {
			return clientIpAddr.clone();
		} finally {
			theReadLock.unlock();
		}
	}
	public void setClientIpAddr(@NonNull RtspProtoIpAddr value) throws RtspProtoSessionInfoException {
		theWriteLock.lock();
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
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	public boolean getIsRtspsConnection() {
		theReadLock.lock();
		try {
			return streamTpMain.getIsRtspsConnection();
		} finally {
			theReadLock.unlock();
		}
	}
	public void setIsRtspsConnection(boolean value) throws RtspProtoSessionInfoException {
		theWriteLock.lock();
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
			theWriteLock.unlock();
		}
	}

	public boolean getIsTransportSrtpSrtcp() {
		theReadLock.lock();
		try {
			return streamTpMain.getIsTransportSrtpSrtcp();
		} finally {
			theReadLock.unlock();
		}
	}

	public boolean getIsTransportUdp() {
		theReadLock.lock();
		try {
			return streamTpMain.getIsTransportUdp();
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoIdSession getIdSession() {
		theReadLock.lock();
		try {
			return idSession.clone();
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getUnsupportedFeatureName() {
		theReadLock.lock();
		try {
			if (unsupportedFeatureName.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(unsupportedFeatureName);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetRequFeat> getRhRequiredFeatures() {
		theReadLock.lock();
		try {
			if (rhRequiredFeatures.isFeatureNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetRequFeat resObj = new RtspProtoDataCntGetRequFeat();
			resObj.copyFrom(rhRequiredFeatures);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetRequFeat> getRhProxyRequiredFeatures() {
		theReadLock.lock();
		try {
			if (rhProxyRequiredFeatures.isFeatureNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetRequFeat resObj = new RtspProtoDataCntGetRequFeat();
			resObj.copyFrom(rhProxyRequiredFeatures);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoDataCntGetSetParamNames> getRhInvalidParamNames() {
		theReadLock.lock();
		try {
			if (! rhInvalidParamNamesIsSet || rhInvalidParamNamesObj.isParamNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamNames resObj = new RtspProtoDataCntGetSetParamNames();
			resObj.copyFrom(rhInvalidParamNamesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamNames> getRhGetParamNames() {
		theReadLock.lock();
		try {
			if (! rhGetParamNamesIsSet || rhGetParamNamesObj.isParamNamesEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamNames resObj = new RtspProtoDataCntGetSetParamNames();
			resObj.copyFrom(rhGetParamNamesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamKvs> getRhGetParamValues() {
		theReadLock.lock();
		try {
			if (! rhGetParamValuesIsSet || rhGetParamValuesObj.isParamKvsEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.copyFrom(rhGetParamValuesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntGetSetParamKvs> getRhSetParamValues() {
		theReadLock.lock();
		try {
			if (! rhSetParamValuesIsSet || rhSetParamValuesObj.isParamKvsEmpty()) {
				return Optional.empty();
			}
			RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
			resObj.copyFrom(rhSetParamValuesObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getClientPlaybackRangeValue() {
		theReadLock.lock();
		try {
			if (clientPlaybackRangeValue.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(clientPlaybackRangeValue);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<String> getServerPlaybackRangeValue() {
		theReadLock.lock();
		try {
			if (serverPlaybackRangeValue.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(serverPlaybackRangeValue);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<String> getClientUserAgent() {
		theReadLock.lock();
		try {
			if (clientUserAgent.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(clientUserAgent);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<String> getServerSoftware() {
		theReadLock.lock();
		try {
			if (serverSoftware.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(serverSoftware);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoDataCntMessageTypes getRhSupportedMessageTypes() {
		theReadLock.lock();
		try {
			RtspProtoDataCntMessageTypes resObj = new RtspProtoDataCntMessageTypes();
			resObj.copyFrom(rhSupportedMessageTypes);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull Set<RtspProtoIdSubStream> getDescrSetupInfoSubStreamIds() {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getSubStreamIds();
		} finally {
			theReadLock.unlock();
		}
	}

	public @NonNull Set<RtspProtoRscUrl> getDescrSetupInfoRscUrls() {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getRscUrls();
		} finally {
			theReadLock.unlock();
		}
	}

	public @NonNull RtspProtoSetupInfoForSubStream getDescrSetupInfoBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		theReadLock.lock();
		try {
			RtspProtoSetupInfoForSubStream tmpSiPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" +
							idSubStream.getIdStr().orElse("-unset-") + "'")
				);
			RtspProtoSetupInfoForSubStream resObj = new RtspProtoSetupInfoForSubStream(tmpSiPtr);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}

	public @NonNull RtspProtoIdXsrc getDescrSetupInfoSsrcOutboundBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getSsrcOutboundBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" +
							idSubStream.getIdStr().orElse("-unset-") + "'")
				).clone();
		} finally {
			theReadLock.unlock();
		}
	}

	public boolean getDescrSetupInfoHaveSetupForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.haveSetupForSubStreamId(idSubStream);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<SrtxpKmd> getDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLock.lock();
		try {
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSsPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream);
			if (tmpOptSiSsPtr.isEmpty()) {
				return Optional.empty();
			}
			return tmpOptSiSsPtr.get().getKmdInboundNextPtr().getKmd();
		} finally {
			theReadLock.unlock();
		}
	}

	public void clearDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theWriteLock.lock();
		try {
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSsPtr = descrSetupInfosStream.getSiPtrBySubStreamId(idSubStream);
			if (tmpOptSiSsPtr.isEmpty()) {
				return;
			}
			tmpOptSiSsPtr.get().getKmdInboundNextPtr().clear();
		} finally {
			theWriteLock.unlock();
		}
	}

	// --------------------------------------------------

	public @NonNull Set<@NonNull RtspProtoIdSubStream> getDescrAvailableSubStreamIds() {
		theReadLock.lock();
		try {
			Set<RtspProtoIdSubStream> resSet = new HashSet<>();
			for (RtspProtoIdSubStream idSubStream : descrAvailableSubStreamIds) {
				resSet.add(idSubStream.clone());
			}
			return resSet;
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoDataCntSdpStructured> getRhDescribeSdpStc() {
		theReadLock.lock();
		try {
			if (! rhDescribeSdpStcIsSet) {
				return Optional.empty();
			}
			RtspProtoDataCntSdpStructured resObj = new RtspProtoDataCntSdpStructured();
			resObj.copyFrom(rhDescribeSdpStcObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoDataCntSdpStructured> getRhAnnouncedSdpStc() {
		theReadLock.lock();
		try {
			if (! rhAnnouncedSdpStcIsSet) {
				return Optional.empty();
			}
			RtspProtoDataCntSdpStructured resObj = new RtspProtoDataCntSdpStructured();
			resObj.copyFrom(rhAnnouncedSdpStcObj);
			resObj.writeProtect();
			return Optional.of(resObj);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspConnectionPolicy> getRhConnectionPolicy() {
		theReadLock.lock();
		try {
			if (rhConnectionPolicy == RtspConnectionPolicy.NONE) {
				return Optional.empty();
			}
			return Optional.of(rhConnectionPolicy);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public @NonNull RtspProtoSessionState getSessionState() {
		theReadLock.lock();
		try {
			return sessionState;
		} finally {
			theReadLock.unlock();
		}
	}

	/**
	 * Moves to the next Session State after a successful request based on the given message type.
	 * @param messageType The type of the last successful request.
	 * @return True if the Session State was changed, false otherwise.
	 */
	public boolean moveToNextSessionState(@NonNull RtspProtoMessageType messageType) {
		theWriteLock.lock();
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
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_nonSetup(@NonNull RtspProtoMessageType mt) {
		if (mt == RtspProtoMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot get Resource URL for UNKNOWN message type");
		}
		if (mt == RtspProtoMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot get Resource URL for SETUP message type");
		}
		theReadLock.lock();
		try {
			if (! this.resourceUrlPerMtMap_nonSetup.containsKey(mt)) {
				return Optional.empty();
			}
			return Optional.of(this.resourceUrlPerMtMap_nonSetup.get(mt).clone());
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_onlySetup(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getResourceUrlBySubStreamId(idSubStream);
		} finally {
			theReadLock.unlock();
		}
	}

	// ----------------------------------------------------

	public Optional<RtspProtoRscUrl> getLastUsedOutgoingRequestResourceUrl() {
		theReadLock.lock();
		try {
			if (lastUsedOutgoingRequestResourceUrlObj.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(lastUsedOutgoingRequestResourceUrlObj.clone());
		} finally {
			theReadLock.unlock();
		}
	}

	public Optional<RtspProtoMessageType> getLastUsedOutgoingRequestMsgType() {
		theReadLock.lock();
		try {
			if (lastUsedOutgoingRequestMsgType == RtspProtoMessageType.UNKNOWN) {
				return Optional.empty();
			}
			return Optional.of(lastUsedOutgoingRequestMsgType);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clearAfterTeardown() {
		theWriteLock.lock();
		try {
			boolean isRtsps = streamTpMain.getIsRtspsConnection();
			streamTpMain.clear();
			streamTpMain.setIsRtspsConnection(isRtsps);

			descrSetupInfosStream.clear();
			descrAvailableSubStreamIds.clear();
			rhDescribeSdpStcObj.clear();
			rhDescribeSdpStcIsSet = false;
			resourceUrlPerMtMap_nonSetup.clear();
			sessionState = RtspProtoSessionState.INIT;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------
	// ----------------------------------------------------

	@NonNull RtspProtoDataCntAuthSrv getPermAuthServer() {
		theReadLock.lock();
		try {
			return permAuthServer.clone();
		} finally {
			theReadLock.unlock();
		}
	}
	void setPermAuthServer(@NonNull RtspProtoDataCntAuthSrv value) {
		theWriteLock.lock();
		try {
			permAuthServer.copyFrom(value);
			permAuthServer.writeProtect();
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoDataCntStreamTpMain getStreamTpMain() {
		theReadLock.lock();
		try {
			RtspProtoDataCntStreamTpMain resObj = new RtspProtoDataCntStreamTpMain();
			resObj.copyFrom(streamTpMain);
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}
	void setStreamTpMainForceRtpRtcpEncryption() {
		theWriteLock.lock();
		try {
			streamTpMain.setForceRtpRtcpEncryption(true);
		} finally {
			theWriteLock.unlock();
		}
	}
	void setStreamTpMainRtpRtcpEncryptionRequired() {
		theWriteLock.lock();
		try {
			streamTpMain.setRtpRtcpEncryptionRequired(true);
		} finally {
			theWriteLock.unlock();
		}
	}
	void setStreamTpMainIsTransportUdp() {
		theWriteLock.lock();
		try {
			streamTpMain.setIsTransportUdp(true);
		} finally {
			theWriteLock.unlock();
		}
	}
	void setStreamTpMainIsTransportTcp() {
		theWriteLock.lock();
		try {
			streamTpMain.setIsTransportUdp(false);
		} finally {
			theWriteLock.unlock();
		}
	}
	void setStreamTpMainIsTransportSrtpSrtcp() {
		theWriteLock.lock();
		try {
			streamTpMain.setIsTransportSrtpSrtcp(true);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setSessionId(@NonNull RtspProtoIdSession value) {
		theWriteLock.lock();
		try {
			idSession.copyFrom(value);
			idSession.writeProtect();
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoCseqNr getCseqNr_requFromRem_lastRcvd() {
		theReadLock.lock();
		try {
			return cseqNr_requFromRem_lastRcvd.clone();
		} finally {
			theReadLock.unlock();
		}
	}
	void setCseqNr_requFromRem_lastRcvd(@NonNull RtspProtoCseqNr value) {
		theWriteLock.lock();
		try {
			cseqNr_requFromRem_lastRcvd.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	@NonNull RtspProtoCseqNr getCseqNr_requFromRem_expected() {
		theReadLock.lock();
		try {
			return cseqNr_requFromRem_expected.clone();
		} finally {
			theReadLock.unlock();
		}
	}
	void setCseqNr_requFromRem_expected(@NonNull RtspProtoCseqNr value) {
		theWriteLock.lock();
		try {
			cseqNr_requFromRem_expected.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	@NonNull RtspProtoCseqNr getCseqNr_requToRem_lastSent() {
		theReadLock.lock();
		try {
			return cseqNr_requToRem_lastSent.clone();
		} finally {
			theReadLock.unlock();
		}
	}
	void setCseqNr_requToRem_lastSent(@NonNull RtspProtoCseqNr value) {
		theWriteLock.lock();
		try {
			cseqNr_requToRem_lastSent.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setUnsupportedFeatureName(@NonNull String value) {
		theWriteLock.lock();
		try {
			unsupportedFeatureName = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhRequiredFeatures(@NonNull RtspProtoDataCntGetRequFeat value) {
		theWriteLock.lock();
		try {
			rhRequiredFeatures.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhProxyRequiredFeatures(@NonNull RtspProtoDataCntGetRequFeat value) {
		theWriteLock.lock();
		try {
			rhProxyRequiredFeatures.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhInvalidParamNames(@NonNull RtspProtoDataCntGetSetParamNames value) {
		theWriteLock.lock();
		try {
			rhInvalidParamNamesObj.copyFrom(value);
			rhInvalidParamNamesIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhGetParamNames(@NonNull RtspProtoDataCntGetSetParamNames value) {
		theWriteLock.lock();
		try {
			rhGetParamNamesObj.copyFrom(value);
			rhGetParamNamesIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}
	void clearRhGetParamNames() {
		theWriteLock.lock();
		try {
			rhGetParamNamesObj.clear();
			rhGetParamNamesIsSet = false;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhGetParamValues(@NonNull RtspProtoDataCntGetSetParamKvs value) {
		theWriteLock.lock();
		try {
			rhGetParamValuesObj.copyFrom(value);
			rhGetParamValuesIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhSetParamValues(@NonNull RtspProtoDataCntGetSetParamKvs setParamKvs) {
		theWriteLock.lock();
		try {
			rhSetParamValuesObj.copyFrom(setParamKvs);
			rhSetParamValuesIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}
	void clearRhSetParamValues() {
		theWriteLock.lock();
		try {
			rhSetParamValuesObj.clear();
			rhSetParamValuesIsSet = false;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setClientPlaybackRangeValue(@NonNull String value) {
		theWriteLock.lock();
		try {
			clientPlaybackRangeValue = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setServerPlaybackRangeValue(@NonNull String value) {
		theWriteLock.lock();
		try {
			serverPlaybackRangeValue = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setClientUserAgent(@NonNull String value) {
		theWriteLock.lock();
		try {
			clientUserAgent = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setServerSoftware(@NonNull String value) {
		theWriteLock.lock();
		try {
			serverSoftware = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		theReadLock.lock();
		try {
			return rtspProtoVersionToUse;
		} finally {
			theReadLock.unlock();
		}
	}
	void setRtspProtoVersionToUse(@NonNull RtspProtocolVersion value) {
		theWriteLock.lock();
		try {
			rtspProtoVersionToUse = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhSupportedMessageTypes(@NonNull RtspProtoDataCntMessageTypes value) {
		theWriteLock.lock();
		try {
			rhSupportedMessageTypes.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	@NonNull RtspProtoSetupInfosStream getDescrSetupInfosStream() {
		theReadLock.lock();
		try {
			RtspProtoSetupInfosStream resObj = new RtspProtoSetupInfosStream();
			resObj.copyFrom(descrSetupInfosStream);
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}
	void setDescrSetupInfosStream(@NonNull RtspProtoSetupInfosStream value) {
		theWriteLock.lock();
		try {
			descrSetupInfosStream.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}
	void setDescrSetupInfosForSubStream(@NonNull RtspProtoIdSubStream idSubStream, @NonNull RtspProtoSetupInfoForSubStream value) {
		theWriteLock.lock();
		try {
			descrSetupInfosStream.replaceSiForSubStream(idSubStream, value);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setDescrAvailableSubStreamIds(@NonNull Set<@NonNull RtspProtoIdSubStream> value) {
		theWriteLock.lock();
		try {
			descrAvailableSubStreamIds.clear();
			descrAvailableSubStreamIds.addAll(value);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhDescribeSdpStc(@NonNull RtspProtoDataCntSdpStructured sdpStructured) {
		theWriteLock.lock();
		try {
			rhDescribeSdpStcObj.copyFrom(sdpStructured);
			rhDescribeSdpStcIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}

	void setRhAnnouncedSdpStc(@NonNull RtspProtoDataCntSdpStructured sdpStructured) {
		theWriteLock.lock();
		try {
			rhAnnouncedSdpStcObj.copyFrom(sdpStructured);
			rhAnnouncedSdpStcIsSet = true;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setRhConnectionPolicy(@NonNull RtspConnectionPolicy connectionPolicy) {
		theWriteLock.lock();
		try {
			rhConnectionPolicy = connectionPolicy;
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void putResourceUrlForMt_nonSetup(@NonNull RtspProtoMessageType mt, @NonNull RtspProtoRscUrl rscUrl) {
		theWriteLock.lock();
		try {
			RtspProtoRscUrl tmpObj = rscUrl.clone();
			tmpObj.writeProtect();
			resourceUrlPerMtMap_nonSetup.put(mt, tmpObj);
		} finally {
			theWriteLock.unlock();
		}
	}

	// ----------------------------------------------------

	void setLastUsedOutgoingRequestResourceUrl(@NonNull RtspProtoRscUrl rscUrl) {
		theWriteLock.lock();
		try {
			lastUsedOutgoingRequestResourceUrlObj.copyFrom(rscUrl);
		} finally {
			theWriteLock.unlock();
		}
	}

	void setLastUsedOutgoingRequestMsgType(@NonNull RtspProtoMessageType requestMessageType) {
		theWriteLock.lock();
		try {
			lastUsedOutgoingRequestMsgType = requestMessageType;
		} finally {
			theWriteLock.unlock();
		}
	}

	@NonNull RtspProtoDataRequest getLastIncomingRequestData() {
		theReadLock.lock();
		try {
			RtspProtoDataRequest resObj = new RtspProtoDataRequest();
			resObj.copyFrom(lastIncomingRequestData);
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}
	void setLastIncomingRequestData(@NonNull RtspProtoDataRequest data) {
		theWriteLock.lock();
		try {
			lastIncomingRequestData.copyFrom(data);
		} finally {
			theWriteLock.unlock();
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
