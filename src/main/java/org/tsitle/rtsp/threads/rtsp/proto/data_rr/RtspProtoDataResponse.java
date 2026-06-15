package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRscUrl;

public final class RtspProtoDataResponse {

	private boolean isWriteProtected = false;

	/** RTSP Session ID */
	public final @NonNull RtspProtoIdSession respIdSession = new RtspProtoIdSession();

	/** Authentication-related info from the server */
	public final @NonNull RtspProtoDataCntAuthSrv respAuthServer = new RtspProtoDataCntAuthSrv();

	/** RTSP message parameters to get */
	public final @NonNull RtspProtoDataCntGetSetParamNames respGetParamNames = new RtspProtoDataCntGetSetParamNames();
	/** RTSP message parameters */
	public final @NonNull RtspProtoDataCntGetSetParamKvs respGetParamValues = new RtspProtoDataCntGetSetParamKvs();
	/** RTSP message parameters that are not supported */
	public final @NonNull RtspProtoDataCntGetSetParamNames respInvalidParamNames = new RtspProtoDataCntGetSetParamNames();
	/** Announced SDP */
	public final @NonNull RtspProtoDataCntSdp respDescribeSdp = new RtspProtoDataCntSdp();

	/** Name of an unsupported feature that has been requested */
	private @NonNull String respUnsupportedFeatureName = "";

	/** Server IP address as resolved from Resource URL */
	public final @NonNull RtspProtoIpAddr respServerIpFromRscUrl = new RtspProtoIpAddr();

	/** Resource URL that the response belongs to */
	public final @NonNull RtspProtoRscUrl respRscUrl = new RtspProtoRscUrl();

	/** Supported Message Types */
	public final @NonNull RtspProtoDataCntMessageTypes respSuppMessageTypes = new RtspProtoDataCntMessageTypes();

	/** RTSP protocol version */
	private @NonNull RtspProtocolVersion respRtspProtoVersionToUse = RtspProtocolVersion.NONE;

	/** Last received RTSP message Sequence Number in request */
	public final @NonNull RtspProtoCseqNr respCseqNrLastRcvd = new RtspProtoCseqNr();

	/** Main transport parameters */
	public final @NonNull RtspProtoDataCntStreamTpMain respStreamTpMain = new RtspProtoDataCntStreamTpMain();

	/** Client's Useragent */
	private @NonNull String respClientUa = "";

	/** Client's IP address */
	public final @NonNull RtspProtoIpAddr respClientIpAddr = new RtspProtoIpAddr();

	/** Playback range value */
	private @NonNull String respPlaybackRangeValue = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataResponse() { }

	public RtspProtoDataResponse(@NonNull RtspProtoDataRequest inputDataRequ) {
		respIdSession.copyFrom(inputDataRequ.requIdSession);
		respGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		respInvalidParamNames.copyFrom(inputDataRequ.requInvalidParamNames);
		respUnsupportedFeatureName = inputDataRequ.getUnsupportedFeatureName();
		respRscUrl.copyFrom(inputDataRequ.requRscUrl);
		respServerIpFromRscUrl.copyFrom(inputDataRequ.requServerIpFromRscUrl);
		respRtspProtoVersionToUse = inputDataRequ.getRtspProtoVersionToUse();
		respCseqNrLastRcvd.copyFrom(inputDataRequ.getCseqNrLastRcvd());
		respStreamTpMain.copyFrom(inputDataRequ.requStreamTpMain);
		respClientUa = inputDataRequ.getClientUa();
		respClientIpAddr.copyFrom(inputDataRequ.requClientIpAddr);
		respPlaybackRangeValue = inputDataRequ.getPlaybackRangeValue();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUnsupportedFeatureName() {
		return respUnsupportedFeatureName;
	}

	public @NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		return respRtspProtoVersionToUse;
	}

	public @NonNull String getClientUa() {
		return respClientUa;
	}

	public @NonNull String getPlaybackRangeValue() {
		return respPlaybackRangeValue;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		respIdSession.clear();
		respAuthServer.clear();
		respGetParamNames.clear();
		respGetParamValues.clear();
		respInvalidParamNames.clear();
		respDescribeSdp.clear();
		respUnsupportedFeatureName = "";
		respServerIpFromRscUrl.clear();
		respRscUrl.clear();
		respSuppMessageTypes.clear();
		respRtspProtoVersionToUse = RtspProtocolVersion.NONE;
		respCseqNrLastRcvd.clear();
		respStreamTpMain.clear();
		respClientUa = "";
		respClientIpAddr.clear();
		respPlaybackRangeValue = "";
	}

	public void writeProtect() {
		isWriteProtected = true;

		respIdSession.writeProtect();
		respAuthServer.writeProtect();
		respGetParamNames.writeProtect();
		respGetParamValues.writeProtect();
		respInvalidParamNames.writeProtect();
		respDescribeSdp.writeProtect();
		respServerIpFromRscUrl.writeProtect();
		respRscUrl.writeProtect();
		respSuppMessageTypes.writeProtect();
		respCseqNrLastRcvd.writeProtect();
		respStreamTpMain.writeProtect();
		respClientIpAddr.writeProtect();
	}

}
