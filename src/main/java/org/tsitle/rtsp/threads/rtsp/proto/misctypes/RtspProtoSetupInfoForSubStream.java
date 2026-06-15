package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoInvalidTpSettingsException;

import java.net.DatagramSocket;

public final class RtspProtoSetupInfoForSubStream implements Cloneable {

	private boolean writeProtected = false;

	/** Resource URL of the Sub-Stream */
	private @NonNull RtspProtoRscUrl rscUrlSubStream = new RtspProtoRscUrl();

	/** Have we received a SETUP for this Sub-Stream? */
	private boolean haveSetup = false;

	/** RTSP Synchronization Source Identifier (random number. one per session/client and per stream) */
	public final int rtspSsrcId;
	/** Initial RTP Sequence Number within the session (random number, 16 bits unsigned) */
	public final short rtspRtpSeqNrT0;
	/** Initial RTP Timestamp within the session (random number) */
	public final int rtspRtpTimestampT0;
	/** System.nanoTime when the RTP TS T0 was generated (in nanoseconds) */
	public final long rtspRtpGenTsT0Ns;

	/** Transport settings */
	private @NonNull RtspProtoDataCntSubStreamTp subStreamTp = new RtspProtoDataCntSubStreamTp();

	/** Server's UDP socket for outbound RTP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtp = null;
	/** Server's UDP socket for inbound/outbound RTCP packets */
	private @Nullable DatagramSocket tpServerUdpSocketRtcp = null;

	/** Inbound Key Management Data */
	private @NonNull RtspProtoKmdForSubStream kmdInboundCur = new RtspProtoKmdForSubStream();
	/** Next Inbound Key Management Data (obtained from re-keying request) */
	private @NonNull RtspProtoKmdForSubStream kmdInboundNext = new RtspProtoKmdForSubStream();
	/** Outbound Key Management Data */
	private @NonNull RtspProtoKmdForSubStream kmdOutbound = new RtspProtoKmdForSubStream();

	public RtspProtoSetupInfoForSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				int rtspSsrcId,
				short rtspRtpSeqNrT0,
				int rtspRtpTimestampT0,
				long rtspRtpGenTsT0
			) {
		this.rscUrlSubStream.copyFrom(rscUrlSubStream);
		this.rscUrlSubStream.writeProtect();
		this.rtspSsrcId = rtspSsrcId;
		this.rtspRtpSeqNrT0 = rtspRtpSeqNrT0;
		this.rtspRtpTimestampT0 = rtspRtpTimestampT0;
		this.rtspRtpGenTsT0Ns = rtspRtpGenTsT0;
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
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.haveSetup = haveSetup;
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
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpServerUdpSocketRtp = value;
	}
	public void setServerUdpSocketRtcpPtr(@NonNull DatagramSocket value) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tpServerUdpSocketRtcp = value;
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
		writeProtected = true;

		rscUrlSubStream.writeProtect();
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
