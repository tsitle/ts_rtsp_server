package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

public final class RtspProtoDataResponse {

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
	public @NonNull String respUnsupportedFeatureName = "";

	public RtspProtoDataResponse() { }

	public RtspProtoDataResponse(@NonNull RtspProtoDataRequest inputDataRequ) {
		copyFrom(inputDataRequ);
	}

	public void clear() {
		respAuthServer.clear();
		respGetParamNames.clear();
		respGetParamValues.clear();
		respInvalidParamNames.clear();
		respDescribeSdp.clear();
		respUnsupportedFeatureName = "";
	}

	public void copyFrom(@NonNull RtspProtoDataRequest inputDataRequ) {
		respGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		respInvalidParamNames.copyFrom(inputDataRequ.requInvalidParamNames);
		respUnsupportedFeatureName = inputDataRequ.requUnsupportedFeatureName;
	}

}
