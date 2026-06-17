package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.proto.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

import java.net.DatagramSocket;
import java.util.Optional;

public abstract class ParamsThreadRtxp implements Cloneable {

	public static class Transport implements Cloneable {
		/** Client IP address */
		private @NonNull RtspProtoIpAddr clientIpAddr = new RtspProtoIpAddr();
		private boolean isSetClientIpAddr;

		/** Destination UDP port for RTxP packets (audio and video), provided by the RTSP Client */
		private @NonNull RtspProtoSocketPortNr clientDestUdpPort = RtspProtoSocketPortNr.ofEmpty();
		private boolean isSetClientDestUdpPort;
		/** UDP socket for outgoing RTxP packets */
		private @Nullable DatagramSocket socketUdp;
		private boolean isSetSocketUdp;

		/** Destination TCP read/write interface for RTxP packets (audio and video) */
		private @Nullable RtxpTcpReadWrite clientDestTcpIf;
		private boolean isSetClientDestTcpIf;
		/** Destination TCP channel for RTxP packets (audio and video), provided by the RTSP Client */
		private @NonNull RtspProtoTcpChannelNr clientDestTcpChann = RtspProtoTcpChannelNr.ofEmpty();
		private boolean isSetClientDestTcpChann;

		@Override
		public @NonNull Transport clone() {
			try {
				Transport cloned = (Transport)super.clone();
				cloned.clientIpAddr = clientIpAddr.clone();
				cloned.clientDestUdpPort = clientDestUdpPort.clone();
				cloned.clientDestTcpChann = clientDestTcpChann.clone();
				return cloned;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	public static class Crypto implements Cloneable {
		private boolean needInboundParams;
		private boolean needOutboundParams;

		/** Is RTP/RTCP encryption enabled? */
		private boolean isRtxpEncryptionEnabled;
		private boolean isSetIsRtxpEncryptionEnabled;

		/** SRTxP KMD for inbound messages */
		private @Nullable SrtxpKmd srtxpKmdInbound;
		private boolean isSetSrtxpKmdInbound;
		/** SRTxP KMD for outbound messages */
		private @Nullable SrtxpKmd srtxpKmdOutbound;
		private boolean isSetSrtxpKmdOutbound;

		@Override
		public @NonNull Crypto clone() {
			try {
				Crypto clone = (Crypto)super.clone();
				//
				if (srtxpKmdInbound != null) {
					clone.srtxpKmdInbound = srtxpKmdInbound.clone();
				}
				if (srtxpKmdOutbound != null) {
					clone.srtxpKmdOutbound = srtxpKmdOutbound.clone();
				}
				return clone;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Logging interface */
	private @Nullable LogMsgInterface logMsgInterface = null;
	private boolean isSetLogMsgInterface;

	/** Session ID */
	private @NonNull RtspProtoIdSession debugSessionId = RtspProtoIdSession.ofEmpty();
	private boolean isSetDebugSessionId;

	/** Stream ID - not the SSRC */
	private @NonNull RtspProtoIdStreamSource idStreamSource = RtspProtoIdStreamSource.ofEmpty();
	private boolean isSetIdStreamSource;

	/** RTSP Synchronization Source Identifier of the stream */
	private @NonNull RtspProtoIdXsrc ssrcId = RtspProtoIdXsrc.ofEmpty();
	private boolean isSetSsrcId;

	/** RTxP UDP/TCP transport parameters */
	private @NonNull Transport transport = new Transport();

	/** RTxP encryption parameters */
	private @NonNull Crypto crypto = new Crypto();

	protected ParamsThreadRtxp(boolean needInboundParams, boolean needOutboundParams) {
		this.crypto.needInboundParams = needInboundParams;
		this.crypto.needOutboundParams = needOutboundParams;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<LogMsgInterface> getLogMsgInterface() { return Optional.ofNullable(logMsgInterface); }
	public void setLogMsgInterface(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
		this.isSetLogMsgInterface = true;
	}

	@SuppressWarnings("unused")
	public @NonNull RtspProtoIdSession getDebugSessionId() {
		return debugSessionId.clone();
	}
	public void setDebugSessionId(@NonNull RtspProtoIdSession debugSessionId) {
		this.debugSessionId.copyFrom(debugSessionId);
		this.debugSessionId.writeProtect();
		this.isSetDebugSessionId = true;
	}

	public @NonNull RtspProtoIdStreamSource getIdStreamSource() { return idStreamSource; }
	public void setIdStreamSource(@NonNull RtspProtoIdStreamSource idStreamSource) {
		this.idStreamSource.copyFrom(idStreamSource);
		this.idStreamSource.writeProtect();
		this.isSetIdStreamSource = true;
	}

	public @NonNull RtspProtoIdXsrc getSsrcId() { return ssrcId.clone(); }
	public void setSsrcId(@NonNull RtspProtoIdXsrc value) {
		this.ssrcId.copyFrom(value);
		this.ssrcId.writeProtect();
		this.isSetSsrcId = true;
	}

	public @NonNull RtspProtoIpAddr getTpClientIpAddr() {
		return transport.clientIpAddr.clone();
	}
	public void setTpClientIpAddr(@NonNull RtspProtoIpAddr clientIpAddr) {
		transport.clientIpAddr.copyFrom(clientIpAddr);
		transport.clientIpAddr.writeProtect();
		transport.isSetClientIpAddr = true;
	}

	public @NonNull RtspProtoSocketPortNr getTpClientDestUdpPort() { return transport.clientDestUdpPort; }
	public void setTpClientDestUdpPort(@NonNull RtspProtoSocketPortNr value) {
		transport.clientDestUdpPort.copyFrom(value);
		transport.clientDestUdpPort.writeProtect();
		transport.isSetClientDestUdpPort = true;
	}

	public Optional<DatagramSocket> getTpSocketUdp() { return Optional.ofNullable(transport.socketUdp); }
	public void setTpSocketUdp(@NonNull DatagramSocket value) {
		this.transport.socketUdp = value;
		this.transport.isSetSocketUdp = true;
	}

	public Optional<RtxpTcpReadWrite> getTpClientDestTcpIf() { return Optional.ofNullable(transport.clientDestTcpIf); }
	public void setTpClientDestTcpIf(@NonNull RtxpTcpReadWrite value) {
		this.transport.clientDestTcpIf = value;
		this.transport.isSetClientDestTcpIf = true;
	}

	public @NonNull RtspProtoTcpChannelNr getTpClientDestTcpChann() { return transport.clientDestTcpChann; }
	public void setTpClientDestTcpChann(@NonNull RtspProtoTcpChannelNr value) {
		transport.clientDestTcpChann.copyFrom(value);
		transport.clientDestTcpChann.writeProtect();
		transport.isSetClientDestTcpChann = true;
	}

	public boolean getCryptoIsRtxpEncryptionEnabled() { return crypto.isRtxpEncryptionEnabled; }
	public void setCryptoIsRtxpEncryptionEnabled(boolean value) {
		this.crypto.isRtxpEncryptionEnabled = value;
		this.crypto.isSetIsRtxpEncryptionEnabled = true;
	}

	public Optional<SrtxpKmd> getCryptoKmdInbound() { return Optional.ofNullable(crypto.srtxpKmdInbound); }
	public void setCryptoKmdInbound(@Nullable SrtxpKmd value) {
		this.crypto.srtxpKmdInbound = (value == null ? null : value.clone());
		this.crypto.isSetSrtxpKmdInbound = true;
	}

	public Optional<SrtxpKmd> getCryptoKmdOutbound() { return Optional.ofNullable(crypto.srtxpKmdOutbound); }
	public void setCryptoKmdOutbound(@Nullable SrtxpKmd value) {
		this.crypto.srtxpKmdOutbound = (value == null ? null : value.clone());
		this.crypto.isSetSrtxpKmdOutbound = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtxp clone() {
		try {
			ParamsThreadRtxp clone = (ParamsThreadRtxp)super.clone();
			//
			clone.debugSessionId = debugSessionId.clone();
			clone.idStreamSource = idStreamSource.clone();
			clone.ssrcId = ssrcId.clone();
			clone.transport = transport.clone();
			clone.crypto = crypto.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetLogMsgInterface, "logMsgInterface");
		requireIsSet(isSetDebugSessionId, "debugSessionId");
		requireIsSet(isSetIdStreamSource, "streamSourceId");

		requireIsSet(isSetSsrcId, "ssrcId");

		requireIsSet(transport.isSetClientIpAddr, "transport.clientIpAddr");
		if (transport.isSetClientDestUdpPort) {
			requireIsSet(transport.isSetSocketUdp, "transport.socketUdp");
		} else {
			requireIsSet(transport.isSetClientDestTcpIf, "transport.clientDestTcpIf");
			requireIsSet(transport.isSetClientDestTcpChann, "transport.clientDestTcpChann");
		}

		if (crypto.needInboundParams) {
			requireIsSet(crypto.isSetSrtxpKmdInbound, "crypto.srtxpKmdInbound");
		}
		if (crypto.needOutboundParams) {
			requireIsSet(crypto.isSetSrtxpKmdOutbound, "crypto.srtxpKmdOutbound");
		}
		requireIsSet(crypto.isSetIsRtxpEncryptionEnabled, "crypto.isRtxpEncryptionEnabled");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(logMsgInterface, "logMsgInterface");

		requireNonNull(debugSessionId, "debugSessionId");
		if (debugSessionId.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "debugSessionId must not be empty");
		}

		if (ssrcId.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "ssrcId must be set");
		}

		requireNonNull(transport.clientIpAddr, "transport.clientIpAddr");
		if (transport.isSetClientDestUdpPort) {
			if (transport.clientDestUdpPort.isEmpty()) {
				throw new IllegalArgumentException(errPrefix + "transport.clientDestUdpPort must be set");
			}
			requireNonNull(transport.socketUdp, "transport.socketUdp");
		} else {
			requireNonNull(transport.clientDestTcpIf, "transport.clientDestTcpIf");
			if (transport.clientDestTcpChann.isEmpty()) {
				throw new IllegalArgumentException(errPrefix + "transport.clientDestTcpChann must be set");
			}
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtxp.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtxp.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
