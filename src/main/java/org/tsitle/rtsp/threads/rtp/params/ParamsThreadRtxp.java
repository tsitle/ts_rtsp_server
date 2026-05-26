package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public abstract class ParamsThreadRtxp implements Cloneable {

	public static class Transport implements Cloneable {
		/** Client IP address */
		private InetAddress clientIpAddr;
		private boolean isSetClientIpAddr;

		/** Destination UDP port for RTxP packets (audio and video), provided by the RTSP Client */
		private int clientDestUdpPort;
		private boolean isSetClientDestUdpPort;
		/** UDP socket for outgoing RTxP packets */
		private DatagramSocket socketUdp;
		private boolean isSetSocketUdp;

		/** Destination TCP read/write interface for RTxP packets (audio and video) */
		private RtxpTcpReadWrite clientDestTcpIf;
		private boolean isSetClientDestTcpIf;
		/** Destination TCP channel for RTxP packets (audio and video), provided by the RTSP Client */
		private int clientDestTcpChann;
		private boolean isSetClientDestTcpChann;

		@Override
		public @NonNull Transport clone() {
			try {
				Transport clone = (Transport)super.clone();
				//
				try {
					if (clientIpAddr != null) {
						clone.clientIpAddr = InetAddress.getByAddress(clientIpAddr.getAddress());
					}
				} catch (UnknownHostException e) {
					// this should never happen
					throw new RuntimeException(e);
				}
				return clone;
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
		private SrtxpKmd srtxpKmdInbound;
		private boolean isSetSrtxpKmdInbound;
		/** SRTxP KMD for outbound messages */
		private SrtxpKmd srtxpKmdOutbound;
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
	private LogMsgInterface logMsgInterface;
	private boolean isSetLogMsgInterface;

	/** Session ID */
	private String debugSessionId;
	private boolean isSetDebugSessionId;

	/** Stream ID - not the SSRC */
	private int streamSourceId;
	private boolean isSetStreamSourceId;

	/** RTSP Synchronization Source Identifier of the stream */
	private int rtspSsrcId;
	private boolean isSetRtspSsrcId;

	/** RTxP UDP/TCP transport parameters */
	private final Transport transport = new Transport();

	/** RTxP encryption parameters */
	private final Crypto crypto = new Crypto();

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
	public Optional<String> getDebugSessionId() { return Optional.ofNullable(debugSessionId); }
	public void setDebugSessionId(@NonNull String debugSessionId) {
		this.debugSessionId = debugSessionId;
		this.isSetDebugSessionId = true;
	}

	@SuppressWarnings("unused")
	public int getStreamSourceId() { return streamSourceId; }
	public void setStreamSourceId(int streamSourceId) {
		this.streamSourceId = streamSourceId;
		this.isSetStreamSourceId = true;
	}

	@SuppressWarnings("unused")
	public int getRtspSsrcId() { return rtspSsrcId; }
	public void setRtspSsrcId(int rtspSsrcId) {
		this.rtspSsrcId = rtspSsrcId;
		this.isSetRtspSsrcId = true;
	}

	public Optional<InetAddress> getTpClientIpAddr() { return Optional.ofNullable(transport.clientIpAddr); }
	public void setTpClientIpAddr(@NonNull InetAddress clientIpAddr) {
		this.transport.clientIpAddr = clientIpAddr;
		this.transport.isSetClientIpAddr = true;
	}

	public int getTpClientDestUdpPort() { return transport.clientDestUdpPort; }
	public void setTpClientDestUdpPort(int value) {
		this.transport.clientDestUdpPort = value;
		this.transport.isSetClientDestUdpPort = true;
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

	public int getTpClientDestTcpChann() { return transport.clientDestTcpChann; }
	public void setTpClientDestTcpChann(int value) {
		this.transport.clientDestTcpChann = value;
		this.transport.isSetClientDestTcpChann = true;
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
			//noinspection StringOperationCanBeSimplified
			clone.debugSessionId = new String(debugSessionId);
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
		requireIsSet(isSetStreamSourceId, "streamSourceId");

		requireIsSet(isSetRtspSsrcId, "rtspSsrcId");

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

		requireNonNull(transport.clientIpAddr, "transport.clientIpAddr");
		if (transport.isSetClientDestUdpPort) {
			if (transport.clientDestUdpPort <= 0 || transport.clientDestUdpPort > 65535) {
				throw new IllegalArgumentException(errPrefix + "transport.clientDestUdpPort must be > 0 and <= 65535");
			}
			requireNonNull(transport.socketUdp, "transport.socketUdp");
		} else {
			requireNonNull(transport.clientDestTcpIf, "transport.clientDestTcpIf");
			if (transport.clientDestTcpChann < 0 || transport.clientDestTcpChann > 255) {
				throw new IllegalArgumentException(errPrefix + "transport.clientDestTcpChann must be >= 0 and <= 255");
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
