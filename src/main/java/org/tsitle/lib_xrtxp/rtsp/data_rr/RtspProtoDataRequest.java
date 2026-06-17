package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoCseqNr;

public final class RtspProtoDataRequest extends RtspProtoDataRrBase {

	/** Authentication-related info from the client */
	public final @NonNull RtspProtoDataCntAuthClient requAuthClient = new RtspProtoDataCntAuthClient();

	/** RTSP message parameters to set */
	public final @NonNull RtspProtoDataCntGetSetParamKvs requSetParamValues = new RtspProtoDataCntGetSetParamKvs();

	/** Announced SDP */
	public final @NonNull RtspProtoDataCntSdp requAnnouncedSdp = new RtspProtoDataCntSdp();

	/** RTSP message Sequence Number to use for sending a request */
	private final @NonNull RtspProtoCseqNr requCseqNrToSend = RtspProtoCseqNr.ofZero();

	/** RTSP session state */
	public final @NonNull RtspProtoDataCntSessionState requRtspSessionState = new RtspProtoDataCntSessionState();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataRequest() {
		super();
	}

	public RtspProtoDataRequest(@NonNull RtspProtoDataRequest other) {
		super();

		this.isWriteProtected = other.isWriteProtected;
		this.rrIdSession.copyFrom(other.rrIdSession);
		this.rrGetParamNames.copyFrom(other.rrGetParamNames);
		this.rrInvalidParamNames.copyFrom(other.rrInvalidParamNames);
		this.rrUnsupportedFeatureName = other.rrUnsupportedFeatureName;
		this.rrServerIpFromRscUrl.copyFrom(other.rrServerIpFromRscUrl);
		this.rrRscUrl.copyFrom(other.rrRscUrl);
		this.rrRtspProtoVersionToUse = other.rrRtspProtoVersionToUse;
		this.rrCseqNrLastRcvd.copyFrom(other.rrCseqNrLastRcvd);
		this.rrStreamTpMain.copyFrom(other.rrStreamTpMain);
		this.rrClientUa = other.rrClientUa;
		this.rrClientIpAddr.copyFrom(other.rrClientIpAddr);
		this.rrPlaybackRangeValue = other.rrPlaybackRangeValue;

		this.requAuthClient.copyFrom(other.requAuthClient);
		this.requSetParamValues.copyFrom(other.requSetParamValues);
		this.requAnnouncedSdp.copyFrom(other.requAnnouncedSdp);
		this.requCseqNrToSend.copyFrom(other.requCseqNrToSend);
		this.requRtspSessionState.copyFrom(other.requRtspSessionState);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setUnsupportedFeatureName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrUnsupportedFeatureName = value;
	}

	public void setRtspProtoVersionToUse(@NonNull RtspProtocolVersion value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrRtspProtoVersionToUse = value;
	}

	public @NonNull RtspProtoCseqNr getCseqNrToSend() {
		return requCseqNrToSend.clone();
	}
	public void copyAndIncrementCseqNrToSend(@NonNull RtspProtoCseqNr value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requCseqNrToSend.copyFrom(value);
		this.requCseqNrToSend.increment();
	}

	public void setClientUa(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrClientUa = value;
	}

	public void setPlaybackRangeValue(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrPlaybackRangeValue = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void clear() {
		super.clear();

		requAuthClient.clear();
		requSetParamValues.clear();
		requAnnouncedSdp.clear();
		requCseqNrToSend.clear();
		try {
			requCseqNrToSend.setCseq32bit(0L);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		requRtspSessionState.clear();
	}

	@Override
	public void writeProtect() {
		super.writeProtect();

		requAuthClient.writeProtect();
		requSetParamValues.writeProtect();
		requAnnouncedSdp.writeProtect();
		requCseqNrToSend.writeProtect();
		requRtspSessionState.writeProtect();
	}

}
