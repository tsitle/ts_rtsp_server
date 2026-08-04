package org.tsitle.lib_dataprov.avstreams.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsMqBase;

public final class FrameGrabberAudioAc3FromEsMq extends FrameGrabberAvFromEsMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAc3FromEsMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming
			);
	}

}
