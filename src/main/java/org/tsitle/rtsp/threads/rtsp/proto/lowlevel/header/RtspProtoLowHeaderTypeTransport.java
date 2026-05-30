package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeTransport {

	/** Client's incoming UDP port for RTP packets (audio and video), provided by the RTSP Client */
	public int tpClientDestUdpPortRtp = 0;
	/** Client's outgoing UDP port for RTCP packets (meta information), provided by the RTSP Client */
	public int tpClientDestUdpPortRtcp = 0;
	/** Client's incoming TCP channel for RTP packets (audio and video), provided by the RTSP Client */
	public int tpClientDestTcpChannRtp = -1;
	/** Client's outgoing TCP channel for RTCP packets (meta information), provided by the RTSP Client */
	public int tpClientDestTcpChannRtcp = -1;
	/** Requested transport type protocol (true: UDP, false: TCP) */
	public boolean tpIsUdp = false;
	/** Requested transport casting type (true: unicast, false: multicast) */
	public boolean tpIsUnicast = false;
	/** Requested transport interleaved mode (true: interleaved (requires TCP), false: separate (requires UDP)) */
	public boolean tpIsInterleaved = false;
	/** Requested transport encryption type (true: SRTP/SRTCP, false: plain RTP/RTCP) */
	public boolean tpIsEncr = false;

	@Override
	public @NonNull String toString() {
		return "[" +
				"tpIsUdp=" + tpIsUdp +
				(tpIsUdp ? ", tpClientDestUdpPortRtp=" + tpClientDestUdpPortRtp : "") +
				(tpIsUdp ? ", tpClientDestUdpPortRtcp=" + tpClientDestUdpPortRtcp : "") +
				(tpIsUdp ? "" : ", tpClientDestTcpChannRtp=" + tpClientDestTcpChannRtp) +
				(tpIsUdp ? "" : ", tpClientDestTcpChannRtcp=" + tpClientDestTcpChannRtcp) +
				", tpIsUnicast=" + tpIsUnicast +
				(tpIsUdp ? "" : ", tpIsInterleaved=" + tpIsInterleaved) +
				", tpIsEncr=" + tpIsEncr +
				"]";
	}

}
