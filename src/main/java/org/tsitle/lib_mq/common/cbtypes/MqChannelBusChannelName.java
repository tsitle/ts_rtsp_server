package org.tsitle.lib_mq.common.cbtypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoBaseIdString;

/**
 * Channel Name for usage with the Channel Bus.
 */
public final class MqChannelBusChannelName extends RtspProtoBaseIdString implements Cloneable {

	private MqChannelBusChannelName() {
		super();
	}

	private MqChannelBusChannelName(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static MqChannelBusChannelName ofEmpty() {
		return new MqChannelBusChannelName();
	}

	public static MqChannelBusChannelName of(@NonNull String idStr) {
		return new MqChannelBusChannelName(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public MqChannelBusChannelName clone() {
		return (MqChannelBusChannelName)super.clone();
	}

}
