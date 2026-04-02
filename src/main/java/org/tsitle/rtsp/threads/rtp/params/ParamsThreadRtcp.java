package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

public final class ParamsThreadRtcp extends ParamsThreadRtxp implements Cloneable {

	/** Callback for notifying the parent thread that an RR packet has been received (for resetting the timeout timer) */
	private Consumer<Instant> cbNotifyRrPacketReceived;
	private boolean isSetCbNotifyRrPacketReceived;

	public ParamsThreadRtcp() {
		super(true, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Consumer<Instant>> getCbNotifyRrPacketReceived() { return Optional.ofNullable(cbNotifyRrPacketReceived); }
	public void setCbNotifyRrPacketReceived(@NonNull Consumer<@NonNull Instant> cbNotifyRrPacketReceived) {
		this.cbNotifyRrPacketReceived = cbNotifyRrPacketReceived;
		this.isSetCbNotifyRrPacketReceived = true;
	}

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
		requireIsSet(isSetCbNotifyRrPacketReceived, "cbNotifyRrPacketReceived");
	}

	private void validateParamValues() {
		requireNonNull(cbNotifyRrPacketReceived, "cbNotifyRrPacketReceived");
	}

	@SuppressWarnings("SameParameterValue")
	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtcp.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtcp.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
