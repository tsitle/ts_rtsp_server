package org.tsitle.rtsp.packets.rtp;

public final class ParamsContainerBase {

	/** RTSP Synchronization Source Identifier */
	public int rtspSsrcId;
	/** Sequence number of the packet (16 bits unsigned) */
	public short sequenceNumber;
	/** Set marker flag? */
	public boolean doSetMarker;
	/** RTP timestamp of the frame (and the packet) */
	public int rtpTimestamp;

	public ParamsContainerBase() {
		reset();
	}

	public void reset() {
		rtspSsrcId = 0;
		sequenceNumber = 0;
		doSetMarker = false;
		rtpTimestamp = 0;
	}

}
