package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp_server.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;

public final class ChildThreadsForOneStream {

	public final @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();
	public final @NonNull RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	public final @NonNull RtspProtoIdStreamSource idStreamSource = RtspProtoIdStreamSource.ofEmpty();

	public ThreadRtpSenderBase<?, ?, ?, ?> rtpThreadSender;

	public ThreadRtcpSendRecv rtcpThreadSendRecv;
	public int rtcpLastTargetCongestionLevel = -1;

	public boolean srtxpInboundRekeyingInProgress = false;
	public boolean srtxpOutboundRekeyingInProgress = false;

	ChildThreadsForOneStream(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource
			) {
		this.idSubStream.copyFrom(idSubStream);
		this.idSubStream.writeProtect();
		this.idInputSource.copyFrom(idInputSource);
		this.idInputSource.writeProtect();
		this.idStreamSource.copyFrom(idStreamSource);
		this.idStreamSource.writeProtect();
	}

}
