package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidTpSettingsException;

import java.net.DatagramSocket;

public final class RtspProtoSetupInfoForSubStream implements Cloneable {

	/** Resource URL of the Sub-Stream */
	public final @NonNull RtspProtoRscUrl rscUrlSubStream = new RtspProtoRscUrl();

	/** Have we received a SETUP for this Sub-Stream? */
	public boolean haveSetup = false;

	/** RTSP Synchronization Source Identifier (random number. one per session/client and per stream) */
	public final int rtspSsrcId;
	/** Initial RTP Sequence Number within the session (random number, 16 bits unsigned) */
	public final short rtspRtpSeqNrT0;
	/** Initial RTP Timestamp within the session (random number) */
	public final int rtspRtpTimestampT0;
	/** System.nanoTime when the RTP TS T0 was generated (in nanoseconds) */
	public final long rtspRtpGenTsT0Ns;

	/** Transport settings */
	public final @NonNull RtspProtoDataCntSubStreamTp subStreamTp = new RtspProtoDataCntSubStreamTp();

	/** Server's UDP socket for outbound RTP packets */
	public @Nullable DatagramSocket tpServerUdpSocketRtp = null;
	/** Server's UDP socket for inbound/outbound RTCP packets */
	public @Nullable DatagramSocket tpServerUdpSocketRtcp = null;

	/** Inbound Key Management Data */
	public final @NonNull RtspProtoKmdForSubStream kmdInboundCur = new RtspProtoKmdForSubStream();
	/** Next Inbound Key Management Data (obtained from re-keying request) */
	public final @NonNull RtspProtoKmdForSubStream kmdInboundNext = new RtspProtoKmdForSubStream();
	/** Outbound Key Management Data */
	public final @NonNull RtspProtoKmdForSubStream kmdOutbound = new RtspProtoKmdForSubStream();

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

	public void isTransportValid(
				boolean needsEncryption,
				boolean forceEncryption,
				boolean isRtspsConnection,
				boolean isTransportUdpDisabled
			) throws RtspInvalidTpSettingsException {
		subStreamTp.isTransportValid(needsEncryption, forceEncryption, isRtspsConnection, isTransportUdpDisabled);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoSetupInfoForSubStream clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

}
