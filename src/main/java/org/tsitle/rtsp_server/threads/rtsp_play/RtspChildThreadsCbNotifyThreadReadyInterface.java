package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

/**
 * Callback interface for RTSP child threads.
 */
public interface RtspChildThreadsCbNotifyThreadReadyInterface {

	void cbNotifyThreadReady(@NonNull RtspProtoIdSubStream idSubStream);
	@NonNull Boolean cbThreadMayStartPlayback();

}
