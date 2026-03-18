package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zeromq.ZMQ;

/**
 * Factory class for creating MqMsgHandler instances.
 */
public final class MqMsgHandlerFactory {

	/**
	 * Create a new MqMsgHandler instance for external MQs.
	 * @param zmqSocket ZMQ socket
	 * @param useSegmentedMessages Use 'segmented' messages? Needs to be identical for both the sender and receiver
	 * @return MqMsgHandler instance
	 */
	public static @NonNull MqMsgHandlerBase createHandlerExternalMq(
				ZMQ.@Nullable Socket zmqSocket,
				boolean useSegmentedMessages
			) {
		if (useSegmentedMessages) {
			return new MqMsgHandlerSegmented(zmqSocket);
		}
		return new MqMsgHandlerTwoParts(zmqSocket);
	}

	/**
	 * Create a new MqMsgHandler instance for internal MQs.
	 * @param zmqSocket ZMQ socket
	 * @return MqMsgHandler instance
	 */
	public static @NonNull MqMsgHandlerBase createHandlerInternalMq(ZMQ.@Nullable Socket zmqSocket) {
		return new MqMsgHandlerTwoParts(zmqSocket);
	}

}
