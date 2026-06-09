package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdSession;

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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataResponse() { }

	public RtspProtoDataResponse(@NonNull RtspProtoDataRequest inputDataRequ) {
		copyFrom(inputDataRequ);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getRespUnsupportedFeatureName() {
		return respUnsupportedFeatureName;
	}

	public void setRespUnsupportedFeatureName(@NonNull String featureName) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.respUnsupportedFeatureName = featureName;
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
	}

	public void copyFrom(@NonNull RtspProtoDataRequest inputDataRequ) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		respIdSession.copyFrom(inputDataRequ.requIdSession);
		respGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		respInvalidParamNames.copyFrom(inputDataRequ.requInvalidParamNames);
		respUnsupportedFeatureName = inputDataRequ.getRequUnsupportedFeatureName();
	}

	public void writeProtect() {
		isWriteProtected = true;

		respIdSession.writeProtect();
		respAuthServer.writeProtect();
		respGetParamNames.writeProtect();
		respGetParamValues.writeProtect();
		respInvalidParamNames.writeProtect();
		respDescribeSdp.writeProtect();
	}

}
