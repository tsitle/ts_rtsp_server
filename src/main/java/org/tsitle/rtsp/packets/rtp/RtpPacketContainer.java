package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Container.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-5">RFC-3550 Section 5</a>
 */
public class RtpPacketContainer {

	/** Size of the RTP header */
	public static final int HEADER_SIZE = 12;

	/** Version number (2 bits) */
	private final byte hdVersion;
	/** Is the header padded? (1 bit) */
	private final boolean hdPadding;
	/** Does the header include extension fields? (1 bit) */
	private final boolean hdExtension;
	/** CSRC Count (4 bits) */
	private final byte hdCsrcCount;
	/** Is the marker bit set? (1 bit) */
	private final boolean hdMarker;
	/** Payload type (7 bits) */
	private final RtpPacketType hdPayloadType;
	/** Sequence number (16 bits unsigned) */
	private final short hdSequenceNumber;
	/** Timestamp (32 bits) */
	private final int hdTimestamp;
	/** Synchronization Source Identifier (identifies the server) (32 bits) */
	private final int hdSsrc;

	/** Original Payload type as byte */
	private final byte orgPayloadTypeByte;
	/** Bitstream of the RTP header */
	private final BufferExt rawHeader = new BufferExt();

	/** RTP payload */
	private final RtpPacketPayloadInterface payload;

	/**
	 * Constructor.
	 * @param rtspSsrcId RTSP Synchronization Source Identifier
	 * @param sequenceNumber Sequence number of the packet (16 bits unsigned)
	 * @param doSetMarker Set marker flag?
	 * @param timestamp RTP timestamp of the frame (and the packet)
	 * @param payload Payload
	 */
	public RtpPacketContainer(
				int rtspSsrcId,
				short sequenceNumber,
				boolean doSetMarker,
				int timestamp,
				RtpPacketPayloadInterface payload
			) {
		// fill static header fields
		this.hdVersion = 2;
		this.hdPadding = false;
		this.hdExtension = false;
		this.hdCsrcCount = 0;
		this.hdSsrc = rtspSsrcId;

		// fill dynamic header fields
		this.hdMarker = doSetMarker;
		this.hdSequenceNumber = sequenceNumber;
		this.hdTimestamp = timestamp;
		this.hdPayloadType = payload.getPayloadType();
		this.orgPayloadTypeByte = this.hdPayloadType.getValue();

		// build the header bistream
		buildRawHeaderFromFields();

		//
		this.payload = payload;
	}

	/**
	 * Constructor.
	 * @param rawPacketData Raw packet bitstream
	 */
	public RtpPacketContainer(BufferExt rawPacketData) {
		if (rawPacketData.getUsed() < HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}
		// get the header bitsream
		this.rawHeader.copyOf(
				rawPacketData,
				0,
				HEADER_SIZE
			);

		// parse header fields
		this.hdVersion = (byte)(((this.rawHeader.get(0) & 0xC0) >>> 6) & 0x03);
		this.hdPadding = ((byte)(((this.rawHeader.get(0) & 0x20) >>> 5) & 0x01) == 1);
		this.hdExtension = ((byte)(((this.rawHeader.get(0) & 0x10) >>> 4) & 0x01) == 1);
		this.hdCsrcCount = (byte)(this.rawHeader.get(0) & 0x0F);
		this.hdMarker = ((byte)(((this.rawHeader.get(1) & 0x80) >>> 7) & 0x01) == 1);
		this.orgPayloadTypeByte = (byte)(this.rawHeader.get(1) & 0x7F);
		this.hdPayloadType = RtpPacketType.of(this.orgPayloadTypeByte);
		this.hdSequenceNumber = (short)(((this.rawHeader.get(3) & 0xFF) | ((this.rawHeader.get(2) & 0xFF) << 8)) & 0xFFFF);
		this.hdTimestamp = (this.rawHeader.get(7) & 0xFF) | ((this.rawHeader.get(6) & 0xFF) << 8) |
				((this.rawHeader.get(5) & 0xFF) << 16) | ((this.rawHeader.get(4) & 0xFF) << 24);
		this.hdSsrc = (this.rawHeader.get(11) & 0xFF) | ((this.rawHeader.get(10) & 0xFF) << 8) |
				((this.rawHeader.get(9) & 0xFF) << 16) | ((this.rawHeader.get(8) & 0xFF) << 24);

		// parse the payload into an object
		BufferExt tmpPayloadData = new BufferExt();
		tmpPayloadData.copyOf(
				rawPacketData,
				HEADER_SIZE,
				rawPacketData.getUsed() - HEADER_SIZE
			);
		switch (this.hdPayloadType) {
			case V_JPEG ->
					this.payload = new RtpPacketPayloadMjpeg(tmpPayloadData);
			case V_H265 ->
					this.payload = new RtpPacketPayloadH265(tmpPayloadData);
			default -> {
				if (! this.hdPayloadType.isPcmAudio()) {
					throw new IllegalArgumentException("Unsupported payload type: " + this.hdPayloadType);
				}
				this.payload = new RtpPacketPayloadPcm(this.hdPayloadType, tmpPayloadData);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns the payload object of the RTP packet.
	 * @return Payload object
	 */
	public RtpPacketPayloadInterface getPayload() {
		return payload;
	}

	/**
	 * Returns the total length of the raw RTP packet, including the header and payload.
	 * @return Size of the raw packet
	 */
	@SuppressWarnings("unused")
	public int getRawPacketSize() {
		return (HEADER_SIZE + (payload == null ? 0 : payload.getRawInnerHeaderAndPayloadSize()));
	}

	/**
	 * Copies the header and payload of the RTP rawPacketData into the given array.
	 * @param rawPacketData Array to copy the raw data into
	 */
	public void copyRawPacketDataInto(BufferExt rawPacketData) {
		// construct the rawPacketData = header + payload
		final int rawPayloadSize = (payload == null ? 0 : payload.getRawInnerHeaderAndPayloadSize());
		rawPacketData.copyOf(rawHeader, 0, HEADER_SIZE);
		if (rawPayloadSize > 0) {
			payload.copyRawInnerHeaderAndPayloadDataInto(rawPacketData, HEADER_SIZE);
		}
	}

	/**
	 * Returns the timestamp of the RTP packet.
	 * @return Timestamp
	 */
	@SuppressWarnings("unused")
	public int getTimestamp() {
		return hdTimestamp;
	}

	/**
	 * Returns the sequence number of the RTP packet.
	 * @return Sequence number (16 bits unsigned)
	 */
	@SuppressWarnings("unused")
	public short getSequenceNumber() {
		return hdSequenceNumber;
	}

	/**
	 * Returns the payload type of the RTP packet.
	 * @return Payload type
	 */
	@SuppressWarnings("unused")
	public RtpPacketType getPayloadType() {
		return hdPayloadType;
	}

	public int getOrgPayloadType() {
		return Byte.toUnsignedInt(orgPayloadTypeByte);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"Version: " + hdVersion +
				", Padding: " + hdPadding +
				", Extension: " + hdExtension +
				", CC: " + hdCsrcCount +
				", Marker: " + hdMarker +
				", PayloadType: " + hdPayloadType + " (o=" + getOrgPayloadType() + ")" +
				", SequenceNumber: " + hdSequenceNumber +
				", TimeStamp: " + Integer.toUnsignedString(hdTimestamp) +
				", SSRC: " + Integer.toUnsignedString(hdSsrc) +
				", Payload: " + payload +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRawHeaderFromFields() {
		byte[] tmpHeader = new byte[HEADER_SIZE];

		byte tmpPadd = (byte)(hdPadding ? 1 : 0);
		byte tmpExt = (byte)(hdExtension ? 1 : 0);
		tmpHeader[0] = (byte)((hdVersion & 0x03) << 6 | tmpPadd << 5 | tmpExt << 4 | (hdCsrcCount & 0x0F));
		byte tmpMarker = (byte)(hdMarker ? 1 : 0);
		tmpHeader[1] = (byte)(tmpMarker << 7 | (hdPayloadType.getValue() & 0x7F));
		tmpHeader[2] = (byte)((hdSequenceNumber & 0xFF00) >> 8);
		tmpHeader[3] = (byte)(hdSequenceNumber & 0xFF);
		tmpHeader[4] = (byte)(hdTimestamp >> 24);
		tmpHeader[5] = (byte)(hdTimestamp >> 16);
		tmpHeader[6] = (byte)(hdTimestamp >> 8);
		tmpHeader[7] = (byte)(hdTimestamp & 0xFF);
		tmpHeader[8] = (byte)(hdSsrc >> 24);
		tmpHeader[9] = (byte)(hdSsrc >> 16);
		tmpHeader[10] = (byte)(hdSsrc >> 8);
		tmpHeader[11] = (byte)(hdSsrc & 0xFF);

		rawHeader.copyOf(tmpHeader);
	}

}
