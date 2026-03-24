package org.tsitle.rtsp.packets.rtp;

public final class ParamsContainerBase {

	/** RTSP Synchronization Source Identifier */
	public int rtspSsrcId;
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
		reset();
	}

	/**
	 * Constructor.
	 * @param rtspSsrcId RTSP Synchronization Source Identifier
	 * @param sequenceNumber Sequence number of the packet (16 bits unsigned)
	 * @param doSetMarker Set marker flag?
	 * @param rtpTimestamp RTP timestamp of the packet
	 */
	public ParamsContainerBase(
				int rtspSsrcId,
				short sequenceNumber,
				boolean doSetMarker,
				int rtpTimestamp
			) {
		this.rtspSsrcId = rtspSsrcId;
		this.sequenceNumber = sequenceNumber;
		this.doSetMarker = doSetMarker;
		this.rtpTimestamp = rtpTimestamp;
	}

	public void reset() {
		rtspSsrcId = 0;
		sequenceNumber = 0;
		doSetMarker = false;
		rtpTimestamp = 0;
	}

}
