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
public final class RtspProtoSetupInfoForSubStream {

	private boolean isWriteProtected = false;

	/** Resource URL of the Sub-Stream */
	private final @NonNull RtspProtoRscUrl rscUrlSubStream = RtspProtoRscUrl.ofEmpty();

	/** Have we received a SETUP for this Sub-Stream? */
	private boolean haveSetup = false;

	/** RTSP Synchronization Source Identifier for inbound packets (random number. one per session/client and per stream) */
	private final @NonNull RtspProtoIdXsrc ssrcInbound = RtspProtoIdXsrc.ofEmpty();
	/** RTSP Synchronization Source Identifier for outbound packets (random number. one per session/client and per stream) */
	private final @NonNull RtspProtoIdXsrc ssrcOutbound = RtspProtoIdXsrc.ofEmpty();
	/** Initial RTP Sequence Number within the session (random number, 16 bits unsigned) */
	public final @NonNull RtspProtoRtpSeqNr rtpSeqNrT0 = RtspProtoRtpSeqNr.ofEmpty();
	/** Initial RTP Timestamp within the session (random number) */
	private final @NonNull RtspProtoRtpTimestamp rtpTimestampT0 = RtspProtoRtpTimestamp.ofEmpty();
	/** System.nanoTime when the RTP TS T0 was generated (in nanoseconds) */
	private final @NonNull TimestampEpochNs rtpGenTsT0EpochNs = TimestampEpochNs.ofEmpty();

	/** Transport settings */
	private final @NonNull RtspProtoDataCntSubStreamTp subStreamTp = new RtspProtoDataCntSubStreamTp();

	/** Server's UDP socket for outbound RTP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtp = null;
	/** Server's UDP socket for inbound/outbound RTCP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtcp = null;

	/** Client's UDP socket for inbound RTP packets */
	private @Nullable DatagramSocket tpClientUdpSocketRtp = null;
	/** Client's UDP socket for inbound/outbound RTCP packets */
	private @Nullable DatagramSocket tpClientUdpSocketRtcp = null;

	/** Inbound Key Management Data */
	private final @NonNull RtspProtoKmdForSubStream kmdInboundCur = new RtspProtoKmdForSubStream();
	/** Next Inbound Key Management Data (obtained from re-keying request) */
	private final @NonNull RtspProtoKmdForSubStream kmdInboundNext = new RtspProtoKmdForSubStream();
	/** Outbound Key Management Data */
	private final @NonNull RtspProtoKmdForSubStream kmdOutbound = new RtspProtoKmdForSubStream();

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
			this.ssrcInbound.copyFrom(ssrcInbound.clone());
		}
		this.ssrcInbound.writeProtect();

		this.ssrcOutbound.copyFrom(ssrcOutbound);
		this.ssrcOutbound.writeProtect();

		this.rtpSeqNrT0.copyFrom(rtpSeqNrT0);
		this.rtpSeqNrT0.writeProtect();
		this.rtpTimestampT0.copyFrom(rtpTimestampT0);
		this.rtpTimestampT0.writeProtect();
		this.rtpGenTsT0EpochNs.copyFrom(rtpGenTsT0EpochNs);
		this.rtpGenTsT0EpochNs.writeProtect();
	}

	public RtspProtoSetupInfoForSubStream(@NonNull RtspProtoSetupInfoForSubStream other) {
		this.isWriteProtected = false;

		this.rscUrlSubStream.copyFrom(other.rscUrlSubStream);
		this.rscUrlSubStream.writeProtect();

		this.haveSetup = other.haveSetup;

		this.ssrcInbound.copyFrom(ssrcInbound);

		this.ssrcOutbound.copyFrom(other.ssrcOutbound);

		this.rtpSeqNrT0.copyFrom(other.rtpSeqNrT0);
		this.rtpTimestampT0.copyFrom(other.rtpTimestampT0);
		this.rtpGenTsT0EpochNs.copyFrom(other.rtpGenTsT0EpochNs);
		this.rtpGenTsT0EpochNs.writeProtect();

		this.subStreamTp.copyFrom(other.subStreamTp);

		this.tpServerUdpSocketRtp = other.tpServerUdpSocketRtp;  // copy pointer to socket
		this.tpServerUdpSocketRtcp = other.tpServerUdpSocketRtcp;  // copy pointer to socket

		this.tpClientUdpSocketRtp = other.tpClientUdpSocketRtp;  // copy pointer to socket
		this.tpClientUdpSocketRtcp = other.tpClientUdpSocketRtcp;  // copy pointer to socket

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

}
