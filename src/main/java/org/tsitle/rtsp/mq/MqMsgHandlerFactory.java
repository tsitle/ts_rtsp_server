package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zeromq.ZMQ;

/**
 * Factory class for creating MqMsgHandler instances.
 */
public final class MqMsgHandlerFactory {

	private static final boolean USE_SEGMENTED_MESSAGES = false;  // needs to match the setting of the external MQ's publisher

	/**
	 * Create a new MqMsgHandler instance.
	 * @param zmqSocket ZMQ socket
	 * @return MqMsgHandler instance
	 */
	public static @NonNull MqMsgHandlerBase createHandler(ZMQ.@Nullable Socket zmqSocket) {
		if (USE_SEGMENTED_MESSAGES) {
			return new MqMsgHandlerSegmented(zmqSocket);
		}
		return new MqMsgHandlerTwoParts(zmqSocket);
	}

}
