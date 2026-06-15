package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidTpSettingsException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

public final class RtspProtoDataCntSubStreamTp {

	private boolean isWriteProtected = false;

	/** Client's UDP port for inbound RTP packets */
	public final @NonNull RtspProtoSocketPortNr tpClientUdpPortRtp = new RtspProtoSocketPortNr();
	/** Client's UDP port for inbound/outbound RTCP packets */
	public final @NonNull RtspProtoSocketPortNr tpClientUdpPortRtcp = new RtspProtoSocketPortNr();
	/** Server's UDP port for outbound RTP packets */
	public final @NonNull RtspProtoSocketPortNr tpServerUdpPortRtp = new RtspProtoSocketPortNr();
	/** Server's UDP port for inbound/outbound RTCP packets */
	public final @NonNull RtspProtoSocketPortNr tpServerUdpPortRtcp = new RtspProtoSocketPortNr();
	/** Client's TCP channel for inbound RTP packets */
	public final @NonNull RtspProtoTcpChannelNr tpClientTcpChannRtp = new RtspProtoTcpChannelNr();
	/** Client's TCP channel for inbound/outbound RTCP packets */
	public final @NonNull RtspProtoTcpChannelNr tpClientTcpChannRtcp = new RtspProtoTcpChannelNr();
	/** Transport type protocol (true: UDP, false: TCP) */
	private boolean tpIsUdp = false;
	/** Transport delivery type (true: unicast, false: multicast) */
	private boolean tpIsUnicast = false;
	/** Transport interleaved mode (true: interleaved (requires TCP), false: separate (requires UDP)) */
	private boolean tpIsInterleaved = false;
	/** Transport encryption type (true: SRTP/SRTCP, false: plain RTP/RTCP) */
	private boolean tpIsEncr = false;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean getIsUdp() {
		return tpIsUdp;
	}
	public void setIsUdp(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tpIsUdp = value;
	}

	public boolean getIsUnicast() {
		return tpIsUnicast;
	}
	public void setIsUnicast(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tpIsUnicast = value;
	}

	public boolean getIsInterleaved() {
		return tpIsInterleaved;
	}
	public void setIsInterleaved(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tpIsInterleaved = value;
	}

	public boolean getIsEncr() {
		return tpIsEncr;
	}
	public void setIsEncr(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tpIsEncr = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void isTransportValid(
				boolean needsEncryption,
				boolean forceEncryption,
				boolean isRtspsConnection,
				boolean isTransportUdpDisabled
			) throws RtspInvalidTpSettingsException {
		if (! tpIsEncr && ((needsEncryption && ! isRtspsConnection) || forceEncryption)) {
			throw new RtspInvalidTpSettingsException("Client requested unencrypted transport, but encryption is required");
		}
		if (tpIsEncr && ! (needsEncryption || forceEncryption)) {
			throw new RtspInvalidTpSettingsException("Client requested encrypted transport, but encryption is disabled");
		}
		if (! tpIsUnicast) {
			throw new RtspInvalidTpSettingsException("Multicast is not supported");
		}
		if (tpIsUdp) {
			if (tpIsInterleaved) {
				throw new RtspInvalidTpSettingsException("Interleaved mode is not supported for UDP");
			}
			if (tpClientUdpPortRtp.isEmpty() || tpClientUdpPortRtcp.isEmpty()) {
				throw new RtspInvalidTpSettingsException("Client UDP ports not set");
			}
			if (tpClientUdpPortRtp.equals(tpClientUdpPortRtcp)) {
				throw new RtspInvalidTpSettingsException("Client UDP ports for RTP and RTCP cannot be the same");
			}
			if (isRtspsConnection && ! tpIsEncr) {
				throw new RtspInvalidTpSettingsException("UDP cannot be used with RTSPS w/o SRTP");
			}
			if (isTransportUdpDisabled) {
				throw new RtspInvalidTpSettingsException("UDP is disabled");
			}
			return;
		}
		if (! tpIsInterleaved) {
			throw new RtspInvalidTpSettingsException("Interleaved mode must be used for TCP");
		}
		if (tpClientTcpChannRtp.isEmpty() || tpClientTcpChannRtcp.isEmpty()) {
			throw new RtspInvalidTpSettingsException("Client TCP channel IDs not set");
		}
		if (tpClientTcpChannRtp.equals(tpClientTcpChannRtcp)) {
			throw new RtspInvalidTpSettingsException("Client TCP channel IDs for RTP and RTCP cannot be the same");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tpClientUdpPortRtp.clear();
		tpClientUdpPortRtcp.clear();
		tpServerUdpPortRtp.clear();
		tpServerUdpPortRtcp.clear();
		tpClientTcpChannRtp.clear();
		tpClientTcpChannRtcp.clear();
		tpIsUdp = false;
		tpIsUnicast = false;
		tpIsInterleaved = false;
		tpIsEncr = false;
	}

	public void copyFrom(@NonNull RtspProtoDataCntSubStreamTp other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		tpClientUdpPortRtp.copyFrom(other.tpClientUdpPortRtp);
		tpClientUdpPortRtcp.copyFrom(other.tpClientUdpPortRtcp);
		tpServerUdpPortRtp.copyFrom(other.tpServerUdpPortRtp);
		tpServerUdpPortRtcp.copyFrom(other.tpServerUdpPortRtcp);
		tpClientTcpChannRtp.copyFrom(other.tpClientTcpChannRtp);
		tpClientTcpChannRtcp.copyFrom(other.tpClientTcpChannRtcp);
		tpIsUdp = other.tpIsUdp;
		tpIsUnicast = other.tpIsUnicast;
		tpIsInterleaved = other.tpIsInterleaved;
		tpIsEncr = other.tpIsEncr;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"tpIsUdp=" + (tpIsUdp ? "T" : "F") +
				(tpIsUdp ? ", tpClientUdpPortRtp=" + tpClientUdpPortRtp : "") +
				(tpIsUdp ? ", tpClientUdpPortRtcp=" + tpClientUdpPortRtcp : "") +
				(tpIsUdp ? ", tpServerUdpPortRtp=" + tpServerUdpPortRtp : "") +
				(tpIsUdp ? ", tpServerUdpPortRtcp=" + tpServerUdpPortRtcp : "") +
				(tpIsUdp ? "" : ", tpClientTcpChannRtp=" + tpClientTcpChannRtp) +
				(tpIsUdp ? "" : ", tpClientTcpChannRtcp=" + tpClientTcpChannRtcp) +
				", tpIsUnicast=" + (tpIsUnicast ? "T" : "F") +
				(tpIsUdp ? "" : ", tpIsInterleaved=" + (tpIsInterleaved ? "T" : "F")) +
				", tpIsEncr=" + (tpIsEncr ? "T" : "F") +
				"]";
	}

}
