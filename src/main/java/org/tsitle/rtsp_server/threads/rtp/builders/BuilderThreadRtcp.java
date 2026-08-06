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

	public static final class Builder {
		// Thread-specific fields
		private final ParamsThreadRtcp threadParams = new ParamsThreadRtcp();

		// Fluent setters
		public @NonNull Builder logMsgInterface(@NonNull LogMsgInterface v) { this.threadParams.setLogMsgInterface(v); return this; }

		public @NonNull Builder debugIdSession(@NonNull RtspProtoIdSession v) { this.threadParams.setDebugSessionId(v); return this; }

		public @NonNull Builder idEsSource(@NonNull RtspProtoIdEsSource v) { this.threadParams.setIdEsSource(v); return this; }

		public @NonNull Builder idSubStream(@NonNull RtspProtoIdSubStream v) { this.threadParams.setIdSubStream(v); return this; }

		public @NonNull Builder idSsrc(@NonNull RtspProtoIdXsrc v) { this.threadParams.setSsrcId(v); return this; }

		public @NonNull Builder tpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParams.setTpClientIpAddr(v); return this; }
		public @NonNull Builder tpClientDestUdpPortRtcp(@NonNull RtspProtoSocketPortNr v) { this.threadParams.setTpClientDestUdpPort(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public @NonNull Builder tpSocketUdpRtcp(@NonNull DatagramSocket v) { this.threadParams.setTpSocketUdp(v); return this; }
		public @NonNull Builder tpClientDestTcpIf(@NonNull RtspChildThreadsCbRtxpTcpInterface v) { this.threadParams.setTpClientDestTcpIf(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public @NonNull Builder tpClientDestTcpChannRtcp(@NonNull RtspProtoTcpChannelNr v) { this.threadParams.setTpClientDestTcpChann(v); return this; }

		public @NonNull Builder cryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParams.setCryptoIsRtxpEncryptionEnabled(v); return this; }
		public @NonNull Builder cryptoKmdInboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdInbound(v); return this; }
		public @NonNull Builder cryptoKmdOutboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdOutbound(v); return this; }

		public @NonNull Builder rtcpReceivedByeInterface(@NonNull RtcpReceivedByeInterface v) { this.threadParams.setRtcpReceivedByeInterface(v); return this; }

		//
		public @NonNull ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
