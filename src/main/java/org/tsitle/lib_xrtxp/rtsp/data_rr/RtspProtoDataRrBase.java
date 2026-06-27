package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoCseqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;

public class RtspProtoDataRrBase {

	protected boolean isWriteProtected = false;

	/** RTSP Session ID */
	public final @NonNull RtspProtoIdSession rrIdSession = RtspProtoIdSession.ofEmpty();

	/** RTSP message parameters to get */
	public final @NonNull RtspProtoDataCntGetSetParamNames rrGetParamNames = new RtspProtoDataCntGetSetParamNames();
	/** RTSP message parameters that are not supported */
	public final @NonNull RtspProtoDataCntGetSetParamNames rrInvalidParamNames = new RtspProtoDataCntGetSetParamNames();

	/** Name of an unsupported feature that has been requested */
	protected @NonNull String rrUnsupportedFeatureName = "";

	/** Server IP address as resolved from Resource URL */
	public final @NonNull RtspProtoIpAddr rrServerIpFromRscUrl = new RtspProtoIpAddr();

	/** Resource URL that the request/response belongs to */
	public final @NonNull RtspProtoRscUrl rrRscUrl = RtspProtoRscUrl.ofEmpty();

	/** RTSP protocol version */
	protected @NonNull RtspProtocolVersion rrRtspProtoVersionToUse = RtspProtocolVersion.NONE;

	/** Last received RTSP message Sequence Number in request */
	public final @NonNull RtspProtoCseqNr rrCseqNrLastRcvd = RtspProtoCseqNr.ofEmpty();

	/** Main transport parameters */
	public final @NonNull RtspProtoDataCntStreamTpMain rrStreamTpMain = new RtspProtoDataCntStreamTpMain();

	/** Client's Useragent */
	protected @NonNull String rrClientUa = "";
	/** Server's software name and version */
	protected @NonNull String rrServerSoftware = "";

	/** Client's IP address */
	public final @NonNull RtspProtoIpAddr rrClientIpAddr = new RtspProtoIpAddr();

	/** Playback range value */
	protected @NonNull String rrPlaybackRangeValue = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected RtspProtoDataRrBase() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUnsupportedFeatureName() {
		return rrUnsupportedFeatureName;
	}
	public void setUnsupportedFeatureName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrUnsupportedFeatureName = value;
	}

	public @NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		return rrRtspProtoVersionToUse;
	}

	public @NonNull String getClientUa() {
		return rrClientUa;
	}
	public void setClientUa(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrClientUa = value;
	}

	public @NonNull String getServerSoftware() {
		return rrServerSoftware;
	}
	public void setServerSoftware(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrServerSoftware = value;
	}

	public @NonNull String getPlaybackRangeValue() {
		return rrPlaybackRangeValue;
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void copyFrom(@NonNull RtspProtoDataRrBase other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		rrIdSession.copyFrom(other.rrIdSession);
		rrGetParamNames.copyFrom(other.rrGetParamNames);
		rrInvalidParamNames.copyFrom(other.rrInvalidParamNames);
		rrUnsupportedFeatureName = other.rrUnsupportedFeatureName;
		rrServerIpFromRscUrl.copyFrom(other.rrServerIpFromRscUrl);
		rrRscUrl.copyFrom(other.rrRscUrl);
		rrRtspProtoVersionToUse = other.rrRtspProtoVersionToUse;
		rrCseqNrLastRcvd.copyFrom(other.rrCseqNrLastRcvd);
		rrStreamTpMain.copyFrom(other.rrStreamTpMain);
		rrClientUa = other.rrClientUa;
		rrServerSoftware = other.rrServerSoftware;
		rrClientIpAddr.copyFrom(other.rrClientIpAddr);
		rrPlaybackRangeValue = other.rrPlaybackRangeValue;
	}

	protected void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		rrIdSession.clear();
		rrGetParamNames.clear();
		rrInvalidParamNames.clear();
		rrUnsupportedFeatureName = "";
		rrServerIpFromRscUrl.clear();
		rrRscUrl.clear();
		rrRtspProtoVersionToUse = RtspProtocolVersion.NONE;
		rrCseqNrLastRcvd.clear();
		rrStreamTpMain.clear();
		rrClientUa = "";
		rrServerSoftware = "";
		rrClientIpAddr.clear();
		rrPlaybackRangeValue = "";
	}

	protected void writeProtect() {
		isWriteProtected = true;

		rrIdSession.writeProtect();
		rrGetParamNames.writeProtect();
		rrInvalidParamNames.writeProtect();
		rrServerIpFromRscUrl.writeProtect();
		rrRscUrl.writeProtect();
		rrCseqNrLastRcvd.writeProtect();
		rrStreamTpMain.writeProtect();
		rrClientIpAddr.writeProtect();
	}

}
