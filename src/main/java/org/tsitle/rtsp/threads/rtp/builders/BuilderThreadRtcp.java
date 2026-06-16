package org.tsitle.rtsp.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.proto.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtcp;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

import java.net.DatagramSocket;
import java.time.Instant;
import java.util.function.Consumer;

public class BuilderThreadRtcp {

	public static Builder builder() { return new Builder(); }

	public static final class Builder {
		// Thread-specific fields
		private final ParamsThreadRtcp threadParams = new ParamsThreadRtcp();

		// Fluent setters
		public Builder logMsgInterface(@NonNull LogMsgInterface v) { this.threadParams.setLogMsgInterface(v); return this; }

		public Builder debugSessionId(@NonNull RtspProtoIdSession v) { this.threadParams.setDebugSessionId(v); return this; }

		public Builder idStreamSource(@NonNull RtspProtoIdStreamSource v) { this.threadParams.setIdStreamSource(v); return this; }

		public Builder ssrcId(@NonNull RtspProtoIdXsrc v) { this.threadParams.setSsrcId(v); return this; }

		public Builder tpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParams.setTpClientIpAddr(v); return this; }
		public Builder tpClientDestUdpPortRtcp(@NonNull RtspProtoSocketPortNr v) { this.threadParams.setTpClientDestUdpPort(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpSocketUdpRtcp(@NonNull DatagramSocket v) { this.threadParams.setTpSocketUdp(v); return this; }
		public Builder tpClientDestTcpIf(@NonNull RtxpTcpReadWrite v) { this.threadParams.setTpClientDestTcpIf(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public Builder tpClientDestTcpChannRtcp(@NonNull RtspProtoTcpChannelNr v) { this.threadParams.setTpClientDestTcpChann(v); return this; }

		public Builder cryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParams.setCryptoIsRtxpEncryptionEnabled(v); return this; }
		public Builder cryptoKmdInboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdInbound(v); return this; }
		public Builder cryptoKmdOutboundRtcp(@Nullable SrtxpKmd v) { this.threadParams.setCryptoKmdOutbound(v); return this; }

		public Builder cbNotifyRrPacketReceived(@NonNull Consumer<@NonNull Instant> v) { this.threadParams.setCbNotifyRrPacketReceived(v); return this; }

		//
		public ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

}
