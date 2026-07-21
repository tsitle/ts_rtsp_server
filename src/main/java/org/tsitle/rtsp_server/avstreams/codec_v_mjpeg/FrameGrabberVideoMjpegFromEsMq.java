package org.tsitle.rtsp_server.avstreams.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoFromEsMqBase;

public final class FrameGrabberVideoMjpegFromEsMq extends FrameGrabberVideoFromEsMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoMjpegFromEsMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

}
