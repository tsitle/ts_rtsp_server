package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

public final class ParamsContainerBase {

	/** RTSP Synchronization Source Identifier */
	public @NonNull RtspProtoIdXsrc ssrcId;
	/** Sequence number of the packet (16 bits unsigned) */
	public short sequenceNumber;
	/** Set marker flag? */
	public boolean doSetMarker;
	/** RTP timestamp of the packet */
	public int rtpTimestamp;

	/**
	 * Constructor.
	 */
	public ParamsContainerBase() {
		this.ssrcId = new RtspProtoIdXsrc();
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
				short sequenceNumber,
				boolean doSetMarker,
				int rtpTimestamp
			) {
		this.ssrcId = ssrcId.clone();
		this.sequenceNumber = sequenceNumber;
		this.doSetMarker = doSetMarker;
		this.rtpTimestamp = rtpTimestamp;
	}

	public void reset() {
		ssrcId.clear();
		sequenceNumber = 0;
		doSetMarker = false;
		rtpTimestamp = 0;
	}

}
