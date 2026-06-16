package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthSrv;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntStreamTpMain;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoSessionState;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoSessionInfoException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.*;

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
	private final @NonNull RtspProtoIdSession idSession = new RtspProtoIdSession();

	/** Request from remote host: Last received RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requFromRem_lastRcvd = new RtspProtoCseqNr();
	/** Request from remote host: Expected RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requFromRem_expected = new RtspProtoCseqNr(0L);
	/** Request to remote host: Last sent RTSP message Sequence Number */
	private final @NonNull RtspProtoCseqNr cseqNr_requToRem_lastSent = new RtspProtoCseqNr(0L);

	/** Playback range request value from the client */
	private @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version to be used */
	private @NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** Client's User-Agent */
	private @NonNull String clientUserAgent = "";

	/** RTSP message types that are supported by the remote host */
	private final @NonNull RtspProtoDataCntMessageTypes rhSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	// ----------------------------------------------------------------

	/** Stream info from DESCRIBE/SETUP responses */
	private final @NonNull RtspProtoSetupInfosStream descrSetupInfosStream = new RtspProtoSetupInfosStream();

	// ----------------------------------------------------------------

	/** Current state of the RTSP session */
	private @NonNull RtspProtoSessionState sessionState = RtspProtoSessionState.INIT;

	// ----------------------------------------------------------------

	/** Resource URL per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspProtoMessageType, @NonNull RtspProtoRscUrl> resourceUrlPerMtMap_nonSetup = new ConcurrentHashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getPermAuthServerRealm() {
		theReadLock.lock();
		try {
			return permAuthServer.getAuthRealm();
		} finally {
			theReadLock.unlock();
		}
	}

	public @NonNull String getPermAuthServerNonce() {
		theReadLock.lock();
		try {
			return permAuthServer.getAuthNonce();
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

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

	// -----------------------------------------------------------------------------------------------------------------

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

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdSession getIdSession() {
		theReadLock.lock();
		try {
			return idSession.clone();
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull String getClientPlaybackRangeValue() {
		theReadLock.lock();
		try {
			return clientPlaybackRangeValue;
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Set<RtspProtoIdStreamSource> getDescrSetupInfoStreamSourceIds() {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getStreamSourceIds();
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
			RtspProtoSetupInfoForSubStream tmpSi = descrSetupInfosStream.getSiBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" + idSubStream.getIdStr() + "'")
				);
			RtspProtoSetupInfoForSubStream resObj = tmpSi.clone();
			resObj.writeProtect();
			return resObj;
		} finally {
			theReadLock.unlock();
		}
	}

	public @NonNull RtspProtoIdXsrc getDescrSetupInfoSsrcBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		theReadLock.lock();
		try {
			return descrSetupInfosStream.getSsrcBySubStreamId(idSubStream).orElseThrow(() ->
					new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" + idSubStream.getIdStr() + "'")
				);
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
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = descrSetupInfosStream.getSiBySubStreamId(idSubStream);
			if (tmpOptSiSs.isEmpty()) {
				return Optional.empty();
			}
			return tmpOptSiSs.get().getKmdInboundNextPtr().getKmd();
		} finally {
			theReadLock.unlock();
		}
	}

	public void clearDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		theWriteLock.lock();
		try {
			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = descrSetupInfosStream.getSiBySubStreamId(idSubStream);
			if (tmpOptSiSs.isEmpty()) {
				return;
			}
			tmpOptSiSs.get().getKmdInboundNextPtr().clear();
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

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

	// -----------------------------------------------------------------------------------------------------------------

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

	// -----------------------------------------------------------------------------------------------------------------

	public void clearAfterTeardown() {
		theWriteLock.lock();
		try {
			boolean isRtsps = streamTpMain.getIsRtspsConnection();
			streamTpMain.clear();
			streamTpMain.setIsRtspsConnection(isRtsps);

			descrSetupInfosStream.clear();
			resourceUrlPerMtMap_nonSetup.clear();
			sessionState = RtspProtoSessionState.INIT;
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
	void setStreamTpMainIsTransportSrtpSrtcp() {
		theWriteLock.lock();
		try {
			streamTpMain.setIsTransportSrtpSrtcp(true);
		} finally {
			theWriteLock.unlock();
		}
	}

	void setSessionId(@NonNull RtspProtoIdSession value) {
		theWriteLock.lock();
		try {
			idSession.copyFrom(value);
			idSession.writeProtect();
		} finally {
			theWriteLock.unlock();
		}
	}

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

	void setClientPlaybackRangeValue(@NonNull String value) {
		theWriteLock.lock();
		try {
			clientPlaybackRangeValue = value;
		} finally {
			theWriteLock.unlock();
		}
	}

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

	@NonNull String getClientUserAgent() {
		theReadLock.lock();
		try {
			return clientUserAgent;
		} finally {
			theReadLock.unlock();
		}
	}
	void setClientUserAgent(@NonNull String value) {
		theWriteLock.lock();
		try {
			clientUserAgent = value;
		} finally {
			theWriteLock.unlock();
		}
	}

	@NonNull RtspProtoDataCntMessageTypes getRhSupportedMessageTypes() {
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
	void setRhSupportedMessageTypes(@NonNull RtspProtoDataCntMessageTypes value) {
		theWriteLock.lock();
		try {
			rhSupportedMessageTypes.copyFrom(value);
		} finally {
			theWriteLock.unlock();
		}
	}

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

			RtspProtoIpAddr resObj = new RtspProtoIpAddr();
			resObj.setIpAddr(optRtspHostIp.get());
			resObj.writeProtect();
			return resObj;
		} catch (UnknownHostException | SocketException e) {
			throw new RtspProtoCannotFindIpFromRscUrlException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

}
