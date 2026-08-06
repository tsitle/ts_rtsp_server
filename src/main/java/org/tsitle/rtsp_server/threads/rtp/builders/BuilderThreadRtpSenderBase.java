package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_dataprov.threads_demux.TdpDemuxReadNextAvPacketInterface;
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
	public BuilderThreadRtpSenderBase<B, T> logMsgInterface(@NonNull LogMsgInterface v) { this.threadParamsCommon.setLogMsgInterface(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comDebugIdSession(@NonNull RtspProtoIdSession v) { this.threadParamsCommon.setDebugSessionId(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comIsVideoThread(boolean v) { this.threadParamsCommon.setIsVideoThread(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comIdEsSource(@NonNull RtspProtoIdEsSource v) { this.threadParamsCommon.setIdEsSource(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comIdSubStream(@NonNull RtspProtoIdSubStream v) { this.threadParamsCommon.setIdSubStream(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comIdSsrc(@NonNull RtspProtoIdXsrc v) { this.threadParamsCommon.setSsrcId(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comTpClientIpAddr(@NonNull RtspProtoIpAddr v) { this.threadParamsCommon.setTpClientIpAddr(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comTpClientDestUdpPortRtp(@NonNull RtspProtoSocketPortNr v) { this.threadParamsCommon.setTpClientDestUdpPort(v); return this; }
	@SuppressWarnings("UnusedReturnValue")
	public BuilderThreadRtpSenderBase<B, T> comTpSocketUdpRtp(@NonNull DatagramSocket v) { this.threadParamsCommon.setTpSocketUdp(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comTpClientDestTcpIf(RtspChildThreadsCbRtxpTcpInterface v) { this.threadParamsCommon.setTpClientDestTcpIf(v); return this; }
	@SuppressWarnings("UnusedReturnValue")
	public BuilderThreadRtpSenderBase<B, T> comTpClientDestTcpChannRtp(@NonNull RtspProtoTcpChannelNr v) { this.threadParamsCommon.setTpClientDestTcpChann(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comCryptoIsRtxpEncryptionEnabled(boolean v) { this.threadParamsCommon.setCryptoIsRtxpEncryptionEnabled(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comCryptoKmdOutboundRtp(@Nullable SrtxpKmd v) { this.threadParamsCommon.setCryptoKmdOutbound(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comDebugRewindMediaFiles(boolean v) { this.threadParamsCommon.setDebugRewindMediaFiles(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comEsStreamSourceType(@NonNull RtspProtoEsSourceType v) { this.threadParamsCommon.setEsSourceType(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comAvFps(double v) { this.threadParamsCommon.setAvFramesPerSecond(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comRtpSeqNrT0(@NonNull RtspProtoRtpSeqNr v) { this.threadParamsCommon.setRtpSeqNrT0(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comRtpTimestampT0(ParamsThreadRtpSenderCommon.@NonNull RtpTsT0WithMonoRef v) { this.threadParamsCommon.setRtpTimestampT0WithMonoRef(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comXsrcBlockEntry(@NonNull RtcpInnerXsrcBlock v) { this.threadParamsCommon.setXsrcBlockEntry(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comCbRtcpAppendSrToOutgoingQueue(@NonNull BiConsumer<@NonNull RtspProtoIdXsrc, @NonNull BufferExt> v) {
		this.threadParamsCommon.setCbRtcpAppendSrToOutgoingQueue(v);
		return this;
	}
	public BuilderThreadRtpSenderBase<B, T> comCbRtcpAppendByeToOutgoingQueue(@NonNull Consumer<@NonNull RtspProtoIdXsrc> v) {
		this.threadParamsCommon.setCbRtcpAppendByeToOutgoingQueue(v);
		return this;
	}

	public BuilderThreadRtpSenderBase<B, T> comCbNotifyThreadReady(@NonNull Consumer<@NonNull RtspProtoIdSubStream> v) { this.threadParamsCommon.setCbNotifyThreadReady(v); return this; }
	public BuilderThreadRtpSenderBase<B, T> comCbThreadMayStartPlayback(@NonNull Supplier<@NonNull Boolean> v) { this.threadParamsCommon.setCbThreadMayStartPlayback(v); return this; }

	public BuilderThreadRtpSenderBase<B, T> comAvStreamIncomingUri(@NonNull URI v) { this.threadParamsCommon.setAvStreamIncomingUri(v); return this; }

	@SuppressWarnings("UnusedReturnValue")
	public BuilderThreadRtpSenderBase<B, T> comDemuxReadNextAvPacketInterface(@NonNull TdpDemuxReadNextAvPacketInterface v) { this.threadParamsCommon.setDemuxReadNextAvPacketInterface(v); return this; }

	//
	public abstract T build();

	//
	protected void validateCommon() {
		threadParamsCommon.validate();
	}

}
