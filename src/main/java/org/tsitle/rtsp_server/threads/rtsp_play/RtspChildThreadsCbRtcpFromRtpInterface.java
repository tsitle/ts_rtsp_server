package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

/**
 * Callback interface for RTSP child threads.
 */
public interface RtspChildThreadsCbRtcpFromRtpInterface {

	void cbSendRtcpPacketsFromRtp(@NonNull RtspProtoIdXsrc ssrcId, @NonNull BufferExt rtcpPacketsBuf);

}
