package org.tsitle.rtsp.mq;

public record HttpResponseOpenMq(
		String cameraId,
		String cameraStreamType,
		int mqPort,
		String mqServerPubKey,
		String status
	) {
}
