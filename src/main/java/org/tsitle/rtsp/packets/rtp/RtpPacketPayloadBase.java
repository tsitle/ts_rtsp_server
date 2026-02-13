package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload base class
 */
public abstract class RtpPacketPayloadBase implements RtpPacketPayloadInterface {

	/** Bitstream of the RTP payload-specific header */
	protected final BufferExt rawInnerHeaderData = new BufferExt();
	/** Bitstream of the RTP payload */
	protected final BufferExt rawInnerPayloadData = new BufferExt();

	/**
	 * Constructor.
	 */
	protected RtpPacketPayloadBase() {
	}

	/**
	 * Constructor.
	 * @param rawInnerHeaderData Raw payload-specific header bitstream
	 * @param rawInnerPayloadData Raw payload bitstream
	 */
	@SuppressWarnings("unused")
	protected RtpPacketPayloadBase(BufferExt rawInnerHeaderData, BufferExt rawInnerPayloadData) {
		this.rawInnerHeaderData.copyOf(rawInnerHeaderData);
		this.rawInnerPayloadData.copyOf(rawInnerPayloadData);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int getRawInnerHeaderAndPayloadSize() {
		return rawInnerHeaderData.getUsed() + rawInnerPayloadData.getUsed();
	}

	@Override
	public void copyRawInnerHeaderAndPayloadDataInto(BufferExt rawData, int dstOffset) {
		rawData.copyFrom(
				rawInnerHeaderData,
				0,
				dstOffset,
				rawInnerHeaderData.getUsed()
			);
		rawData.copyFrom(
				rawInnerPayloadData,
				0,
				dstOffset + rawInnerHeaderData.getUsed(),
				rawInnerPayloadData.getUsed()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void getRawInnerHeaderData(BufferExt rawData) {
		rawData.copyOf(rawInnerHeaderData);
	}

	@Override
	public int getRawInnerHeaderSize() {
		return rawInnerHeaderData.getUsed();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void getRawInnerPayloadData(BufferExt rawData) {
		rawData.copyOf(rawInnerPayloadData);
	}

	@Override
	public int getRawInnerPayloadSize() {
		return rawInnerPayloadData.getUsed();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"innerHeaderSz: " + Integer.toUnsignedString(rawInnerHeaderData.getUsed()) +
				", innerPayloadSz: " + Integer.toUnsignedString(rawInnerPayloadData.getUsed()) +
				"]";
	}

}
