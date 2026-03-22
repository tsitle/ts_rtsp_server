package org.tsitle.rtsp.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.security.SrtpContext;
import org.tsitle.rtsp.threads.LogMsgInterface;
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

		public Builder clientIpAddr(@NonNull InetAddress v) { this.threadParams.setClientIpAddr(v); return this; }
		public Builder clientDestPortRtcp(int v) { this.threadParams.setClientDestPortRtcp(v); return this; }

		public Builder rtcpSocketUdp(@NonNull DatagramSocket v) { this.threadParams.setRtcpSocketUdp(v); return this; }

		public Builder rtspSsrcId(int v) { this.threadParams.setRtspSsrcId(v); return this; }

		public Builder comIsRtpEncryptionEnabled(boolean v) { this.threadParams.setIsRtpEncryptionEnabled(v); return this; }
		public Builder comSrtpContext(@NonNull SrtpContext v) { this.threadParams.setSrtpContext(v); return this; }

		//
		public ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

}
