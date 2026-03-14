package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.zeromq.ZContext;

public class MqContextHelper {

	private static final int ZMQ_IO_THREAD_COUNT = 4;

	private static ZContext zmqContextObj = null;  // one context for all MQs
	private static int zmqContextRefCount = 0;

	public static synchronized @NonNull ZContext openMqContext() {
		if (zmqContextObj == null) {
			zmqContextObj = new ZContext(ZMQ_IO_THREAD_COUNT);
		}
		++zmqContextRefCount;
		return zmqContextObj;
	}

	public static synchronized void closeMqContext() {
		if (zmqContextRefCount != 0 && --zmqContextRefCount == 0) {
			zmqContextObj.close();
			zmqContextObj = null;
		}
	}

}
