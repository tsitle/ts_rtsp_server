package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdInputSource;
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

	/** Server IP address as resolved from Resource URL */
	private @NonNull String respServerIpFromRscUrl = "";

	/** Resource URL that the response belongs to */
	private @NonNull String respResourceUrl = "";

	/** Input Source ID */
	private final @NonNull RtspProtoIdInputSource respIdInputSource = new RtspProtoIdInputSource();

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
	public void setRespUnsupportedFeatureName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.respUnsupportedFeatureName = value;
	}

	public @NonNull String getRespServerIpFromRscUrl() {
		return respServerIpFromRscUrl;
	}
	public void setRespServerIpFromRscUrl(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.respServerIpFromRscUrl = value;
	}

	public @NonNull String getRespResourceUrl() {
		return respResourceUrl;
	}
	public void setRespResourceUrl(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.respResourceUrl = value;
	}

	public @NonNull RtspProtoIdInputSource getRespIdInputSource() {
		return respIdInputSource;
	}
	public void setRespIdInputSource(@NonNull RtspProtoIdInputSource value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.respIdInputSource.copyFrom(value);
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
	}

	public void copyFrom(@NonNull RtspProtoDataRequest inputDataRequ) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		respIdSession.copyFrom(inputDataRequ.requIdSession);
		respGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		respInvalidParamNames.copyFrom(inputDataRequ.requInvalidParamNames);
		respUnsupportedFeatureName = inputDataRequ.getRequUnsupportedFeatureName();
		respResourceUrl = inputDataRequ.getRequResourceUrl();
		respServerIpFromRscUrl = inputDataRequ.getRequServerIpFromRscUrl();
		respIdInputSource.copyFrom(inputDataRequ.getRequIdInputSource());
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
	}

}
