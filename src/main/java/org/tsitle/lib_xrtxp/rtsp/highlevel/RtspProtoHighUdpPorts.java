package org.tsitle.lib_xrtxp.rtsp.highlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoCouldNotFindUdpPortsException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;

import java.net.DatagramSocket;
import java.net.SocketException;

public final class RtspProtoHighUdpPorts {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private RtspProtoHighUdpPorts() { }

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC-3551 Section 8
	 */
	public static void findAndOpenUdpSocketPorts(
				boolean isForServer,
				@NonNull RtspProtoSetupInfoForSubStream siSs
			) throws UdpSocketIoException, RtspProtoCouldNotFindUdpPortsException {
		final String FNC_NAME = RtspProtoHighUdpPorts.class.getSimpleName() + ".findAndOpenUdpSocketPorts()";

		if (! siSs.getSubStreamTpPtr().getIsUdp()) {
			return;
		}

		if (isForServer) {
			if (siSs.getServerUdpSocketRtpPtr() != null) { siSs.getServerUdpSocketRtpPtr().close(); }
			if (siSs.getServerUdpSocketRtcpPtr() != null) { siSs.getServerUdpSocketRtcpPtr().close(); }
		} else {
			if (siSs.getClientUdpSocketRtpPtr() != null) { siSs.getClientUdpSocketRtpPtr().close(); }
			if (siSs.getClientUdpSocketRtcpPtr() != null) { siSs.getClientUdpSocketRtcpPtr().close(); }
		}

		//
		DatagramSocket tmpSocketRtp = null;
		DatagramSocket tmpSocketRtcp = null;

		int loopCnt = 0;
		boolean isOk = false;
		while (++loopCnt <= 1000) {
			if (tmpSocketRtp != null) {
				tmpSocketRtp.close();
			}
			try {
				tmpSocketRtp = new DatagramSocket();
				if (tmpSocketRtp.getLocalPort() % 2 != 0) {
					continue;
				}
				tmpSocketRtcp = new DatagramSocket(
						tmpSocketRtp.getLocalPort() + 1
					);
				isOk = true;
				break;
			} catch (SocketException e) {
				// keep going until we find a free port pair
			}
		}
		if (! isOk) {
			throw new RtspProtoCouldNotFindUdpPortsException(FNC_NAME + ": Could not find proper UDP sockets");
		}
		try {
			tmpSocketRtp.setSoTimeout(SOCKET_UDP_RTP_TIMEOUT_MS);
			tmpSocketRtp.setSendBufferSize(1024 * 1024);  // this is only a hint, not the actual buffer size
			tmpSocketRtcp.setSoTimeout(SOCKET_UDP_RTCP_TIMEOUT_MS);
			tmpSocketRtcp.setSendBufferSize(1024 * 64);  // this is only a hint, not the actual buffer size
		} catch (SocketException e) {
			throw new UdpSocketIoException(FNC_NAME + ": Could not configure UDP sockets: " + e.getMessage());
		}

		if (isForServer) {
			siSs.setServerUdpSocketRtpPtr(tmpSocketRtp);
			siSs.setServerUdpSocketRtcpPtr(tmpSocketRtcp);
		} else {
			siSs.setClientUdpSocketRtpPtr(tmpSocketRtp);
			siSs.setClientUdpSocketRtcpPtr(tmpSocketRtcp);
		}
	}

}
