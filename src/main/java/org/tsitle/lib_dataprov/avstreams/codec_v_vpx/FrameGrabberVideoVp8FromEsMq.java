package org.tsitle.lib_dataprov.avstreams.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsMqBase;

public final class FrameGrabberVideoVp8FromEsMq extends FrameGrabberAvFromEsMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoVp8FromEsMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

}
