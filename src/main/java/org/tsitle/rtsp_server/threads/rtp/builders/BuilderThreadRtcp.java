package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp_server.threads.rtcp.RtcpReceivedByeInterface;
import org.tsitle.rtsp_server.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtcp;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;

import java.net.DatagramSocket;

public final class BuilderThreadRtcp {

	public static Builder builder() { return new Builder(); }

	public static final class Builder {
		// Thread-specific fields
		private final ParamsThreadRtcp threadParams = new ParamsThreadRtcp();

		// Fluent setters
		public Builder logMsgInterface(@NonNull LogMsgInterface v) { this.threadParams.setLogMsgInterface(v); return this; }

		public Builder debugIdSession(@NonNull RtspProtoIdSession v) { this.threadParams.setDebugSessionId(v); return this; }

		public Builder idEsSource(@NonNull RtspProtoIdEsSource v) { this.threadParams.setIdEsSource(v); return this; }

		public Builder idSubStream(@NonNull RtspProtoIdSubStream v) { this.threadParams.setIdSubStream(v); return this; }

		public Builder idSsrc(@NonNull RtspProtoIdXsrc v) { this.threadParams.setSsrcId(v); return this; }

		public Builder tpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParams.setTpClientIpAddr(v); return this; }
		public Builder tpClientDestUdpPortRtcp(@NonNull RtspProtoSocketPortNr v) { this.threadParams.setTpClientDestUdpPort(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpSocketUdpRtcp(@NonNull DatagramSocket v) { this.threadParams.setTpSocketUdp(v); return this; }
		public Builder tpClientDestTcpIf(@NonNull RtspChildThreadsCbRtxpTcpInterface v) { this.threadParams.setTpClientDestTcpIf(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpClientDestTcpChannRtcp(@NonNull RtspProtoTcpChannelNr v) { this.threadParams.setTpClientDestTcpChann(v); return this; }

		public Builder cryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParams.setCryptoIsRtxpEncryptionEnabled(v); return this; }
		public Builder cryptoKmdInboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdInbound(v); return this; }
		public Builder cryptoKmdOutboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdOutbound(v); return this; }

		public Builder rtcpReceivedByeInterface(@NonNull RtcpReceivedByeInterface v) { this.threadParams.setRtcpReceivedByeInterface(v); return this; }

		//
		public ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

}
