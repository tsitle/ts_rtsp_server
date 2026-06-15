package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;

import java.time.Instant;

/**
 * Callback interface for RTSP child threads.
 */
public interface RtspChildThreadsCallbackInterface {

	void cbSendRtcpPackets(int ssrcId, @NonNull BufferExt rtcpPacketsBuf);
	void cbRcvdRtcpRrPacket(@NonNull Instant time);

	void cbNotifyThreadReady(@NonNull RtspProtoIdStreamSource idStreamSource);
	@NonNull Boolean cbThreadMayStartPlayback();

}
