package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoAdSettingsStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoCseqNr;

public final class RtspProtoDataRequest extends RtspProtoDataRrBase {

	/** Authentication-related info from the client */
	public final @NonNull RtspProtoDataCntAuthClient requAuthClient = new RtspProtoDataCntAuthClient();

	/** RTSP message parameters to set */
	public final @NonNull RtspProtoDataCntGetSetParamKvs requSetParamValues = new RtspProtoDataCntGetSetParamKvs();

	/** Required features */
	public final @NonNull RtspProtoDataCntGetRequFeat requRequiredFeatures = new RtspProtoDataCntGetRequFeat();
	/** Required features for the proxy */
	public final @NonNull RtspProtoDataCntGetRequFeat requProxyRequiredFeatures = new RtspProtoDataCntGetRequFeat();

	/** Stream settings for building an ANNOUNCE request */
	public final @NonNull RtspProtoAdSettingsStream requAdStreamSett = new RtspProtoAdSettingsStream();
	/** Announced SDP in its raw form */
	public final @NonNull RtspProtoDataCntSdpRaw requAnnouncedSdpRaw = new RtspProtoDataCntSdpRaw();
	/** Announced SDP in its parsed form */
	public final @NonNull RtspProtoDataCntSdpStructured requAnnouncedSdpStc = new RtspProtoDataCntSdpStructured();

	/** RTSP message Sequence Number to use for sending a request */
	private final @NonNull RtspProtoCseqNr requCseqNrToSend = RtspProtoCseqNr.ofZero();

	/** RTSP connection policy */
	private @NonNull RtspConnectionPolicy requConnectionPolicy = RtspConnectionPolicy.NONE;

	/** RTSP session state */
	public final @NonNull RtspProtoDataCntSessionState requRtspSessionState = new RtspProtoDataCntSessionState();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoDataRequest() {
		super();
	}

	public RtspProtoDataRequest(@NonNull RtspProtoDataRequest other) {
		super();

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
		this.requRequiredFeatures.copyFrom(other.requRequiredFeatures);
		this.requProxyRequiredFeatures.copyFrom(other.requProxyRequiredFeatures);
		this.requAdStreamSett.copyFrom(other.requAdStreamSett);
		this.requAnnouncedSdpRaw.copyFrom(other.requAnnouncedSdpRaw);
		this.requAnnouncedSdpStc.copyFrom(other.requAnnouncedSdpStc);
		this.requCseqNrToSend.copyFrom(other.requCseqNrToSend);
		this.requConnectionPolicy = other.requConnectionPolicy;
		this.requRtspSessionState.copyFrom(other.requRtspSessionState);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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

	public void setPlaybackRangeValue(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.rrPlaybackRangeValue = value;
	}

	public @NonNull RtspConnectionPolicy getConnectionPolicy() {
		return requConnectionPolicy;
	}
	public void setConnectionPolicy(@NonNull RtspConnectionPolicy value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.requConnectionPolicy = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoDataRequest other) {
		super.copyFrom(other);

		requAuthClient.copyFrom(other.requAuthClient);
		requSetParamValues.copyFrom(other.requSetParamValues);
		requRequiredFeatures.copyFrom(other.requRequiredFeatures);
		requProxyRequiredFeatures.copyFrom(other.requProxyRequiredFeatures);
		requAdStreamSett.copyFrom(other.requAdStreamSett);
		requAnnouncedSdpRaw.copyFrom(other.requAnnouncedSdpRaw);
		requAnnouncedSdpStc.copyFrom(other.requAnnouncedSdpStc);
		requCseqNrToSend.copyFrom(other.requCseqNrToSend);
		requConnectionPolicy = other.requConnectionPolicy;
		requRtspSessionState.copyFrom(other.requRtspSessionState);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void clear() {
		super.clear();

		requAuthClient.clear();
		requSetParamValues.clear();
		requRequiredFeatures.clear();
		requProxyRequiredFeatures.clear();
		requAdStreamSett.clear();
		requAnnouncedSdpRaw.clear();
		requAnnouncedSdpStc.clear();
		requCseqNrToSend.clear();
		try {
			requCseqNrToSend.setCseq32bit(0L);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		requConnectionPolicy = RtspConnectionPolicy.NONE;
		requRtspSessionState.clear();
	}

	@Override
	public void writeProtect() {
		super.writeProtect();

		requAuthClient.writeProtect();
		requSetParamValues.writeProtect();
		requRequiredFeatures.writeProtect();
		requProxyRequiredFeatures.writeProtect();
		requAdStreamSett.writeProtect();
		requAnnouncedSdpRaw.writeProtect();
		requAnnouncedSdpStc.writeProtect();
		requCseqNrToSend.writeProtect();
		requRtspSessionState.writeProtect();
	}

}
