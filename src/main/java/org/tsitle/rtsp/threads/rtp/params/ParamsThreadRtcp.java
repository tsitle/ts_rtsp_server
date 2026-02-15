package org.tsitle.rtsp.threads.rtp.params;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public class ParamsThreadRtcp implements Cloneable {

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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getDebugSessionId() { return Optional.ofNullable(debugSessionId); }
	public void setDebugSessionId(String debugSessionId) {
		this.debugSessionId = debugSessionId;
		this.isSetDebugSessionId = true;
	}

	public int getStreamSourceId() { return streamSourceId; }
	public void setStreamSourceId(int streamSourceId) {
		this.streamSourceId = streamSourceId;
		this.isSetStreamSourceId = true;
	}

	public Optional<InetAddress> getClientIpAddr() { return Optional.ofNullable(clientIpAddr); }
	public void setClientIpAddr(InetAddress clientIpAddr) {
		this.clientIpAddr = clientIpAddr;
		this.isSetClientIpAddr = true;
	}

	public int getClientDestPortRtcp() { return clientDestPortRtcp; }
	public void setClientDestPortRtcp(int clientDestPortRtcp) {
		this.clientDestPortRtcp = clientDestPortRtcp;
		this.isSetClientDestPortRtcp = true;
	}

	public Optional<DatagramSocket> getRtcpSocketUdp() { return Optional.ofNullable(rtcpSocketUdp); }
	public void setRtcpSocketUdp(DatagramSocket rtcpSocketUdp) {
		this.rtcpSocketUdp = rtcpSocketUdp;
		this.isSetRtcpSocketUdp = true;
	}

	public int getRtspSsrcId() { return rtspSsrcId; }
	public void setRtspSsrcId(int rtspSsrcId) {
		this.rtspSsrcId = rtspSsrcId;
		this.isSetRtspSsrcId = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtcp clone() {
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
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetDebugSessionId, "debugSessionId");
		requireIsSet(isSetStreamSourceId, "streamSourceId");
		requireIsSet(isSetClientIpAddr, "clientIpAddr");
		requireIsSet(isSetClientDestPortRtcp, "clientDestPortRtcp");
		requireIsSet(isSetRtcpSocketUdp, "rtcpSocketUdp");
		requireIsSet(isSetRtspSsrcId, "rtspSsrcId");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(debugSessionId, "debugSessionId");
		if (debugSessionId.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "debugSessionId must not be empty");
		}
		requireNonNull(clientIpAddr, "clientIpAddr");
		if (clientDestPortRtcp <= 0 || clientDestPortRtcp > 65535) {
			throw new IllegalArgumentException(errPrefix + "clientDestPortRtcp must be > 0 and <= 65535");
		}
		requireNonNull(rtcpSocketUdp, "rtcpSocketUdp");
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
