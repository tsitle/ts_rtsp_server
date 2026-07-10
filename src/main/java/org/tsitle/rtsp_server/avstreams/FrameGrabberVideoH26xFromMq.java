package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberVideoH26xFromMq extends FrameGrabberVideoFromMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoH26xFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

}
