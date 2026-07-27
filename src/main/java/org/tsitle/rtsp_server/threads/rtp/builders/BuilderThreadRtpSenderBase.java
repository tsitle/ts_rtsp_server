package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.rtsp_server.threads.dataprovider_demux.TdpDemuxReadNextAvPacketInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;

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

	public B comDebugIdSession(@NonNull RtspProtoIdSession v) { this.threadParamsCommon.setDebugSessionId(v); return self(); }

	public B comIsVideoThread(boolean v) { this.threadParamsCommon.setIsVideoThread(v); return self(); }

	public B comIdEsSource(@NonNull RtspProtoIdEsSource v) { this.threadParamsCommon.setIdEsSource(v); return self(); }

	public B comIdSubStream(@NonNull RtspProtoIdSubStream v) { this.threadParamsCommon.setIdSubStream(v); return self(); }

	public B comIdSsrc(@NonNull RtspProtoIdXsrc v) { this.threadParamsCommon.setSsrcId(v); return self(); }

	public B comTpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParamsCommon.setTpClientIpAddr(v); return self(); }
	public B comTpClientDestUdpPortRtp(@NonNull RtspProtoSocketPortNr v) { this.threadParamsCommon.setTpClientDestUdpPort(v); return self(); }
	@SuppressWarnings("UnusedReturnValue")
	public B comTpSocketUdpRtp(@NonNull DatagramSocket v) { this.threadParamsCommon.setTpSocketUdp(v); return self(); }
	public B comTpClientDestTcpIf(RtspChildThreadsCbRtxpTcpInterface v) { this.threadParamsCommon.setTpClientDestTcpIf(v); return self(); }
	@SuppressWarnings("UnusedReturnValue")
	public B comTpClientDestTcpChannRtp(@NonNull RtspProtoTcpChannelNr v) { this.threadParamsCommon.setTpClientDestTcpChann(v); return self(); }

	public B comCryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParamsCommon.setCryptoIsRtxpEncryptionEnabled(v); return self(); }
	public B comCryptoKmdOutboundRtp(@Nullable SrtxpKmd v) { this.threadParamsCommon.setCryptoKmdOutbound(v); return self(); }

	public B comDebugRewindMediaFiles(boolean v) { this.threadParamsCommon.setDebugRewindMediaFiles(v); return self(); }

	public B comEsStreamSourceType(@NonNull RtspProtoEsSourceType v) { this.threadParamsCommon.setEsSourceType(v); return self(); }

	public B comAvFps(double v) { this.threadParamsCommon.setAvFramesPerSecond(v); return self(); }

	public B comRtpSeqNrT0(@NonNull RtspProtoRtpSeqNr v) { this.threadParamsCommon.setRtpSeqNrT0(v); return self(); }
	public B comRtpTimestampT0(ParamsThreadRtpSenderCommon.@NonNull RtpTsT0WithMonoRef v) { this.threadParamsCommon.setRtpTimestampT0WithMonoRef(v); return self(); }

	public B comXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock v) { this.threadParamsCommon.setXsrcBlockEntry(v); return self(); }
	public B comCbRtcpAppendSrToOutgoingQueue(@NonNull BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> v) {
		this.threadParamsCommon.setCbRtcpAppendSrToOutgoingQueue(v);
		return self();
	}
	public B comCbRtcpAppendByeToOutgoingQueue(@NonNull Consumer<@NonNull RtspProtoIdXsrc> v) {
		this.threadParamsCommon.setCbRtcpAppendByeToOutgoingQueue(v);
		return self();
	}

	public B comCbNotifyThreadReady(@NonNull Consumer<@NonNull RtspProtoIdSubStream> v) { this.threadParamsCommon.setCbNotifyThreadReady(v); return self(); }
	public B comCbThreadMayStartPlayback(@NonNull Supplier<@NonNull Boolean> v) { this.threadParamsCommon.setCbThreadMayStartPlayback(v); return self(); }

	public B comAvStreamIncomingUri(@NonNull URI v) { this.threadParamsCommon.setAvStreamIncomingUri(v); return self(); }

	@SuppressWarnings("UnusedReturnValue")
	public B comDemuxReadNextAvPacketInterface(@NonNull TdpDemuxReadNextAvPacketInterface v) { this.threadParamsCommon.setDemuxReadNextAvPacketInterface(v); return self(); }

	//
	public abstract T build() throws Exception;

	//
	protected void validateCommon() {
		threadParamsCommon.validate();
	}

}
