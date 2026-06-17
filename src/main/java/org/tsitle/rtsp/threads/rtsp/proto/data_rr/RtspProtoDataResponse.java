package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

public final class RtspProtoDataResponse extends RtspProtoDataRrBase {

	/** Authentication-related info from the server */
	public final @NonNull RtspProtoDataCntAuthSrv respAuthServer = new RtspProtoDataCntAuthSrv();

	/** RTSP message parameters */
	public final @NonNull RtspProtoDataCntGetSetParamKvs respGetParamValues = new RtspProtoDataCntGetSetParamKvs();

	/** Announced SDP */
	public final @NonNull RtspProtoDataCntSdp respDescribeSdp = new RtspProtoDataCntSdp();

	/** Supported Message Types */
	public final @NonNull RtspProtoDataCntMessageTypes respSuppMessageTypes = new RtspProtoDataCntMessageTypes();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataResponse() {
		super();
	}

	public RtspProtoDataResponse(@NonNull RtspProtoDataRequest inputDataRequ) {
		super();

		rrIdSession.copyFrom(inputDataRequ.rrIdSession);
		rrGetParamNames.copyFrom(inputDataRequ.rrGetParamNames);
		rrInvalidParamNames.copyFrom(inputDataRequ.rrInvalidParamNames);
		rrUnsupportedFeatureName = inputDataRequ.getUnsupportedFeatureName();
		rrRscUrl.copyFrom(inputDataRequ.rrRscUrl);
		rrServerIpFromRscUrl.copyFrom(inputDataRequ.rrServerIpFromRscUrl);
		rrRtspProtoVersionToUse = inputDataRequ.getRtspProtoVersionToUse();
		rrCseqNrLastRcvd.copyFrom(inputDataRequ.rrCseqNrLastRcvd);
		rrStreamTpMain.copyFrom(inputDataRequ.rrStreamTpMain);
		rrClientUa = inputDataRequ.getClientUa();
		rrClientIpAddr.copyFrom(inputDataRequ.rrClientIpAddr);
		rrPlaybackRangeValue = inputDataRequ.getPlaybackRangeValue();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void clear() {
		super.clear();

		respAuthServer.clear();
		respGetParamValues.clear();
		respDescribeSdp.clear();
		respSuppMessageTypes.clear();
	}

	@Override
	public void writeProtect() {
		super.writeProtect();

		respAuthServer.writeProtect();
		respGetParamValues.writeProtect();
		respDescribeSdp.writeProtect();
		respSuppMessageTypes.writeProtect();
	}

}
