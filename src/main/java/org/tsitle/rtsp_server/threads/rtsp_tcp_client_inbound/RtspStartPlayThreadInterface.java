package org.tsitle.rtsp_server.threads.rtsp_tcp_client_inbound;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoGlobalSessionInfoSvc;
import org.tsitle.lib_xrtxp.rtsp.RtspProtoSessionInfo;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtcpFromRtpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;

public interface RtspStartPlayThreadInterface {

	@NonNull ThreadRtspPlay startThreadRtspPlay(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc,
				@NonNull RtspChildThreadsCbRtcpFromRtpInterface childThreadsCbRtcpInterface,
				@NonNull RtspChildThreadsCbRtxpTcpInterface childThreadsCbRtpTcpInterface,
				int clientConnectionNr
			);

}
