package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;

public final class RtspProtoDataRequest {

	private boolean isWriteProtected = false;

	/** RTSP Session ID */
	public final @NonNull RtspProtoIdSession requIdSession = new RtspProtoIdSession();

	/** Authentication-related info from the client */
	public final @NonNull RtspProtoDataCntAuthClient requAuthClient = new RtspProtoDataCntAuthClient();

	/** RTSP message parameters to get */
	public final @NonNull RtspProtoDataCntGetSetParamNames requGetParamNames = new RtspProtoDataCntGetSetParamNames();
	/** RTSP message parameters to set */
	public final @NonNull RtspProtoDataCntGetSetParamKvs requSetParamValues = new RtspProtoDataCntGetSetParamKvs();
	/** RTSP message parameters that are not supported */
	public final @NonNull RtspProtoDataCntGetSetParamNames requInvalidParamNames = new RtspProtoDataCntGetSetParamNames();
	/** Announced SDP */
	public final @NonNull RtspProtoDataCntSdp requAnnouncedSdp = new RtspProtoDataCntSdp();

	/** Name of an unsupported feature that has been requested */
	private @NonNull String requUnsupportedFeatureName = "";

	/** Server IP address as resolved from Resource URL */
	public final @NonNull RtspProtoIpAddr requServerIpFromRscUrl = new RtspProtoIpAddr();

	/** Resource URL that the request was made for */
	public final @NonNull RtspProtoRscUrl requRscUrl = new RtspProtoRscUrl();

	/** RTSP protocol version */
	private @NonNull RtspProtocolVersion requRtspProtoVersionToUse = RtspProtocolVersion.NONE;

	/** Last received RTSP message Sequence Number in request */
	private final @NonNull RtspProtoCseqNr requCseqNrLastRcvd = RtspProtoCseqNr.ofEmpty();

	/** RTSP message Sequence Number to use for sending a request */
	private final @NonNull RtspProtoCseqNr requCseqNrToSend = RtspProtoCseqNr.ofZero();

	/** Main transport parameters */
	public final @NonNull RtspProtoDataCntStreamTpMain requStreamTpMain = new RtspProtoDataCntStreamTpMain();

	/** Client's Useragent */
	private @NonNull String requClientUa = "";

	/** Client's IP address */
	public final @NonNull RtspProtoIpAddr requClientIpAddr = new RtspProtoIpAddr();

	/** RTSP session state */
	public final @NonNull RtspProtoDataCntSessionState requRtspSessionState = new RtspProtoDataCntSessionState();

	/** Playback range value */
	private @NonNull String requPlaybackRangeValue = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataRequest() { }

	public RtspProtoDataRequest(@NonNull RtspProtoDataRequest other) {
		this.isWriteProtected = other.isWriteProtected;  // only for the linter
		if (this.isWriteProtected) {
			this.isWriteProtected = false;
		}
		this.requIdSession.copyFrom(other.requIdSession);
		this.requAuthClient.copyFrom(other.requAuthClient);
		this.requGetParamNames.copyFrom(other.requGetParamNames);
		this.requSetParamValues.copyFrom(other.requSetParamValues);
		this.requInvalidParamNames.copyFrom(other.requInvalidParamNames);
		this.requAnnouncedSdp.copyFrom(other.requAnnouncedSdp);
		this.requUnsupportedFeatureName = other.requUnsupportedFeatureName;
		this.requServerIpFromRscUrl.copyFrom(other.requServerIpFromRscUrl);
		this.requRscUrl.copyFrom(other.requRscUrl);
		this.requRtspProtoVersionToUse = other.requRtspProtoVersionToUse;
		this.requCseqNrLastRcvd.copyFrom(other.requCseqNrLastRcvd);
		this.requCseqNrToSend.copyFrom(other.requCseqNrToSend);
		this.requStreamTpMain.copyFrom(other.requStreamTpMain);
		this.requClientUa = other.requClientUa;
		this.requClientIpAddr.copyFrom(other.requClientIpAddr);
		this.requRtspSessionState.copyFrom(other.requRtspSessionState);
		this.requPlaybackRangeValue = other.requPlaybackRangeValue;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUnsupportedFeatureName() {
		return requUnsupportedFeatureName;
	}
	public void setUnsupportedFeatureName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requUnsupportedFeatureName = value;
	}

	public @NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		return requRtspProtoVersionToUse;
	}
	public void setRtspProtoVersionToUse(@NonNull RtspProtocolVersion value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requRtspProtoVersionToUse = value;
	}

	public @NonNull RtspProtoCseqNr getCseqNrLastRcvd() {
		return requCseqNrLastRcvd.clone();
	}
	public void setCseqNrLastRcvd(@NonNull RtspProtoCseqNr value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requCseqNrLastRcvd.copyFrom(value);
	}

	public @NonNull RtspProtoCseqNr getCseqNrToSend() {
		return requCseqNrToSend.clone();
	}
	public void copyAndIncrementCseqNrToSend(@NonNull RtspProtoCseqNr value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		long tmpVal = value.getCseq32bit().orElse(-1L) + 1L;
		try {
			this.requCseqNrToSend.setCseq32bit(tmpVal);
		} catch (RtspProtoNumberRangeException e) {
			// overflow
			try {
				this.requCseqNrToSend.setCseq32bit(0L);
			} catch (RtspProtoNumberRangeException ex) {
				// this will never happen
			}
		}
	}

	public @NonNull String getClientUa() {
		return requClientUa;
	}
	public void setClientUa(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requClientUa = value;
	}

	public @NonNull String getPlaybackRangeValue() {
		return requPlaybackRangeValue;
	}
	public void setPlaybackRangeValue(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requPlaybackRangeValue = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		requIdSession.clear();
		requAuthClient.clear();
		requGetParamNames.clear();
		requSetParamValues.clear();
		requInvalidParamNames.clear();
		requAnnouncedSdp.clear();
		requUnsupportedFeatureName = "";
		requServerIpFromRscUrl.clear();
		requRscUrl.clear();
		requRtspProtoVersionToUse = RtspProtocolVersion.NONE;
		requCseqNrLastRcvd.clear();
		requCseqNrToSend.clear();
		try {
			requCseqNrToSend.setCseq32bit(0L);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		requStreamTpMain.clear();
		requClientUa = "";
		requClientIpAddr.clear();
		requRtspSessionState.clear();
		requPlaybackRangeValue = "";
	}

	public void writeProtect() {
		isWriteProtected = true;

		requIdSession.writeProtect();
		requAuthClient.writeProtect();
		requGetParamNames.writeProtect();
		requSetParamValues.writeProtect();
		requInvalidParamNames.writeProtect();
		requAnnouncedSdp.writeProtect();
		requServerIpFromRscUrl.writeProtect();
		requRscUrl.writeProtect();
		requCseqNrLastRcvd.writeProtect();
		requCseqNrToSend.writeProtect();
		requStreamTpMain.writeProtect();
		requClientIpAddr.writeProtect();
		requRtspSessionState.writeProtect();
	}

}
