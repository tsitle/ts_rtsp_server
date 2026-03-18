package org.tsitle.rtsp.mq.httpdata;

/**
 * Response for 'Open Message Queue' request.
 */
public record HttpResponseOpenMq(
		String cameraId,
		String cameraStreamType,
		int mqPort,
		String mqServerPubKey,
		boolean mqEncrypted,
		String status
	) {
}
