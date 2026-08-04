package org.tsitle.lib_mq.common;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zeromq.ZMQ;

/**
 * Factory class for creating MqMsgHandler instances.
 */
public final class MqMsgHandlerFactory {

	private MqMsgHandlerFactory() { }

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
			return new MqMsgHandlerSegmented(zmqSocket, null, -1);
		}
		return new MqMsgHandlerTwoParts(zmqSocket, null, -1);
	}

	/**
	 * Create a new MqMsgHandler instance for internal MQs.
	 * @param zmqSocket ZMQ socket
	 * @param zmqPollerObj ZMQ poller object
	 * @param zmqPollerIxWrite ZMQ poller index for write events
	 * @return MqMsgHandler instance
	 */
	public static @NonNull MqMsgHandlerBase createHandlerInternalMq(
				ZMQ.@Nullable Socket zmqSocket,
				ZMQ.@Nullable Poller zmqPollerObj,
				int zmqPollerIxWrite
			) {
		return new MqMsgHandlerTwoParts(zmqSocket, zmqPollerObj, zmqPollerIxWrite);
	}

}
