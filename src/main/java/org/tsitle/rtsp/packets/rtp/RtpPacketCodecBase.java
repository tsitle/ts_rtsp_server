package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP codec-specific Packet base class
 */
public abstract class RtpPacketCodecBase extends RtpPacketContainerBase {

	/** Additional size of the RTP payload-specific header */
	protected int payloadSpecHeaderSize;

	/**
	 * Constructor.
	 * @param payloadType RTP payload type
	 * @param paramsBase Base Container parameters
	 */
	protected RtpPacketCodecBase(
				@NonNull RtpPacketType payloadType,
				@NonNull ParamsContainerBase paramsBase
			) {
		super(payloadType, paramsBase);
	}

	/**
	 * Constructor.
	 * @param expectedPayloadType Expected RTP payload type
	 * @param packetData RTP packet bitstream including header and payload
	 */
	protected RtpPacketCodecBase(
				@NonNull RtpPacketType expectedPayloadType,
				@NonNull BufferExt packetData
			) {
		super(expectedPayloadType, packetData);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the payload-specific header of the RTP packet into the given buffer.
	 * @param rawData Header data
	 */
	@SuppressWarnings("unused")
	public void getRawInnerHeaderData(@NonNull BufferExt rawData) {
		rawData.copyOf(
				packetBuf,
				RTP_CONT_HEADER_SIZE,
				getRawInnerHeaderSize()
			);
	}

	/**
	 * Returns the length of the payload-specific header of the RTP packet.
	 * @return Size of the header data
	 */
	public int getRawInnerHeaderSize() {
		return payloadSpecHeaderSize;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the inner payload of the RTP packet into the given buffer.
	 * @param rawData Payload data
	 */
	@SuppressWarnings("unused")
	public void getRawInnerPayloadData(@NonNull BufferExt rawData) {
		rawData.copyOf(
				packetBuf,
				RTP_CONT_HEADER_SIZE + getRawInnerHeaderSize(),
				getRawInnerPayloadSize()
			);
	}

	/**
	 * Returns the length of the inner payload of the RTP packet.
	 * @return Size of the payload data
	 */
	public int getRawInnerPayloadSize() {
		return packetBuf.getUsed() - RTP_CONT_HEADER_SIZE - getRawInnerHeaderSize();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return toString(false);
	}

	public @NonNull String toString(boolean skipClassName) {
		return (skipClassName ? "" : getClass().getSimpleName() + " [") +
				super.toString(skipClassName) +
				", innerHeaderSz: " + Integer.toUnsignedString(getRawInnerHeaderSize()) +
				", innerPayloadSz: " + Integer.toUnsignedString(getRawInnerPayloadSize()) +
				(skipClassName ? "" : "]");
	}

}
