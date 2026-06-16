package org.tsitle.rtsp.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

import java.net.DatagramSocket;
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

	public B comDebugSessionId(@NonNull RtspProtoIdSession v) { this.threadParamsCommon.setDebugSessionId(v); return self(); }

	public B comIdStreamSource(@NonNull RtspProtoIdStreamSource v) { this.threadParamsCommon.setIdStreamSource(v); return self(); }

	public B comSsrcId(@NonNull RtspProtoIdXsrc v) { this.threadParamsCommon.setSsrcId(v); return self(); }

	public B comTpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParamsCommon.setTpClientIpAddr(v); return self(); }
	public B comTpClientDestUdpPortRtp(@NonNull RtspProtoSocketPortNr v) { this.threadParamsCommon.setTpClientDestUdpPort(v); return self(); }
	@SuppressWarnings("UnusedReturnValue")
	public B comTpSocketUdpRtp(@NonNull DatagramSocket v) { this.threadParamsCommon.setTpSocketUdp(v); return self(); }
	public B comTpClientDestTcpIf(RtxpTcpReadWrite v) { this.threadParamsCommon.setTpClientDestTcpIf(v); return self(); }
	@SuppressWarnings("UnusedReturnValue")
	public B comTpClientDestTcpChannRtp(@NonNull RtspProtoTcpChannelNr v) { this.threadParamsCommon.setTpClientDestTcpChann(v); return self(); }

	public B comCryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParamsCommon.setCryptoIsRtxpEncryptionEnabled(v); return self(); }
	public B comCryptoKmdOutboundRtp(@Nullable SrtxpKmd v) { this.threadParamsCommon.setCryptoKmdOutbound(v); return self(); }

	public B comDebugRewindMediaFiles(boolean v) { this.threadParamsCommon.setDebugRewindMediaFiles(v); return self(); }

	public B comIsStreamSourceFromFile(boolean v) { this.threadParamsCommon.setIsStreamSourceFromFile(v); return self(); }

	public B comAvFps(double v) { this.threadParamsCommon.setAvFramesPerSecond(v); return self(); }

	public B comRtpSeqNrT0(short v) { this.threadParamsCommon.setRtpSeqNrT0(v); return self(); }
	public B comRtpTimestampT0(ParamsThreadRtpSenderCommon.@NonNull RtpTsT0 v) { this.threadParamsCommon.setRtpTimestampT0(v); return self(); }

	public B comXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock v) { this.threadParamsCommon.setXsrcBlockEntry(v); return self(); }
	public B comCbRtcpAppendToOutgoingQueue(@NonNull BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> v) {
		this.threadParamsCommon.setCbRtcpAppendToOutgoingQueue(v);
		return self();
	}

	public B comCbNotifyThreadReady(@NonNull Consumer<@NonNull RtspProtoIdStreamSource> v) { this.threadParamsCommon.setCbNotifyThreadReady(v); return self(); }
	public B comCbThreadMayStartPlayback(@NonNull Supplier<@NonNull Boolean> v) { this.threadParamsCommon.setCbThreadMayStartPlayback(v); return self(); }

	public B comAvStreamIncomingUri(@NonNull URI v) { this.threadParamsCommon.setAvStreamIncomingUri(v); return self(); }

	//
	public abstract T build() throws Exception;

	//
	protected void validateCommon() {
		threadParamsCommon.validate();
	}

}
