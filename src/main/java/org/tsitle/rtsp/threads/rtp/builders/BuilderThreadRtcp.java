package org.tsitle.rtsp.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtcp;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class BuilderThreadRtcp {

	public static Builder builder() { return new Builder(); }

	public static final class Builder {
		// Thread-specific fields
		private final ParamsThreadRtcp threadParams = new ParamsThreadRtcp();

		// Fluent setters
		public Builder logMsgInterface(@NonNull LogMsgInterface v) { this.threadParams.setLogMsgInterface(v); return this; }

		public Builder debugSessionId(@NonNull String v) { this.threadParams.setDebugSessionId(v); return this; }

		public Builder streamSourceId(int v) { this.threadParams.setStreamSourceId(v); return this; }

		public Builder rtspSsrcId(int v) { this.threadParams.setRtspSsrcId(v); return this; }

		public Builder tpClientIpAddr(@NonNull InetAddress v) { this.threadParams.setTpClientIpAddr(v); return this; }
		public Builder tpClientDestUdpPortRtcp(int v) { this.threadParams.setTpClientDestUdpPort(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpSocketUdpRtcp(@NonNull DatagramSocket v) { this.threadParams.setTpSocketUdp(v); return this; }
		public Builder tpClientDestTcpIf(RtxpTcpReadWrite v) { this.threadParams.setTpClientDestTcpIf(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpClientDestTcpChannRtcp(int v) { this.threadParams.setTpClientDestTcpChann(v); return this; }

		public Builder cryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParams.setCryptoIsRtxpEncryptionEnabled(v); return this; }
		public Builder cryptoKmdInboundRtcp(@NonNull SrtxpKmd v) { this.threadParams.setCryptoKmdInbound(v); return this; }
		public Builder cryptoKmdOutboundRtcp(@NonNull SrtxpKmd v) { this.threadParams.setCryptoKmdOutbound(v); return this; }

		//
		public ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

}
