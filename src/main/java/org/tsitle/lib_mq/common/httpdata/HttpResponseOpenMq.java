package org.tsitle.lib_mq.common.httpdata;

/**
 * Response for 'Open Message Queue' request.
 */
public record HttpResponseOpenMq(
		String cameraId,
		String cameraStreamType,
		int mqPort,
		String mqServerPubKey,
		boolean mqEncrypted,
		boolean mqMsgSegmented,
		String status
	) {
}
