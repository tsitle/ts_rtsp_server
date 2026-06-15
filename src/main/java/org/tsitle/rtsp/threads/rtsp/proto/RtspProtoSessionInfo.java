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
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfosStream;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RtspProtoSessionInfo {

	/** Authentication-related info from the server */
	final @NonNull RtspProtoDataCntAuthSrv permAuthServer = new RtspProtoDataCntAuthSrv();

	/** Client IP address */
	final @NonNull RtspProtoIpAddr clientIpAddr = new RtspProtoIpAddr();

	/** Main stream transport info */
	final @NonNull RtspProtoDataCntStreamTpMain streamTpMain = new RtspProtoDataCntStreamTpMain();
	private boolean haveSetIsRtspsConnection = false;

	/** RTSP Session ID */
	final @NonNull RtspProtoIdSession idSession = new RtspProtoIdSession();

	/** Request from remote host: Last received RTSP message Sequence Number */
	long seqNr_requFromRem_lastRcvd = -1L;
	/** Request from remote host: Expected RTSP message Sequence Number */
	long seqNr_requFromRem_expected = 0L;
	/** Request to remote host: Last sent RTSP message Sequence Number */
	long seqNr_requToRem_lastSent = 0L;

	/** Playback range request value from the client */
	@NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version to be used */
	@NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** Client's User-Agent */
	@NonNull String clientUserAgent = "";

	/** RTSP message types that are supported by the remote host */
	final @NonNull RtspProtoDataCntMessageTypes rhSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	// ----------------------------------------------------------------

	/** Stream info from DESCRIBE/SETUP responses */
	final @NonNull RtspProtoSetupInfosStream descrSetupInfosStream = new RtspProtoSetupInfosStream();

	// ----------------------------------------------------------------

	/** Current state of the RTSP session */
	@NonNull RtspProtoSessionState sessionState = RtspProtoSessionState.INIT;

	// ----------------------------------------------------------------

	/** Resource URL per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspProtoMessageType, @NonNull RtspProtoRscUrl> resourceUrlPerMtMap_nonSetup = new ConcurrentHashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getPermAuthServerRealm() {
		return permAuthServer.getAuthRealm();
	}

	public @NonNull String getPermAuthServerNonce() {
		return permAuthServer.getAuthNonce();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIpAddr getClientIpAddr() {
		return clientIpAddr.clone();
	}
	public void setClientIpAddr(@NonNull RtspProtoIpAddr value) throws RtspProtoSessionInfoException {
		if (clientIpAddr.equals(value)) {
			return;
		}
		if (! clientIpAddr.isEmpty()) {
			throw new RtspProtoSessionInfoException("Client IP address already set to '" + clientIpAddr.getIpAddrStr().orElseThrow() +
					"' (attempted to change it to '" + value.getIpAddrStr().orElseThrow() + "')");
		}
		clientIpAddr.copyFrom(value);
		clientIpAddr.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean getIsRtspsConnection() {
		return streamTpMain.getIsRtspsConnection();
	}
	public void setIsRtspsConnection(boolean value) throws RtspProtoSessionInfoException {
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
	}

	public boolean getIsTransportSrtpSrtcp() {
		return streamTpMain.getIsTransportSrtpSrtcp();
	}

	public boolean getIsTransportUdp() {
		return streamTpMain.getIsTransportUdp();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdSession getIdSession() {
		return idSession.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Set<RtspProtoIdStreamSource> getDescrSetupInfoStreamSourceIds() {
		return descrSetupInfosStream.getStreamSourceIds();
	}

	public @NonNull Set<RtspProtoRscUrl> getDescrSetupInfoRscUrls() {
		return descrSetupInfosStream.getRscUrls();
	}

	public @NonNull RtspProtoSetupInfoForSubStream getDescrSetupInfoBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		RtspProtoSetupInfoForSubStream tmpSi = descrSetupInfosStream.getSiBySubStreamId(idSubStream).orElseThrow(() ->
				new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" + idSubStream.getIdStr() + "'")
			);
		RtspProtoSetupInfoForSubStream resObj = tmpSi.clone();
		resObj.writeProtect();
		return resObj;
	}

	public int getDescrSetupInfoSsrcBySubStreamsId(@NonNull RtspProtoIdSubStream idSubStream)
			throws RtspProtoSessionInfoException {
		return descrSetupInfosStream.getSsrcBySubStreamId(idSubStream).orElseThrow(() ->
				new RtspProtoSessionInfoException("No Stream Info found for Sub-Stream ID='" + idSubStream.getIdStr() + "'")
			);
	}

	public boolean getDescrSetupInfoHaveSetupForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		return descrSetupInfosStream.haveSetupForSubStreamId(idSubStream);
	}

	public Optional<SrtxpKmd> getDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = descrSetupInfosStream.getSiBySubStreamId(idSubStream);
		if (tmpOptSiSs.isEmpty()) {
			return Optional.empty();
		}
		return tmpOptSiSs.get().getKmdInboundNextPtr().getKmd();
	}

	public void clearDescrSetupInfoNextKmdInboundForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		Optional<RtspProtoSetupInfoForSubStream> tmpOptSiSs = descrSetupInfosStream.getSiBySubStreamId(idSubStream);
		if (tmpOptSiSs.isEmpty()) {
			return;
		}
		tmpOptSiSs.get().getKmdInboundNextPtr().clear();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoSessionState getSessionState() {
		return sessionState;
	}

	/**
	 * Moves to the next Session State after a successful request based on the given message type.
	 * @param messageType The type of the last successful request.
	 * @return True if the Session State was changed, false otherwise.
	 */
	public boolean moveToNextSessionState(@NonNull RtspProtoMessageType messageType) {
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
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clearAfterTeardown() {
		boolean isRtsps = streamTpMain.getIsRtspsConnection();
		streamTpMain.clear();
		streamTpMain.setIsRtspsConnection(isRtsps);

		descrSetupInfosStream.clear();
		resourceUrlPerMtMap_nonSetup.clear();
		sessionState = RtspProtoSessionState.INIT;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void putResourceUrlForMt_nonSetup(@NonNull RtspProtoMessageType mt, @NonNull RtspProtoRscUrl rscUrl) {
		RtspProtoRscUrl tmpObj = rscUrl.clone();
		tmpObj.writeProtect();
		resourceUrlPerMtMap_nonSetup.put(mt, tmpObj);
	}

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_nonSetup(@NonNull RtspProtoMessageType mt) {
		if (mt == RtspProtoMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot get Resource URL for UNKNOWN message type");
		}
		if (mt == RtspProtoMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot get Resource URL for SETUP message type");
		}
		return Optional.ofNullable(this.resourceUrlPerMtMap_nonSetup.get(mt));
	}

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_onlySetup(@NonNull RtspProtoIdSubStream idSubStream) {
		return descrSetupInfosStream.getResourceUrlBySubStreamId(idSubStream);
	}

	@NonNull RtspProtoIpAddr findRtspIpFromResourceUrl(@NonNull RtspProtoRscUrl rscUrl)
			throws RtspProtoCannotFindIpFromRscUrlException {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspIpFromResourceUrl()";

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
