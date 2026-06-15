package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
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
	private long requCseqNrLastRcvd = -1L;

	/** RTSP message Sequence Number to use for sending a request */
	private long requCseqNrToSend = 0L;

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
		this.requCseqNrLastRcvd = other.requCseqNrLastRcvd;
		this.requCseqNrToSend = other.requCseqNrToSend;
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

	public long getCseqNrLastRcvd() {
		return requCseqNrLastRcvd;
	}
	public void setCseqNrLastRcvd(long value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requCseqNrLastRcvd = value;
	}

	public long getCseqNrToSend() {
		return requCseqNrToSend;
	}
	public void setCseqNrToSend(long value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requCseqNrToSend = value;
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
		requCseqNrLastRcvd = -1L;
		requCseqNrToSend = 0L;
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
		requRscUrl.writeProtect();
		requStreamTpMain.writeProtect();
		requRtspSessionState.writeProtect();
	}

}
