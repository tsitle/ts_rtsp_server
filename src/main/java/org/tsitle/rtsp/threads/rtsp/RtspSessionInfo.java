package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAuthSrv;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntStreamTpMain;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspSessionState;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfosStream;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RtspSessionInfo {

	public final static class PermDataCntAuthClient {
		/** Authentication credentials: username */
		public @NonNull String authUser = "";
		/** Authentication credentials: password */
		public @NonNull String authPlainPassword = "";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Authentication-related info from the server */
	public final @NonNull RtspProtoDataCntAuthSrv permAuthServer = new RtspProtoDataCntAuthSrv();
	/** Authentication-related info for the client */
	public final @NonNull PermDataCntAuthClient permAuthClient = new PermDataCntAuthClient();

	/** Client IP address */
	public final @NonNull RtspProtoIpAddr clientIpAddr = new RtspProtoIpAddr();

	/** Main stream transport info */
	public @NonNull RtspProtoDataCntStreamTpMain streamTpMain = new RtspProtoDataCntStreamTpMain();

	/** RTSP Session ID */
	public @NonNull RtspProtoIdSession idSession = new RtspProtoIdSession();

	/** Request from remote host: Last received RTSP message Sequence Number */
	public long seqNr_requFromRem_lastRcvd = -1L;
	/** Request from remote host: Expected RTSP message Sequence Number */
	public long seqNr_requFromRem_expected = 0L;
	/** Request to remote host: Last sent RTSP message Sequence Number */
	public long seqNr_requToRem_lastSent = 0L;

	/** Playback range request value from the client */
	public @NonNull String clientPlaybackRangeValue = "";

	/** RTSP protocol version to be used */
	public @NonNull RtspProtocolVersion rtspProtoVersionToUse = RtspProtoLowMsgConstants.DEFAULT_RTSP_PROTO_VERSION;

	/** Client's User-Agent */
	public @NonNull String clientUserAgent = "";

	/** RTSP message types that are supported by the remote host */
	public final @NonNull RtspProtoDataCntMessageTypes rhSupportedMessageTypes = new RtspProtoDataCntMessageTypes();

	// ----------------------------------------------------------------

	/** Stream info from DESCRIBE/SETUP responses */
	public final @NonNull RtspProtoSetupInfosStream descrSetupInfosStream = new RtspProtoSetupInfosStream();

	// ----------------------------------------------------------------

	/** Resource URL per DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN/... request */
	private final @NonNull Map<@NonNull RtspMessageType, @NonNull RtspProtoRscUrl> resourceUrlPerMtMap_nonSetup = new ConcurrentHashMap<>();

	// ----------------------------------------------------------------

	/** Current state of the RTSP session */
	public @NonNull RtspSessionState sessionState = RtspSessionState.INIT;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void putResourceUrlForMt_nonSetup(@NonNull RtspMessageType mt, @NonNull RtspProtoRscUrl rscUrl) {
		RtspProtoRscUrl tmpObj = rscUrl.clone();
		tmpObj.writeProtect();
		resourceUrlPerMtMap_nonSetup.put(mt, tmpObj);
	}

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_nonSetup(@NonNull RtspMessageType mt) {
		if (mt == RtspMessageType.UNKNOWN) {
			throw new IllegalArgumentException("Cannot get Resource URL for UNKNOWN message type");
		}
		if (mt == RtspMessageType.SETUP) {
			throw new IllegalArgumentException("Cannot get Resource URL for SETUP message type");
		}
		return Optional.ofNullable(this.resourceUrlPerMtMap_nonSetup.get(mt));
	}

	public Optional<RtspProtoRscUrl> getResourceUrlForMt_onlySetup(@NonNull RtspProtoIdSubStream idSubStream) {
		return descrSetupInfosStream.getResourceUrlBySubStreamId(idSubStream);
	}

	public @NonNull RtspProtoIpAddr findRtspIpFromResourceUrl(@NonNull RtspProtoRscUrl rscUrl)
			throws RtspCannotFindIpFromRscUrlException {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspIpFromResourceUrl()";

		if (rscUrl.getUrlStr().isBlank()) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": No Resource URL set");
		}
		String tmpRtspHostname;
		try {
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(rscUrl.getUrlStr());
			tmpRtspHostname = rscUriObj.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not parse URL: " + e.getMessage());
		}
		if (tmpRtspHostname.isBlank()) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(
					tmpRtspHostname,
					true
				);
			if (optRtspHostIp.isEmpty()) {
				throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" +
						tmpRtspHostname + "'");
			}

			RtspProtoIpAddr resObj = new RtspProtoIpAddr();
			resObj.setIpAddr(optRtspHostIp.get());
			resObj.writeProtect();
			return resObj;
		} catch (UnknownHostException | SocketException e) {
			throw new RtspCannotFindIpFromRscUrlException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clearAfterTeardown() {
		descrSetupInfosStream.clear();
	}

}
