package org.tsitle.rtsp.packets.rtcp;

import org.tsitle.rtsp.buffers.BufferExt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RTCP Source Description Packet.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.5">RFC-3550 Section 6.5</a>
 */
public class RtcpPacketSDES {

	/** SSRC/CSRC Blocks */
	private final List<RtcpInnerXsrcBlock> xsrcBlocks = new ArrayList<>();

	/** Packet header */
	private final RtcpPacketHeader mainPktHd;
	/** Bitstream of the payload */
	private final BufferExt rawPayload = new BufferExt();

	/**
	 * Constructor.
	 * @param xsrcBlocks SSRC/CSRC Blocks (can be empty)
	 */
	public RtcpPacketSDES(List<RtcpInnerXsrcBlock> xsrcBlocks) {
		if (xsrcBlocks == null || xsrcBlocks.isEmpty()) {
			throw new IllegalArgumentException("SSRC/CSRC Blocks == null || empty");
		}
		for (RtcpInnerXsrcBlock block : xsrcBlocks) {
			boolean haveCname = block.getBlockEntries().stream()
					.anyMatch(blockEntry -> blockEntry.getType() == RtcpInnerXsrcBlock.BlockType.CNAME);
			if (! haveCname) {
				throw new IllegalArgumentException("All SSRC/CSRC Blocks must contain a CNAME entry");
			}
			this.xsrcBlocks.add(block.clone());
		}

		//
		final int allItemsPayloadSize = getAllItemsPayloadSize();
		this.mainPktHd = new RtcpPacketHeader(
				RtcpPacketType.SDES,
				(byte)this.xsrcBlocks.size(),
				allItemsPayloadSize
			);

		// Construct the bitstream
		byte[] tmpBuf = new byte[allItemsPayloadSize];
		ByteBuffer bb = ByteBuffer.wrap(tmpBuf);  // big-endian by default
		for (RtcpInnerXsrcBlock block : xsrcBlocks) {
			block.appendToBuffer(bb);
		}
		this.rawPayload.copyOf(tmpBuf);
	}

	/**
	 * Constructor.
	 * @param mainPacketHeader Packet header
	 * @param packet Raw packet bitstream which contains the main RTCP header and may contain zero or more XSRCBs
	 */
	@SuppressWarnings("unused")
	public RtcpPacketSDES(RtcpPacketHeader mainPacketHeader, BufferExt packet) {
		if (mainPacketHeader.getPayloadType() != RtcpPacketType.SDES) {
			throw new IllegalArgumentException("Invalid RTCP packet type");
		}
		if (packet.getUsed() < RtcpPacketHeader.HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTCP packet size");
		}
		this.mainPktHd = mainPacketHeader;
		BufferExt tmpPayloadBuf = new BufferExt();
		if (mainPacketHeader.getItemsCount() > 0) {
			tmpPayloadBuf.copyOf(packet, RtcpPacketHeader.HEADER_SIZE, packet.getUsed() - RtcpPacketHeader.HEADER_SIZE);
		}

		// Parse payload fields
		ByteBuffer bbIn = ByteBuffer.wrap(tmpPayloadBuf.getBuf());  // big-endian by default
		for (int i = 1; i <= mainPacketHeader.getItemsCount(); i++) {
			RtcpInnerXsrcBlock block = RtcpInnerXsrcBlock.decodeFromBuffer(i, bbIn);
			this.xsrcBlocks.add(block);
		}

		// Re-encode the payload bitstream
		final int allItemsPayloadSize = getAllItemsPayloadSize();
		byte[] tmpReBuf = new byte[allItemsPayloadSize];
		ByteBuffer bbRe = ByteBuffer.wrap(tmpReBuf);  // big-endian by default
		for (RtcpInnerXsrcBlock block : xsrcBlocks) {
			block.appendToBuffer(bbRe);
		}
		this.rawPayload.copyOf(tmpReBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the header and payload of the RTCP packet into the given buffer.
	 * @param packetBuf Buffer to copy the raw packet data into
	 */
	@SuppressWarnings("unused")
	public void copyRawPacketDataInto(BufferExt packetBuf) {
		// construct the packet = header + payload
		mainPktHd.copyRawPacketHeaderDataInto(packetBuf);
		packetBuf.copyFrom(rawPayload, 0, packetBuf.getUsed(), rawPayload.getUsed());
	}

	/**
	 * Returns the total length of the raw RTCP packet, including the header and payload.
	 * @return Size of the raw packet
	 */
	@SuppressWarnings("unused")
	public int getRawPacketSize() {
		return (RtcpPacketHeader.HEADER_SIZE + getAllItemsPayloadSize());
	}

	/**
	 * Get the main packet header.
	 * @return Main Packet header
	 */
	@SuppressWarnings("unused")
	public RtcpPacketHeader getMainPacketHeader() {
		return mainPktHd.clone();
	}

	/**
	 * Get the number of items/blocks in this packet.
	 * @return Item count
	 */
	@SuppressWarnings("unused")
	public int getItemsCount() {
		return mainPktHd.getItemsCount();
	}

	/**
	 * Get the SSRC/CSRC Block with the given number.
	 * @param itemNr SSRC/CSRC Block number, starting with 1
	 * @return SSRC/CSRC Block
	 */
	@SuppressWarnings("unused")
	public Optional<RtcpInnerXsrcBlock> getXsrcBlock(int itemNr) {
		if (itemNr < 1 || itemNr > xsrcBlocks.size()) {
			throw new IllegalArgumentException("Invalid SSRC/CSRC Block number");
		}
		Optional<RtcpInnerXsrcBlock> optBlock = xsrcBlocks.stream()
				.filter(rb -> rb.getItemNr() == itemNr)
				.findFirst();
		return optBlock.map(RtcpInnerXsrcBlock::clone);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				mainPktHd +
				", XSRCB Count: " + xsrcBlocks.size() +
				", " + xsrcBlocks +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private int getAllItemsPayloadSize() {
		int allItemsPayloadSize = 0;
		for (RtcpInnerXsrcBlock block : xsrcBlocks) {
			allItemsPayloadSize += block.getPayloadSizeForBlock();
		}
		return allItemsPayloadSize;
	}

}
