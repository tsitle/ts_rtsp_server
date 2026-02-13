package org.tsitle.rtsp.threads.rtp;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class BuilderThreadRtpSenderBase<B extends BuilderThreadRtpSenderBase<B, T>, T> {

	// Common / shared fields
	protected final ParamsThreadRtpSenderCommon threadParamsCommon = new ParamsThreadRtpSenderCommon();

	// Fluent setters
	@SuppressWarnings("unchecked")
	protected final B self() { return (B)this; }

	public B comDebugSessionId(String v) { this.threadParamsCommon.setDebugSessionId(v); return self(); }
	public B comDebugRewindMediaFiles(boolean v) { this.threadParamsCommon.setDebugRewindMediaFiles(v); return self(); }

	public B comStreamSourceId(int v) { this.threadParamsCommon.setStreamSourceId(v); return self(); }

	public B comClientIpAddr(InetAddress v) { this.threadParamsCommon.setClientIpAddr(v); return self(); }
	public B comClientDestPortRtp(int v) { this.threadParamsCommon.setClientDestPortRtp(v); return self(); }

	public B comRtpSocketUdp(DatagramSocket v) { this.threadParamsCommon.setRtpSocketUdp(v); return self(); }

	public B comAvFps(float v) { this.threadParamsCommon.setAvFramesPerSecond(v); return self(); }

	public B comRtpSeqNrT0(short v) { this.threadParamsCommon.setRtpSeqNrT0(v); return self(); }
	public B comRtpTimestampT0(int v) { this.threadParamsCommon.setRtpTimestampT0(v); return self(); }
	public B comRtspSsrcId(int v) { this.threadParamsCommon.setRtspSsrcId(v); return self(); }

	public B comXsrcBlockEntry(RtcpInnerXsrcBlock v) { this.threadParamsCommon.setXsrcBlockEntry(v); return self(); }
	public B comCbRtcpAppendToOutgoingQueque(BiConsumer<Integer, BufferExt> v) { this.threadParamsCommon.setCbRtcpAppendToOutgoingQueque(v); return self(); }

	public B comCbNotifyThreadReady(Consumer<Integer> v) { this.threadParamsCommon.setCbNotifyThreadReady(v); return self(); }
	public B comCbThreadMayStartPlayback(Supplier<Boolean> v) { this.threadParamsCommon.setCbThreadMayStartPlayback(v); return self(); }

	//
	public abstract T build() throws Exception;

	//
	protected void validateCommon() {
		threadParamsCommon.validate();
	}

}
