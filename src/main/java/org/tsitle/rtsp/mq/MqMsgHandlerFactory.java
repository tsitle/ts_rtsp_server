package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zeromq.ZMQ;

public final class MqMsgHandlerFactory {

	private static final boolean USE_SEGMENTED_MESSAGES = false;

	public static @NonNull MqMsgHandlerBase createHandler(ZMQ.@Nullable Socket zmqSocket) {
		if (USE_SEGMENTED_MESSAGES) {
			return new MqMsgHandlerSegmented(zmqSocket);
		}
		return new MqMsgHandlerTwoParts(zmqSocket);
	}

}
