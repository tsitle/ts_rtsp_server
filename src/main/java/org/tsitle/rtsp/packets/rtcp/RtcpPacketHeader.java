package org.tsitle.rtsp.packets.rtcp;

import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTCP Packet Header.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.1">RFC-3550 Section 6.1</a>
 */
public class RtcpPacketHeader implements Cloneable {

	/** Size of the RTCP header */
	public static final int HEADER_SIZE = 4;

	/** Version number (2 bits) */
	private final byte hdVersion;
	/** Is the header padded? (1 bit) */
	private final boolean hdPadding;
	/** Item count in the body (for instance, Reception Report Count, 5 bits) */
	private final byte hdItemCount;
	/** Payload type (8 bits) */
	private final RtcpPacketType hdPayloadType;
	/** Payload size */
	private final int hdPayloadSize;

	/** Original Payload type as byte */
	private final byte orgPayloadTypeByte;
	/** Bitstream of header */
	private BufferExt rawHeader = new BufferExt();

	/**
	 * Constructor.
	 * @param type RTCP packet type
	 * @param itemCount Number of items in the body
	 * @param payloadSize Size of the body in bytes
	 */
	public RtcpPacketHeader(RtcpPacketType type, byte itemCount, int payloadSize) {
		if (type == null || type == RtcpPacketType.UNKNOWN) {
			throw new IllegalArgumentException("Invalid RTCP packet type: " + type);
		}
		this.hdVersion = 2;
		this.hdPadding = false;
		this.hdItemCount = itemCount;
		this.hdPayloadType = type;
		this.orgPayloadTypeByte = type.getValue();
		this.hdPayloadSize = payloadSize;

		// Construct the bitstreams
		byte[] tmpBuf = new byte[HEADER_SIZE];

		//noinspection ConstantValue
		byte tmpPadd = (byte)(this.hdPadding ? 1 : 0);
		//noinspection ConstantValue
		tmpBuf[0] = (byte)((((this.hdVersion & 0x03) << 6) & 0xFF) | ((tmpPadd << 5) & 0xFF) | (this.hdItemCount & 0x1F));
		tmpBuf[1] = (byte)(this.hdPayloadType.getValue() & 0xFF);
		int tmpPktLength32bitWordsMinusOne = (((HEADER_SIZE + this.hdPayloadSize) / 4) - 1);
		tmpBuf[2] = (byte)((tmpPktLength32bitWordsMinusOne & 0xFF00) >> 8);
		tmpBuf[3] = (byte)(tmpPktLength32bitWordsMinusOne & 0xFF);
		this.rawHeader.copyOf(tmpBuf);
	}

	/**
	 * Constructor.
	 * @param rawPacketHeader Raw packet header bitstream
	 */
	public RtcpPacketHeader(BufferExt rawPacketHeader) {
		if (rawPacketHeader.getUsed() < HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTCP header size");
		}
		this.rawHeader.copyOf(rawPacketHeader, 0, HEADER_SIZE);

		// Parse header fields
		this.hdVersion = (byte)(((this.rawHeader.get(0) & 0xC0) >> 6) & 0x03);
		this.hdPadding = ((byte)(((this.rawHeader.get(0) & 0x20) >> 5) & 0x01) == 1);
		this.hdItemCount = (byte)(this.rawHeader.get(0) & 0x1F);
		this.orgPayloadTypeByte = (byte)(this.rawHeader.get(1) & 0xFF);
		this.hdPayloadType = RtcpPacketType.of(this.orgPayloadTypeByte);
		int tmpPktLength32bitWordsMinusOne = (((this.rawHeader.get(2) & 0xFF) << 8) | (this.rawHeader.get(3) & 0xFF));
		this.hdPayloadSize = ((tmpPktLength32bitWordsMinusOne + 1) * 4) - HEADER_SIZE;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the header of the raw RTCP packet header into the given buffer.
	 * @param rawPacketHeader Buffer to copy the raw packet header data into
	 */
	public void copyRawPacketHeaderDataInto(BufferExt rawPacketHeader) {
		rawPacketHeader.copyOf(rawHeader, 0, HEADER_SIZE);
	}

	/**
	 * Get the total size of the raw RTCP packet, including the header and payload.
	 * @return Size of the raw packet
	 */
	public int getPacketSize() {
		return (HEADER_SIZE + hdPayloadSize);
	}

	/**
	 * Get the payload type of the RTCP packet.
	 * @return Payload type
	 */
	public RtcpPacketType getPayloadType() {
		return hdPayloadType;
	}

	/**
	 * Get the original payload type of the RTCP packet.
	 * @return Original payload type as an unsigned integer
	 */
	public int getOrgPayloadType() {
		return Byte.toUnsignedInt(orgPayloadTypeByte);
	}

	/**
	 * Get the number of items in the body of the RTCP packet.
	 * @return Item count
	 */
	public int getItemsCount() {
		return hdItemCount;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"Version: " + hdVersion +
				", Padding: " + hdPadding +
				", Type: " + hdPayloadType + " (o=" + getOrgPayloadType() + ")" +
				", PayloadSize: " + Integer.toUnsignedString(hdPayloadSize) +
				"]";
	}

	@Override
	public RtcpPacketHeader clone() {
		try {
			RtcpPacketHeader clone = (RtcpPacketHeader)super.clone();
			clone.rawHeader = new BufferExt();
			clone.rawHeader.copyOf(this.rawHeader);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
