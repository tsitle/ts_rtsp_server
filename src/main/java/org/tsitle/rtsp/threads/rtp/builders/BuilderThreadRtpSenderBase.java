package org.tsitle.rtsp.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class BuilderThreadRtpSenderBase<B extends BuilderThreadRtpSenderBase<B, T>, T> {

	// Common / shared fields
	protected final ParamsThreadRtpSenderCommon threadParamsCommon = new ParamsThreadRtpSenderCommon();

	// Fluent setters
	@SuppressWarnings("unchecked")
	protected final B self() { return (B)this; }

	public B logMsgInterface(@NonNull LogMsgInterface v) { this.threadParamsCommon.setLogMsgInterface(v); return self(); }

	public B comDebugSessionId(@NonNull String v) { this.threadParamsCommon.setDebugSessionId(v); return self(); }
	public B comDebugRewindMediaFiles(boolean v) { this.threadParamsCommon.setDebugRewindMediaFiles(v); return self(); }

	public B comStreamSourceId(int v) { this.threadParamsCommon.setStreamSourceId(v); return self(); }
	public B comIsStreamSourceFromFile(boolean v) { this.threadParamsCommon.setIsStreamSourceFromFile(v); return self(); }

	public B comClientIpAddr(@NonNull InetAddress v) { this.threadParamsCommon.setClientIpAddr(v); return self(); }
	public B comClientDestPortRtp(int v) { this.threadParamsCommon.setClientDestPortRtp(v); return self(); }

	public B comRtpSocketUdp(@NonNull DatagramSocket v) { this.threadParamsCommon.setRtpSocketUdp(v); return self(); }

	public B comAvFps(double v) { this.threadParamsCommon.setAvFramesPerSecond(v); return self(); }

	public B comRtpSeqNrT0(short v) { this.threadParamsCommon.setRtpSeqNrT0(v); return self(); }
	public B comRtpTimestampT0(ParamsThreadRtpSenderCommon.@NonNull RtpTsT0 v) { this.threadParamsCommon.setRtpTimestampT0(v); return self(); }
	public B comRtspSsrcId(int v) { this.threadParamsCommon.setRtspSsrcId(v); return self(); }

	public B comXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock v) { this.threadParamsCommon.setXsrcBlockEntry(v); return self(); }
	public B comCbRtcpAppendToOutgoingQueque(@NonNull BiConsumer<@NonNull Integer, @NonNull BufferExt> v) { this.threadParamsCommon.setCbRtcpAppendToOutgoingQueque(v); return self(); }

	public B comCbNotifyThreadReady(@NonNull Consumer<@NonNull Integer> v) { this.threadParamsCommon.setCbNotifyThreadReady(v); return self(); }
	public B comCbThreadMayStartPlayback(@NonNull Supplier<@NonNull Boolean> v) { this.threadParamsCommon.setCbThreadMayStartPlayback(v); return self(); }

	public B comAvStreamIncomingUri(@NonNull URI v) { this.threadParamsCommon.setAvStreamIncomingUri(v); return self(); }

	//
	public abstract T build() throws Exception;

	//
	protected void validateCommon() {
		threadParamsCommon.validate();
	}

}
