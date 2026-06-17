package org.tsitle.lib_rtsp_mq.common.cbtypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoBaseNumberNonNeg32bit;

import java.util.Optional;

/**
 * Channel ID for usage with the Channel Bus.
 */
public final class MqChannelBusChannelId extends RtspProtoBaseNumberNonNeg32bit<MqChannelBusChannelId> implements Cloneable {

	private MqChannelBusChannelId() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static MqChannelBusChannelId ofEmpty() {
		return new MqChannelBusChannelId();
	}

	public static MqChannelBusChannelId of(long value32bit) throws RtspProtoNumberRangeException {
		MqChannelBusChannelId resObj = new MqChannelBusChannelId();
		resObj.setNumber32bit(value32bit);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getId32bit() {
		return getNumber32bit();
	}
	public void setId32bit(long value32bit) throws RtspProtoNumberRangeException {
		setNumber32bit(value32bit);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void copyFrom(@NonNull MqChannelBusChannelId other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		theNumber = other.theNumber;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof MqChannelBusChannelId that)) {
			return false;
		}
		return super.equalsOnlyValue(that);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"channelId=" + (isEmpty() ? "unset" : Long.toUnsignedString(theNumber)) +
				"]";
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public MqChannelBusChannelId clone() {
		MqChannelBusChannelId cloned = new MqChannelBusChannelId();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

}
