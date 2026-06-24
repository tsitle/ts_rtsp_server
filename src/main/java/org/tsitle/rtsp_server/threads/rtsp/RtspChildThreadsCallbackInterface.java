package org.tsitle.rtsp_server.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.time.Instant;

/**
 * Callback interface for RTSP child threads.
 */
public interface RtspChildThreadsCallbackInterface {

	void cbSendRtcpPackets(@NonNull RtspProtoIdXsrc ssrcId, @NonNull BufferExt rtcpPacketsBuf);
	void cbRcvdRtcpRrPacket(@NonNull Instant time);

	void cbNotifyThreadReady(@NonNull RtspProtoIdSubStream idSubStream);
	@NonNull Boolean cbThreadMayStartPlayback();

}
