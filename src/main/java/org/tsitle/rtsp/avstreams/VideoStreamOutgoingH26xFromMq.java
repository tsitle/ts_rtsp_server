package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;

public class VideoStreamOutgoingH26xFromMq extends VideoStreamOutgoingFromMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public VideoStreamOutgoingH26xFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

}
