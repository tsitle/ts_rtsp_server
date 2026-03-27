package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class ParamsThreadRtcp implements Cloneable {

	/** Logging interface */
	private LogMsgInterface logMsgInterface;
	private boolean isSetLogMsgInterface;

	/** Session ID */
	private String debugSessionId;
	private boolean isSetDebugSessionId;

	/** Stream ID - not the SSRC */
	private int streamSourceId;
	private boolean isSetStreamSourceId;

	/** Client IP address */
	private InetAddress clientIpAddr;
	private boolean isSetClientIpAddr;
	/** Destination port for RTCP packets, provided by the RTSP Client */
	private int clientDestPortRtcp;
	private boolean isSetClientDestPortRtcp;

	/** UDP socket for outgoing RTCP packets */
	private DatagramSocket rtcpSocketUdp;
	private boolean isSetRtcpSocketUdp;

	/** RTSP Synchronization Source Identifier of the stream */
	private int rtspSsrcId;
	private boolean isSetRtspSsrcId;

	/** Is RTP/RTCP encryption enabled? */
	private boolean isRtxpEncryptionEnabled;
	private boolean isSetIsRtxpEncryptionEnabled;
	/** SRTxP KMD for inbound messages */
	private SrtxpKmd srtxpKmdInbound;
	private boolean isSetSrtxpKmdInbound;
	/** SRTxP KMD for outbound messages */
	private SrtxpKmd srtxpKmdOutbound;
	private boolean isSetSrtxpKmdOutbound;

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

	public Optional<InetAddress> getClientIpAddr() { return Optional.ofNullable(clientIpAddr); }
	public void setClientIpAddr(@NonNull InetAddress clientIpAddr) {
		this.clientIpAddr = clientIpAddr;
		this.isSetClientIpAddr = true;
	}

	public int getClientDestPortRtcp() { return clientDestPortRtcp; }
	public void setClientDestPortRtcp(int clientDestPortRtcp) {
		this.clientDestPortRtcp = clientDestPortRtcp;
		this.isSetClientDestPortRtcp = true;
	}

	public Optional<DatagramSocket> getRtcpSocketUdp() { return Optional.ofNullable(rtcpSocketUdp); }
	public void setRtcpSocketUdp(@NonNull DatagramSocket rtcpSocketUdp) {
		this.rtcpSocketUdp = rtcpSocketUdp;
		this.isSetRtcpSocketUdp = true;
	}

	@SuppressWarnings("unused")
	public int getRtspSsrcId() { return rtspSsrcId; }
	public void setRtspSsrcId(int rtspSsrcId) {
		this.rtspSsrcId = rtspSsrcId;
		this.isSetRtspSsrcId = true;
	}

	public boolean getIsRtxpEncryptionEnabled() { return isRtxpEncryptionEnabled; }
	public void setIsRtxpEncryptionEnabled(boolean value) {
		this.isRtxpEncryptionEnabled = value;
		this.isSetIsRtxpEncryptionEnabled = true;
	}

	public Optional<SrtxpKmd> getSrtxpKmdInbound() { return Optional.ofNullable(srtxpKmdInbound); }
	public void setSrtxpKmdInbound(@NonNull SrtxpKmd value) {
		this.srtxpKmdInbound = value.clone();
		this.isSetSrtxpKmdInbound = true;
	}

	public Optional<SrtxpKmd> getSrtxpKmdOutbound() { return Optional.ofNullable(srtxpKmdOutbound); }
	public void setSrtxpKmdOutbound(@NonNull SrtxpKmd value) {
		this.srtxpKmdOutbound = value.clone();
		this.isSetSrtxpKmdOutbound = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtcp clone() {
		try {
			ParamsThreadRtcp clone = (ParamsThreadRtcp)super.clone();
			//
			try {
				if (clientIpAddr != null) {
					clone.clientIpAddr = InetAddress.getByAddress(clientIpAddr.getAddress());
				}
			} catch (UnknownHostException e) {
				// this should never happen
				throw new RuntimeException(e);
			}
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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetLogMsgInterface, "logMsgInterface");
		requireIsSet(isSetDebugSessionId, "debugSessionId");
		requireIsSet(isSetStreamSourceId, "streamSourceId");
		requireIsSet(isSetClientIpAddr, "clientIpAddr");
		requireIsSet(isSetClientDestPortRtcp, "clientDestPortRtcp");
		requireIsSet(isSetRtcpSocketUdp, "rtcpSocketUdp");
		requireIsSet(isSetRtspSsrcId, "rtspSsrcId");

		requireIsSet(isSetIsRtxpEncryptionEnabled, "isRtxpEncryptionEnabled");
		requireIsSet(isSetSrtxpKmdInbound, "srtxpKmdInbound");
		requireIsSet(isSetSrtxpKmdOutbound, "srtxpKmdOutbound");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(logMsgInterface, "logMsgInterface");

		requireNonNull(debugSessionId, "debugSessionId");
		if (debugSessionId.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "debugSessionId must not be empty");
		}
		requireNonNull(clientIpAddr, "clientIpAddr");
		if (clientDestPortRtcp <= 0 || clientDestPortRtcp > 65535) {
			throw new IllegalArgumentException(errPrefix + "clientDestPortRtcp must be > 0 and <= 65535");
		}
		requireNonNull(rtcpSocketUdp, "rtcpSocketUdp");

		requireNonNull(srtxpKmdInbound, "srtxpKmdInbound");
		requireNonNull(srtxpKmdOutbound, "srtxpKmdOutbound");
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtcp.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtcp.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
