package org.tsitle.lib_dataprov.avstreams.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsMqBase;

public final class FrameGrabberAudioAacFromEsMq extends FrameGrabberAvFromEsMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAacFromEsMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming
			);
	}

}
