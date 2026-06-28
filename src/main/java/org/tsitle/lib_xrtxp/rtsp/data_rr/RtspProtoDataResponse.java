package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

public final class RtspProtoDataResponse extends RtspProtoDataRrBase {

	/** Authentication-related info from the server */
	public final @NonNull RtspProtoDataCntAuthSrv respAuthServer = new RtspProtoDataCntAuthSrv();

	/** RTSP message parameters */
	public final @NonNull RtspProtoDataCntGetSetParamKvs respGetParamValues = new RtspProtoDataCntGetSetParamKvs();

	/** Announced SDP in its raw form */
	public final @NonNull RtspProtoDataCntSdpRaw respDescribeSdpRaw = new RtspProtoDataCntSdpRaw();
	/** Announced SDP in its parsed form */
	public final @NonNull RtspProtoDataCntSdpStructured respDescribeSdpStc = new RtspProtoDataCntSdpStructured();

	/** Sub-Stream transport parameters */
	public final @NonNull RtspProtoDataCntSubStreamTp respSetupSubStreamTp = new RtspProtoDataCntSubStreamTp();
	/** Sub-Stream SSRC */
	public final @NonNull RtspProtoIdXsrc respSetupSubStreamSsrc = RtspProtoIdXsrc.ofEmpty();

	/** Supported Message Types */
	public final @NonNull RtspProtoDataCntMessageTypes respSuppMessageTypes = new RtspProtoDataCntMessageTypes();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataResponse() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setPlaybackRangeValue(@NonNull String value) {
		rrPlaybackRangeValue = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFromRequest(@NonNull RtspProtoDataRequest inputDataRequ) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
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

	@Override
	public void clear() {
		super.clear();

		respAuthServer.clear();
		respGetParamValues.clear();
		respDescribeSdpRaw.clear();
		respDescribeSdpStc.clear();
		respSetupSubStreamTp.clear();
		respSetupSubStreamSsrc.clear();
		respSuppMessageTypes.clear();
	}

	@Override
	public void writeProtect() {
		super.writeProtect();

		respAuthServer.writeProtect();
		respGetParamValues.writeProtect();
		respDescribeSdpRaw.writeProtect();
		respDescribeSdpStc.writeProtect();
		respSetupSubStreamTp.writeProtect();
		respSetupSubStreamSsrc.writeProtect();
		respSuppMessageTypes.writeProtect();
	}

}
