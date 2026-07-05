package org.tsitle.lib_rtsp_mq.common;

import org.jspecify.annotations.NonNull;
import org.zeromq.ZContext;

/**
 * Helper class for managing the ZeroMQ context.
 */
public final class MqContextHelper {

	private static final int ZMQ_IO_THREAD_COUNT = 4;

	private static ZContext zmqContextObj = null;  // one context for all MQs
	private static int zmqContextRefCount = 0;

	private MqContextHelper() { }

	/**
	 * Open the ZeroMQ context if it is not already open.
	 * @return ZeroMQ context
	 */
	public static synchronized @NonNull ZContext openMqContext() {
		if (zmqContextObj == null) {
			zmqContextObj = new ZContext(ZMQ_IO_THREAD_COUNT);
		}
		++zmqContextRefCount;
		return zmqContextObj;
	}

	/**
	 * Close the ZeroMQ context if it is no longer necessary.
	 */
	public static synchronized void closeMqContext() {
		if (zmqContextRefCount != 0 && --zmqContextRefCount == 0) {
			zmqContextObj.close();
			zmqContextObj = null;
		}
	}

}
