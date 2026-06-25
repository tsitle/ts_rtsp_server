package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidTpSettingsException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.net.DatagramSocket;

/**
 * Container for SETUP information for a Sub-Stream.
 */
public final class RtspProtoSetupInfoForSubStream implements Cloneable {

	private boolean isWriteProtected = false;

	/** Resource URL of the Sub-Stream */
	private @NonNull RtspProtoRscUrl rscUrlSubStream = new RtspProtoRscUrl();

	/** Have we received a SETUP for this Sub-Stream? */
	private boolean haveSetup = false;

	/** RTSP Synchronization Source Identifier for inbound packets (random number. one per session/client and per stream) */
	private @NonNull RtspProtoIdXsrc ssrcInbound;
	/** RTSP Synchronization Source Identifier for outbound packets (random number. one per session/client and per stream) */
	private @NonNull RtspProtoIdXsrc ssrcOutbound;
	/** Initial RTP Sequence Number within the session (random number, 16 bits unsigned) */
	public @NonNull RtspProtoRtpSeqNr rtpSeqNrT0;
	/** Initial RTP Timestamp within the session (random number) */
	private @NonNull RtspProtoRtpTimestamp rtpTimestampT0;
	/** System.nanoTime when the RTP TS T0 was generated (in nanoseconds) */
	private @NonNull TimestampEpochNs rtpGenTsT0EpochNs;

	/** Transport settings */
	private @NonNull RtspProtoDataCntSubStreamTp subStreamTp = new RtspProtoDataCntSubStreamTp();

	/** Server's UDP socket for outbound RTP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtp = null;
	/** Server's UDP socket for inbound/outbound RTCP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtcp = null;

	/** Client's UDP socket for inbound RTP packets */
	private @Nullable DatagramSocket tpClientUdpSocketRtp = null;
	/** Client's UDP socket for inbound/outbound RTCP packets */
	private @Nullable DatagramSocket tpClientUdpSocketRtcp = null;

	/** Inbound Key Management Data */
	private @NonNull RtspProtoKmdForSubStream kmdInboundCur = new RtspProtoKmdForSubStream();
	/** Next Inbound Key Management Data (obtained from re-keying request) */
	private @NonNull RtspProtoKmdForSubStream kmdInboundNext = new RtspProtoKmdForSubStream();
	/** Outbound Key Management Data */
	private @NonNull RtspProtoKmdForSubStream kmdOutbound = new RtspProtoKmdForSubStream();

	public RtspProtoSetupInfoForSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				@Nullable RtspProtoIdXsrc ssrcInbound,
				@NonNull RtspProtoIdXsrc ssrcOutbound,
				@NonNull RtspProtoRtpSeqNr rtpSeqNrT0,
				@NonNull RtspProtoRtpTimestamp rtpTimestampT0,
				@NonNull TimestampEpochNs rtpGenTsT0EpochNs
			) {
		this.rscUrlSubStream.copyFrom(rscUrlSubStream);
		this.rscUrlSubStream.writeProtect();

		if (ssrcInbound != null) {
			this.ssrcInbound = ssrcInbound.clone();
		} else {
			this.ssrcInbound = RtspProtoIdXsrc.ofEmpty();
		}
		this.ssrcInbound.writeProtect();

		this.ssrcOutbound = ssrcOutbound.clone();
		this.ssrcOutbound.writeProtect();

		this.rtpSeqNrT0 = rtpSeqNrT0.clone();
		this.rtpSeqNrT0.writeProtect();
		this.rtpTimestampT0 = rtpTimestampT0.clone();
		this.rtpTimestampT0.writeProtect();
		this.rtpGenTsT0EpochNs = rtpGenTsT0EpochNs.clone();
		this.rtpGenTsT0EpochNs.writeProtect();
	}

	public RtspProtoSetupInfoForSubStream(
				@NonNull RtspProtoSetupInfoForSubStream other,
				@NonNull RtspProtoIdXsrc ssrcInbound
			) {
		this.rscUrlSubStream.copyFrom(other.rscUrlSubStream);
		this.rscUrlSubStream.writeProtect();

		this.haveSetup = other.haveSetup;

		this.ssrcInbound = ssrcInbound.clone();
		this.ssrcInbound.writeProtect();

		this.ssrcOutbound = other.ssrcOutbound.clone();

		this.rtpSeqNrT0 = other.rtpSeqNrT0.clone();
		this.rtpSeqNrT0.writeProtect();
		this.rtpTimestampT0 = other.rtpTimestampT0.clone();
		this.rtpTimestampT0.writeProtect();
		this.rtpGenTsT0EpochNs = other.rtpGenTsT0EpochNs.clone();
		this.rtpGenTsT0EpochNs.writeProtect();

		this.subStreamTp.copyFrom(other.subStreamTp);

		this.tpServerUdpSocketRtp = other.tpServerUdpSocketRtp;
		this.tpServerUdpSocketRtcp = other.tpServerUdpSocketRtcp;

		this.tpClientUdpSocketRtp = other.tpClientUdpSocketRtp;
		this.tpClientUdpSocketRtcp = other.tpClientUdpSocketRtcp;

		this.kmdInboundCur.copyFrom(other.kmdInboundCur);
		this.kmdInboundNext.copyFrom(other.kmdInboundNext);
		this.kmdOutbound.copyFrom(other.kmdOutbound);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoRscUrl getRscUrlSubStreamPtr() {
		return rscUrlSubStream;
	}

	public boolean getHaveSetup() {
		return haveSetup;
	}
	public void setHaveSetup(boolean haveSetup) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.haveSetup = haveSetup;
	}

	public @NonNull RtspProtoIdXsrc getSsrcInboundPtr() {
		return ssrcInbound;
	}
	public @NonNull RtspProtoIdXsrc getSsrcOutboundPtr() {
		return ssrcOutbound;
	}

	public @NonNull RtspProtoRtpSeqNr getRtpSeqNrT0Ptr() {
		return rtpSeqNrT0;
	}

	public @NonNull RtspProtoRtpTimestamp getRtpTimestampT0Ptr() {
		return rtpTimestampT0;
	}

	public @NonNull TimestampEpochNs getRtpGenTsT0EpochNsPtr() {
		return rtpGenTsT0EpochNs;
	}

	public @NonNull RtspProtoDataCntSubStreamTp getSubStreamTpPtr() {
		return subStreamTp;
	}

	public @Nullable DatagramSocket getServerUdpSocketRtpPtr() {
		return tpServerUdpSocketRtp;
	}
	public @Nullable DatagramSocket getServerUdpSocketRtcpPtr() {
		return tpServerUdpSocketRtcp;
	}
	public void setServerUdpSocketRtpPtr(@NonNull DatagramSocket value) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpServerUdpSocketRtp = value;
	}
	public void setServerUdpSocketRtcpPtr(@NonNull DatagramSocket value) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpServerUdpSocketRtcp = value;
	}

	public @Nullable DatagramSocket getClientUdpSocketRtpPtr() {
		return tpClientUdpSocketRtp;
	}
	public @Nullable DatagramSocket getClientUdpSocketRtcpPtr() {
		return tpClientUdpSocketRtcp;
	}
	public void setClientUdpSocketRtpPtr(@NonNull DatagramSocket value) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpClientUdpSocketRtp = value;
	}
	public void setClientUdpSocketRtcpPtr(@NonNull DatagramSocket value) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpClientUdpSocketRtcp = value;
	}

	public @NonNull RtspProtoKmdForSubStream getKmdInboundCurPtr() {
		return kmdInboundCur;
	}
	public @NonNull RtspProtoKmdForSubStream getKmdInboundNextPtr() {
		return kmdInboundNext;
	}
	public @NonNull RtspProtoKmdForSubStream getKmdOutboundPtr() {
		return kmdOutbound;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void isTransportValid(
				boolean needsEncryption,
				boolean forceEncryption,
				boolean isRtspsConnection,
				boolean isTransportUdpDisabled
			) throws RtspProtoInvalidTpSettingsException {
		subStreamTp.isTransportValid(needsEncryption, forceEncryption, isRtspsConnection, isTransportUdpDisabled);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		isWriteProtected = true;

		rscUrlSubStream.writeProtect();
		ssrcInbound.writeProtect();
		ssrcOutbound.writeProtect();
		rtpSeqNrT0.writeProtect();
		rtpTimestampT0.writeProtect();
		rtpGenTsT0EpochNs.writeProtect();

		subStreamTp.writeProtect();
		kmdInboundCur.writeProtect();
		kmdInboundNext.writeProtect();
		kmdOutbound.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoSetupInfoForSubStream clone() {
		try {
			RtspProtoSetupInfoForSubStream cloned = (RtspProtoSetupInfoForSubStream)super.clone();

			cloned.rscUrlSubStream = rscUrlSubStream.clone();
			cloned.ssrcInbound = ssrcInbound.clone();
			cloned.ssrcOutbound = ssrcOutbound.clone();
			cloned.rtpSeqNrT0 = rtpSeqNrT0.clone();
			cloned.rtpTimestampT0 = rtpTimestampT0.clone();
			cloned.rtpGenTsT0EpochNs = rtpGenTsT0EpochNs.clone();

			cloned.subStreamTp = subStreamTp.clone();

			// we don't clone the UDP sockets and keep them as pointers instead

			cloned.kmdInboundCur = kmdInboundCur.clone();
			cloned.kmdInboundNext = kmdInboundNext.clone();
			cloned.kmdOutbound = kmdOutbound.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
