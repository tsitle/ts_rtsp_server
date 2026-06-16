package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoInvalidTpSettingsException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

public final class RtspProtoDataCntSubStreamTp implements Cloneable {

	private boolean isWriteProtected = false;

	/** Client's UDP port for inbound RTP packets */
	private @NonNull RtspProtoSocketPortNr tpClientUdpPortRtp = RtspProtoSocketPortNr.ofEmpty();
	/** Client's UDP port for inbound/outbound RTCP packets */
	private @NonNull RtspProtoSocketPortNr tpClientUdpPortRtcp = RtspProtoSocketPortNr.ofEmpty();
	/** Server's UDP port for outbound RTP packets */
	private @NonNull RtspProtoSocketPortNr tpServerUdpPortRtp = RtspProtoSocketPortNr.ofEmpty();
	/** Server's UDP port for inbound/outbound RTCP packets */
	private @NonNull RtspProtoSocketPortNr tpServerUdpPortRtcp = RtspProtoSocketPortNr.ofEmpty();
	/** Client's TCP channel for inbound RTP packets */
	private @NonNull RtspProtoTcpChannelNr tpClientTcpChannRtp = RtspProtoTcpChannelNr.ofEmpty();
	/** Client's TCP channel for inbound/outbound RTCP packets */
	private @NonNull RtspProtoTcpChannelNr tpClientTcpChannRtcp = RtspProtoTcpChannelNr.ofEmpty();
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


	public @NonNull RtspProtoSocketPortNr getClientUdpPortRtpPtr() {
		return tpClientUdpPortRtp;
	}
	public @NonNull RtspProtoSocketPortNr getClientUdpPortRtcpPtr() {
		return tpClientUdpPortRtcp;
	}

	public @NonNull RtspProtoSocketPortNr getServerUdpPortRtpPtr() {
		return tpServerUdpPortRtp;
	}
	public @NonNull RtspProtoSocketPortNr getServerUdpPortRtcpPtr() {
		return tpServerUdpPortRtcp;
	}

	public @NonNull RtspProtoTcpChannelNr getClientTcpChannRtpPtr() {
		return tpClientTcpChannRtp;
	}
	public @NonNull RtspProtoTcpChannelNr getClientTcpChannRtcpPtr() {
		return tpClientTcpChannRtcp;
	}

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
			) throws RtspProtoInvalidTpSettingsException {
		if (! tpIsEncr && ((needsEncryption && ! isRtspsConnection) || forceEncryption)) {
			throw new RtspProtoInvalidTpSettingsException("Client requested unencrypted transport, but encryption is required");
		}
		if (tpIsEncr && ! (needsEncryption || forceEncryption)) {
			throw new RtspProtoInvalidTpSettingsException("Client requested encrypted transport, but encryption is disabled");
		}
		if (! tpIsUnicast) {
			throw new RtspProtoInvalidTpSettingsException("Multicast is not supported");
		}
		if (tpIsUdp) {
			if (tpIsInterleaved) {
				throw new RtspProtoInvalidTpSettingsException("Interleaved mode is not supported for UDP");
			}
			if (tpClientUdpPortRtp.isEmpty() || tpClientUdpPortRtcp.isEmpty()) {
				throw new RtspProtoInvalidTpSettingsException("Client UDP ports not set");
			}
			if (tpClientUdpPortRtp.equals(tpClientUdpPortRtcp)) {
				throw new RtspProtoInvalidTpSettingsException("Client UDP ports for RTP and RTCP cannot be the same");
			}
			if (isRtspsConnection && ! tpIsEncr) {
				throw new RtspProtoInvalidTpSettingsException("UDP cannot be used with RTSPS w/o SRTP");
			}
			if (isTransportUdpDisabled) {
				throw new RtspProtoInvalidTpSettingsException("UDP is disabled");
			}
			return;
		}
		if (! tpIsInterleaved) {
			throw new RtspProtoInvalidTpSettingsException("Interleaved mode must be used for TCP");
		}
		if (tpClientTcpChannRtp.isEmpty() || tpClientTcpChannRtcp.isEmpty()) {
			throw new RtspProtoInvalidTpSettingsException("Client TCP channel IDs not set");
		}
		if (tpClientTcpChannRtp.equals(tpClientTcpChannRtcp)) {
			throw new RtspProtoInvalidTpSettingsException("Client TCP channel IDs for RTP and RTCP cannot be the same");
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

		tpClientUdpPortRtp.writeProtect();
		tpClientUdpPortRtcp.writeProtect();
		tpServerUdpPortRtp.writeProtect();
		tpServerUdpPortRtcp.writeProtect();
		tpClientTcpChannRtp.writeProtect();
		tpClientTcpChannRtcp.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

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

	@Override
	public RtspProtoDataCntSubStreamTp clone() {
		try {
			RtspProtoDataCntSubStreamTp cloned = (RtspProtoDataCntSubStreamTp)super.clone();
			cloned.tpClientUdpPortRtp = tpClientUdpPortRtp.clone();
			cloned.tpClientUdpPortRtcp = tpClientUdpPortRtcp.clone();
			cloned.tpServerUdpPortRtp = tpServerUdpPortRtp.clone();
			cloned.tpServerUdpPortRtcp = tpServerUdpPortRtcp.clone();
			cloned.tpClientTcpChannRtp = tpClientTcpChannRtp.clone();
			cloned.tpClientTcpChannRtcp = tpClientTcpChannRtcp.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
