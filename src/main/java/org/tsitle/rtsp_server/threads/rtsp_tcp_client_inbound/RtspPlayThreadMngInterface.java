package org.tsitle.rtsp_server.threads.rtsp_tcp_client_inbound;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;

public interface RtspPlayThreadMngInterface {

	@Nullable ThreadRtspPlay startOrGetThreadRtspPlay(
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspChildThreadsCbRtxpTcpInterface childThreadsCbRtpTcpInterface
			);

	void updateThreadsSessionInfoBySessionId(
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoSessionInfo rtspSessionInfo
			);

	void shutdownThreadBySessionId(@NonNull RtspProtoIdSession idSession);

}
