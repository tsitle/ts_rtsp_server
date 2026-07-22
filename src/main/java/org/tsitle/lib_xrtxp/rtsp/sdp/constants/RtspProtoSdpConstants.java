package org.tsitle.lib_xrtxp.rtsp.sdp.constants;

/**
 * Constants for the Session Description Protocol (SDP) messages Producer and Consumer.
 */
public final class RtspProtoSdpConstants {

	private RtspProtoSdpConstants() { }

	/**
	 * AAC: AU-header field AU-Size length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int AAC_HEADER_FLD_SIZE_LENGTH_BITS = 13;
	/**
	 * AAC: AU-header field AU-Index length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int AAC_HEADER_FLD_INDEX_LENGTH_BITS = 3;
	/**
	 * AAC: AU-header field AU-IndexDelta length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int AAC_HEADER_FLD_INDEXDELTA_LENGTH_BITS = 0;

}
