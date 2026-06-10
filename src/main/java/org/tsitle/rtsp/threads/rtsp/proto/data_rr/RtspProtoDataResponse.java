package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

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
	private @NonNull String respServerIpFromRscUrl = "";

	/** Resource URL that the response belongs to */
	private @NonNull String respResourceUrl = "";

	/** Input Source ID */
	private final @NonNull RtspProtoIdInputSource respIdInputSource = new RtspProtoIdInputSource();

	/** Supported Message Types */
	public final @NonNull RtspProtoDataCntMessageTypes respSuppMessageTypes = new RtspProtoDataCntMessageTypes();

	/** RTSP protocol version */
	private @NonNull RtspProtocolVersion respRtspProtoVersionToUse = RtspProtocolVersion.NONE;

	/** Last received RTSP message Sequence Number in request */
	private long respCseqNrLastRcvd = -1L;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataResponse() { }

	public RtspProtoDataResponse(@NonNull RtspProtoDataRequest inputDataRequ) {
		copyFrom(inputDataRequ);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUnsupportedFeatureName() {
		return respUnsupportedFeatureName;
	}

	public @NonNull String getServerIpFromRscUrl() {
		return respServerIpFromRscUrl;
	}

	public @NonNull String getResourceUrl() {
		return respResourceUrl;
	}

	public @NonNull RtspProtoIdInputSource getIdInputSource() {
		return respIdInputSource.clone();
	}

	public @NonNull RtspProtocolVersion getRtspProtoVersionToUse() {
		return respRtspProtoVersionToUse;
	}

	public long getCseqNrLastRcvd() {
		return respCseqNrLastRcvd;
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
		respServerIpFromRscUrl = "";
		respResourceUrl = "";
		respIdInputSource.clear();
		respSuppMessageTypes.clear();
		respRtspProtoVersionToUse = RtspProtocolVersion.NONE;
		respCseqNrLastRcvd = -1L;
	}

	public void copyFrom(@NonNull RtspProtoDataRequest inputDataRequ) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		respIdSession.copyFrom(inputDataRequ.requIdSession);
		respGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		respInvalidParamNames.copyFrom(inputDataRequ.requInvalidParamNames);
		respUnsupportedFeatureName = inputDataRequ.getUnsupportedFeatureName();
		respResourceUrl = inputDataRequ.getResourceUrl();
		respServerIpFromRscUrl = inputDataRequ.getServerIpFromRscUrl();
		respIdInputSource.copyFrom(inputDataRequ.getIdInputSource());
		respRtspProtoVersionToUse = inputDataRequ.getRtspProtoVersionToUse();
		respCseqNrLastRcvd = inputDataRequ.getCseqNrLastRcvd();
	}

	public void writeProtect() {
		isWriteProtected = true;

		respIdSession.writeProtect();
		respAuthServer.writeProtect();
		respGetParamNames.writeProtect();
		respGetParamValues.writeProtect();
		respInvalidParamNames.writeProtect();
		respDescribeSdp.writeProtect();
		respIdInputSource.writeProtect();
		respSuppMessageTypes.writeProtect();
	}

}
