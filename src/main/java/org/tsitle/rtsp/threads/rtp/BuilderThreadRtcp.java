package org.tsitle.rtsp.threads.rtp;

import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class BuilderThreadRtcp {

	public static Builder builder() { return new Builder(); }

	public static final class Builder {
		// Thread-specific fields
		private final ParamsThreadRtcp threadParams = new ParamsThreadRtcp();

		// Fluent setters
		public Builder debugSessionId(String v) { this.threadParams.setDebugSessionId(v); return this; }
		public Builder debugStreamId(int v) { this.threadParams.setDebugStreamId(v); return this; }

		public Builder clientIpAddr(InetAddress v) { this.threadParams.setClientIpAddr(v); return this; }
		public Builder clientDestPortRtcp(int v) { this.threadParams.setClientDestPortRtcp(v); return this; }

		public Builder rtcpSocketUdp(DatagramSocket v) { this.threadParams.setRtcpSocketUdp(v); return this; }

		public Builder rtspSsrcId(int v) { this.threadParams.setRtspSsrcId(v); return this; }

		//
		public ThreadRtcpSendRecv build() {
			threadParams.validate();

			return new ThreadRtcpSendRecv(threadParams);
		}
	}

}
