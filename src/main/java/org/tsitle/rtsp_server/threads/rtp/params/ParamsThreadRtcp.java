package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.threads.rtcp.RtcpReceivedByeInterface;

import java.util.Optional;

public final class ParamsThreadRtcp extends ParamsThreadRtxp implements Cloneable {

	private @Nullable RtcpReceivedByeInterface rtcpReceivedByeInterface = null;
	private boolean isSetRtcpReceivedByeInterface = false;

	public ParamsThreadRtcp() {
		super(true, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<RtcpReceivedByeInterface> getRtcpReceivedByeInterface() { return Optional.ofNullable(rtcpReceivedByeInterface); }
	public void setRtcpReceivedByeInterface(@NonNull RtcpReceivedByeInterface value) {
		this.rtcpReceivedByeInterface = value;
		this.isSetRtcpReceivedByeInterface = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		super.validate();
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtcp clone() {
		return (ParamsThreadRtcp)super.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetRtcpReceivedByeInterface, "rtcpReceivedByeInterface");
	}

	private void validateParamValues() {
		requireNonNull(rtcpReceivedByeInterface, "rtcpReceivedByeInterface");
	}

	@SuppressWarnings("SameParameterValue")
	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtpSenderCommon.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
