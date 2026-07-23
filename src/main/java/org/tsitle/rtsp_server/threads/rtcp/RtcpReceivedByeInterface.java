package org.tsitle.rtsp_server.threads.rtcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

public interface RtcpReceivedByeInterface {

	void cbRtcpReceivedBye(@NonNull RtspProtoIdXsrc ssrcId);

}
