package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.buffers.BufferExt;

public interface RtpPacketPayloadInterface {

	/**
	 * Returns the payload type of the RTP packet.
	 * @return Payload type
	 */
	RtpPacketType getPayloadType();

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns the total length of the payload-specific header and the payload of the RTP packet.
	 * @return Size of the header and payload data
	 */
	int getRawInnerHeaderAndPayloadSize();

	/**
	 * Copies the payload-specific header and payload of the RTP packet into the given buffer.
	 * @param rawData Buffer to copy the raw data into
	 * @param dstOffset Offset within the rawData buffer at which to start inserting the data
	 */
	void copyRawInnerHeaderAndPayloadDataInto(BufferExt rawData, int dstOffset);

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the payload-specific header of the RTP packet into the given buffer.
	 * @param rawData Header data
	 */
	@SuppressWarnings("unused")
	void getRawInnerHeaderData(BufferExt rawData);

	/**
	 * Returns the length of the payload-specific header of the RTP packet.
	 * @return Size of the header data
	 */
	@SuppressWarnings("unused")
	int getRawInnerHeaderSize();

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the inner payload of the RTP packet into the given buffer.
	 * @param rawData Payload data
	 */
	@SuppressWarnings("unused")
	void getRawInnerPayloadData(BufferExt rawData);

	/**
	 * Returns the length of the inner payload of the RTP packet.
	 * @return Size of the payload data
	 */
	@SuppressWarnings("unused")
	int getRawInnerPayloadSize();

}
