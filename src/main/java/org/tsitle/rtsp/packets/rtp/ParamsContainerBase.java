package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

public final class ParamsContainerBase {

	/** RTSP Synchronization Source Identifier */
	public @NonNull RtspProtoIdXsrc ssrcId;
	/** Sequence number of the packet (16 bits unsigned) */
	public @NonNull RtspProtoRtpSeqNr sequenceNumber;
	/** Set marker flag? */
	public boolean doSetMarker;
	/** RTP timestamp of the packet */
	public @NonNull RtspProtoRtpTimestamp rtpTimestamp;

	/**
	 * Constructor.
	 */
	public ParamsContainerBase() {
		this.ssrcId = RtspProtoIdXsrc.ofEmpty();
		this.sequenceNumber = RtspProtoRtpSeqNr.ofEmpty();
		this.rtpTimestamp = RtspProtoRtpTimestamp.ofEmpty();
		reset();
	}

	/**
	 * Constructor.
	 * @param ssrcId RTSP Synchronization Source Identifier
	 * @param sequenceNumber Sequence number of the packet (16 bits unsigned)
	 * @param doSetMarker Set marker flag?
	 * @param rtpTimestamp RTP timestamp of the packet
	 */
	public ParamsContainerBase(
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull RtspProtoRtpSeqNr sequenceNumber,
				boolean doSetMarker,
				@NonNull RtspProtoRtpTimestamp rtpTimestamp
			) {
		this.ssrcId = ssrcId.clone();
		this.sequenceNumber = sequenceNumber.clone();
		this.doSetMarker = doSetMarker;
		this.rtpTimestamp = rtpTimestamp.clone();
	}

	public void reset() {
		ssrcId.clear();
		sequenceNumber.clear();
		doSetMarker = false;
		rtpTimestamp.clear();
	}

}
