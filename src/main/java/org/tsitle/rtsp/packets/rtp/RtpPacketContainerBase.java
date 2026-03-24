package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;

/**
 * RTP Packet Container base class.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-5">RFC-3550 Section 5</a>
 */
public class RtpPacketContainerBase {

	/** Size of the RTP header */
	public static final int RTP_CONT_HEADER_SIZE = 12;

	/** Version number (2 bits) */
	private final byte hdBaseVersion;
	/** Is the header padded? (1 bit) */
	private final boolean hdBasePadding;
	/** Does the header include extension fields? (1 bit) */
	private final boolean hdBaseExtension;
	/** CSRC Count (4 bits) */
	private final byte hdBaseCsrcCount;
	/** Is the marker bit set? (1 bit) */
	private final boolean hdBaseMarker;
	/** Payload type (7 bits) */
	private final @NonNull RtpPacketType hdBasePayloadType;
	/** Original Payload type as byte */
	private final byte orgPayloadTypeByte;
	/** Sequence number (16 bits unsigned) */
	private final short hdBaseSequenceNumber;
	/** Timestamp (32 bits) */
	private final int hdBaseTimestamp;
	/** Synchronization Source Identifier (identifies the server) (32 bits) */
	private final int hdBaseSsrc;

	/** RTP header and payload */
	protected final BufferExt packetBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param payloadType RTP payload type
	 * @param paramsBase Base Container parameters
	 */
	protected RtpPacketContainerBase(
				@NonNull RtpPacketType payloadType,
				@NonNull ParamsContainerBase paramsBase
			) {
		// fill static header fields
		this.hdBaseVersion = 2;
		this.hdBasePadding = false;
		this.hdBaseExtension = false;
		this.hdBaseCsrcCount = 0;
		this.hdBaseSsrc = paramsBase.rtspSsrcId;

		// fill dynamic header fields
		this.hdBaseMarker = paramsBase.doSetMarker;
		this.hdBaseSequenceNumber = paramsBase.sequenceNumber;
		this.hdBaseTimestamp = paramsBase.rtpTimestamp;
		this.hdBasePayloadType = payloadType;
		this.orgPayloadTypeByte = payloadType.getValue();

		// build the header bistream
		buildRawHeaderFromFields();
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 * @param onlyCopyHeader If true, copy only the main RTP header into the packet buffer.
	 */
	private RtpPacketContainerBase(@NonNull BufferExt packetData, boolean onlyCopyHeader) {
		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		//
		this.packetBuf.copyOf(packetData, 0, onlyCopyHeader ? RTP_CONT_HEADER_SIZE : packetData.getUsed());

		// parse header fields
		this.hdBaseVersion = (byte)(((this.packetBuf.get(0) & 0xC0) >>> 6) & 0x03);
		this.hdBasePadding = ((byte)(((this.packetBuf.get(0) & 0x20) >>> 5) & 0x01) == 1);
		this.hdBaseExtension = ((byte)(((this.packetBuf.get(0) & 0x10) >>> 4) & 0x01) == 1);
		this.hdBaseCsrcCount = (byte)(this.packetBuf.get(0) & 0x0F);
		this.hdBaseMarker = ((byte)(((this.packetBuf.get(1) & 0x80) >>> 7) & 0x01) == 1);
		this.orgPayloadTypeByte = (byte)(this.packetBuf.get(1) & 0x7F);
		this.hdBasePayloadType = RtpPacketType.of(this.orgPayloadTypeByte);
		this.hdBaseSequenceNumber = (short)(((this.packetBuf.get(3) & 0xFF) | ((this.packetBuf.get(2) & 0xFF) << 8)) & 0xFFFF);
		this.hdBaseTimestamp = (this.packetBuf.get(7) & 0xFF) | ((this.packetBuf.get(6) & 0xFF) << 8) |
				((this.packetBuf.get(5) & 0xFF) << 16) | ((this.packetBuf.get(4) & 0xFF) << 24);
		this.hdBaseSsrc = (this.packetBuf.get(11) & 0xFF) | ((this.packetBuf.get(10) & 0xFF) << 8) |
				((this.packetBuf.get(9) & 0xFF) << 16) | ((this.packetBuf.get(8) & 0xFF) << 24);
	}

	/**
	 * Constructor.
	 * @param expectedPayloadType Expected RTP payload type
	 * @param packetData RTP packet bitstream including header and payload
	 */
	protected RtpPacketContainerBase(
				@NonNull RtpPacketType expectedPayloadType,
				@NonNull BufferExt packetData
			) {
		this(packetData, false);
		//
		if (this.hdBasePayloadType != expectedPayloadType) {
			throw new IllegalArgumentException(
					String.format("Invalid RTP payload type (is=0x%02X, exp=0x%02X)",
							this.hdBasePayloadType.getValue(), expectedPayloadType.getValue())
				);
		}
	}

	/**
	 * Constructor.
	 * @param expectedPayloadType Expected RTP payload type
	 * @param packetData RTP packet bitstream including header and payload
	 * @param onlyCopyHeader If true, copy only the main RTP header into the packet buffer.
	 */
	protected RtpPacketContainerBase(
				@NonNull RtpPacketType expectedPayloadType,
				@NonNull BufferExt packetData,
				boolean onlyCopyHeader
			) {
		this(packetData, onlyCopyHeader);
		//
		if (this.hdBasePayloadType != expectedPayloadType) {
			throw new IllegalArgumentException(
					String.format("Invalid RTP payload type (is=0x%02X, exp=0x%02X)",
							this.hdBasePayloadType.getValue(), expectedPayloadType.getValue())
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns the total length of the RTP packet, including the header and payload.
	 * @return Size of the RTP packet
	 */
	@SuppressWarnings("unused")
	public int getPacketSize() {
		return packetBuf.getUsed();
	}

	/**
	 * Returns a pointer to the raw RTP packet buffer.
	 * @return Pointer to the raw RTP packet buffer
	 */
	public @NonNull BufferExt getPacketBufferPtr() {
		return packetBuf;
	}

	/**
	 * Returns a view of the raw RTP packet buffer.
	 * @return View of the raw RTP packet buffer
	 */
	public @NonNull BufferView getPacketBufferView() {
		return new BufferView(packetBuf);
	}

	/**
	 * Copies the header and payload of the RTP packet into the given buffer.
	 * @param packetData Buffer to copy the raw data into
	 */
	@SuppressWarnings("unused")
	public void copyPacketDataInto(@NonNull BufferExt packetData) {
		packetData.copyOf(packetBuf);
	}

	/**
	 * Returns the payload type of the RTP packet.
	 * @return Payload type
	 */
	@SuppressWarnings("unused")
	public @NonNull RtpPacketType getPayloadType() {
		return hdBasePayloadType;
	}

	/**
	 * Returns the raw value of the payload type of the RTP packet.
	 * @return Raw payload type
	 */
	public byte getOrgPayloadType() {
		return orgPayloadTypeByte;
	}

	/**
	 * Returns the sequence number of the RTP packet.
	 * @return Sequence number (16 bits unsigned)
	 */
	public short getSequenceNumber() {
		return hdBaseSequenceNumber;
	}

	/**
	 * Returns the timestamp of the RTP packet.
	 * @return Timestamp
	 */
	@SuppressWarnings("unused")
	public int getTimestamp() {
		return hdBaseTimestamp;
	}

	/**
	 * Returns the SSRC ID of the RTP packet.
	 * @return SSRC ID
	 */
	public int getSsrcId() {
		return hdBaseSsrc;
	}

	/**
	 * Checks if the RTP packet contains a CSRC list.
	 * @return True if the RTP packet contains a CSRC list
	 */
	public boolean hasCsrcList() {
		return (hdBaseCsrcCount > 0);
	}

	/**
	 * Checks if the RTP packet contains a header extension.
	 * @return True if the RTP packet contains a header extension
	 */
	public boolean hasHeaderExtension() {
		return hdBaseExtension;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return toString(false);
	}

	public @NonNull String toString(boolean skipClassName) {
		return (skipClassName ? "" : getClass().getSimpleName() + " [") +
				"Version: " + hdBaseVersion +
				", Padding: " + hdBasePadding +
				", Extension: " + hdBaseExtension +
				", CC: " + hdBaseCsrcCount +
				", Marker: " + hdBaseMarker +
				", PayloadType: " + hdBasePayloadType + " (o=" + getOrgPayloadType() + ")" +
				", SequenceNumber: " + hdBaseSequenceNumber +
				", TimeStamp: " + Integer.toUnsignedString(hdBaseTimestamp) +
				", SSRC: " + Integer.toUnsignedString(hdBaseSsrc) +
				(skipClassName ? "" : "]");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the RTP packet header from the given buffer.
	 * @param packetData RTP packet bitstream including header and payload
	 * @return Information about the RTP packet header
	 */
	@SuppressWarnings("unused")
	public static RtpBaseContainerInfo parsePacketHeader(@NonNull BufferExt packetData) {
		RtpPacketContainerBase tmpCb = new RtpPacketContainerBase(packetData, true);

		return new RtpBaseContainerInfo(
				tmpCb.hdBasePayloadType,
				tmpCb.orgPayloadTypeByte,
				tmpCb.hdBaseSsrc,
				tmpCb.hdBaseSequenceNumber,
				tmpCb.hdBaseMarker,
				tmpCb.hdBaseTimestamp
			);
	}

	/**
	 * Creates a new RTP packet header with the given payload type and parameters.
	 * @param payloadType RTP payload type
	 * @param paramsBase RTP packet parameters
	 * @return RTP packet header
	 */
	public static RtpPacketContainerBase createPacketHeader(
				@NonNull RtpPacketType payloadType,
				@NonNull ParamsContainerBase paramsBase
			) {
		return new RtpPacketContainerBase(payloadType, paramsBase);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRawHeaderFromFields() {
		byte[] tmpHeader = new byte[RTP_CONT_HEADER_SIZE];

		byte tmpPadd = (byte)(hdBasePadding ? 1 : 0);
		byte tmpExt = (byte)(hdBaseExtension ? 1 : 0);
		tmpHeader[0] = (byte)((hdBaseVersion & 0x03) << 6 | tmpPadd << 5 | tmpExt << 4 | (hdBaseCsrcCount & 0x0F));
		byte tmpMarker = (byte)(hdBaseMarker ? 1 : 0);
		tmpHeader[1] = (byte)(tmpMarker << 7 | (hdBasePayloadType.getValue() & 0x7F));
		tmpHeader[2] = (byte)((hdBaseSequenceNumber & 0xFF00) >> 8);
		tmpHeader[3] = (byte)(hdBaseSequenceNumber & 0xFF);
		tmpHeader[4] = (byte)(hdBaseTimestamp >> 24);
		tmpHeader[5] = (byte)(hdBaseTimestamp >> 16);
		tmpHeader[6] = (byte)(hdBaseTimestamp >> 8);
		tmpHeader[7] = (byte)(hdBaseTimestamp & 0xFF);
		tmpHeader[8] = (byte)(hdBaseSsrc >> 24);
		tmpHeader[9] = (byte)(hdBaseSsrc >> 16);
		tmpHeader[10] = (byte)(hdBaseSsrc >> 8);
		tmpHeader[11] = (byte)(hdBaseSsrc & 0xFF);

		packetBuf.copyOf(tmpHeader);
	}

}
