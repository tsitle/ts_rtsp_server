package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdSession;

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
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getRequUnsupportedFeatureName() {
		return requUnsupportedFeatureName;
	}

	public void setRequUnsupportedFeatureName(@NonNull String featureName) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requUnsupportedFeatureName = featureName;
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
	}

	public void writeProtect() {
		isWriteProtected = true;

		requIdSession.writeProtect();
		requAuthClient.writeProtect();
		requGetParamNames.writeProtect();
		requSetParamValues.writeProtect();
		requInvalidParamNames.writeProtect();
		requAnnouncedSdp.writeProtect();
	}

}
