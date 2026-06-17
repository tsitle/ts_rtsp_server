package org.tsitle.rtsp.packets.rtcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RTCP Sender Report Packet.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.4.1">RFC-3550 Section 6.4.1</a>
 */
public class RtcpPacketSR {

	/** Size of the inner RTCP header */
	public static final int INNER_HEADER_SIZE = 4;

	/** Synchronization Source Identifier of sender (32 bits) */
	private final @NonNull RtspProtoIdXsrc hdSsrcSender;
	/** Sender Info Block */
	private final RtcpInnerSenderInfoBlock senderInfoBlock;
	/** Reception Report Blocks */
	private final List<RtcpInnerRecpReportBlock> recpReportBlocks = new ArrayList<>();

	/** Packet header */
	private final RtcpPacketHeader mainPktHd;
	/** Bitstream of the payload */
	private final BufferExt rawPayload = new BufferExt();

	/**
	 * Constructor.
	 * @param ssrcSender Sender SSRC
	 * @param senderInfoBlock Sender Info Block
	 * @param recpReportBlocks Reception Report Blocks (can be empty)
	 */
	public RtcpPacketSR(
				@NonNull RtspProtoIdXsrc ssrcSender,
				@NonNull RtcpInnerSenderInfoBlock senderInfoBlock,
				@Nullable List<@NonNull RtcpInnerRecpReportBlock> recpReportBlocks
			) {
		if (recpReportBlocks != null && recpReportBlocks.size() > 255) {
			throw new IllegalArgumentException("Invalid RTCP RR packet: invalid number of RRBs");
		}

		//
		this.hdSsrcSender = ssrcSender.clone();

		//
		this.senderInfoBlock = senderInfoBlock.clone();
		if (recpReportBlocks != null) {
			for (RtcpInnerRecpReportBlock block : recpReportBlocks) {
				this.recpReportBlocks.add(block.clone());
			}
		}

		//
		final int allItemsPayloadSize = (RtcpInnerSenderInfoBlock.PAYLOAD_SIZE +
				(recpReportBlocks == null ? 0 : RtcpInnerRecpReportBlock.PAYLOAD_SIZE * recpReportBlocks.size()));
		this.mainPktHd = new RtcpPacketHeader(
				RtcpPacketType.SR,
				(byte)this.recpReportBlocks.size(),
				INNER_HEADER_SIZE + allItemsPayloadSize
			);

		// Construct the bitstream
		byte[] tmpBuf = new byte[INNER_HEADER_SIZE + allItemsPayloadSize];
		ByteBuffer bb = ByteBuffer.wrap(tmpBuf);  // big-endian by default
		bb.putInt(this.hdSsrcSender.getId32bit().orElse(0L).intValue());
		senderInfoBlock.appendToBuffer(bb);
		if (recpReportBlocks != null) {
			for (RtcpInnerRecpReportBlock block : recpReportBlocks) {
				block.appendToBuffer(bb);
			}
		}
		this.rawPayload.copyOf(tmpBuf);
	}

	/**
	 * Constructor.
	 * @param mainPacketHeader Packet header
	 * @param packet Raw packet bitstream which contains the main RTCP header, the SIB and may contain zero or more RRBs
	 */
	public RtcpPacketSR(@NonNull RtcpPacketHeader mainPacketHeader, @NonNull BufferExt packet) {
		if (mainPacketHeader.getPayloadType() != RtcpPacketType.SR) {
			throw new IllegalArgumentException("Invalid RTCP packet type");
		}
		final int allItemsPayloadSize = (RtcpInnerSenderInfoBlock.PAYLOAD_SIZE +
				RtcpInnerRecpReportBlock.PAYLOAD_SIZE * mainPacketHeader.getItemsCount());
		if (packet.getUsed() < RtcpPacketHeader.HEADER_SIZE + INNER_HEADER_SIZE + allItemsPayloadSize) {
			throw new IllegalArgumentException("Invalid RTCP packet size");
		}
		this.mainPktHd = mainPacketHeader;
		if (mainPacketHeader.getItemsCount() > 0) {
			this.rawPayload.copyOf(packet, RtcpPacketHeader.HEADER_SIZE, INNER_HEADER_SIZE + allItemsPayloadSize);
		}

		// Parse payload fields
		ByteBuffer bb = ByteBuffer.wrap(this.rawPayload.getBaPtr(), 0, this.rawPayload.getUsed());  // big-endian by default
		this.hdSsrcSender = RtspProtoIdXsrc.ofEmpty();
		try {
			this.hdSsrcSender.setId32bit(Integer.toUnsignedLong(bb.getInt()));
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		this.senderInfoBlock = RtcpInnerSenderInfoBlock.decodeFromBuffer(bb);
		for (int i = 1; i <= mainPacketHeader.getItemsCount(); i++) {
			RtcpInnerRecpReportBlock block = RtcpInnerRecpReportBlock.decodeFromBuffer(i, bb);
			this.recpReportBlocks.add(block);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Copies the header and payload of the RTCP packet into the given buffer.
	 * @param packetBuf Buffer to copy the raw packet data into
	 */
	@SuppressWarnings("unused")
	public void copyRawPacketDataInto(@NonNull BufferExt packetBuf) {
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
		return (RtcpPacketHeader.HEADER_SIZE + INNER_HEADER_SIZE +RtcpInnerSenderInfoBlock.PAYLOAD_SIZE +
				(recpReportBlocks.size() * RtcpInnerRecpReportBlock.PAYLOAD_SIZE));
	}

	/**
	 * Get the SSRC of the sender of the RTCP packet.
	 * @return SSRC of the sender
	 */
	@SuppressWarnings("unused")
	public @NonNull RtspProtoIdXsrc getSsrcSender() {
		return hdSsrcSender.clone();
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
	 * Get the Sender Info Block.
	 * @return Sender Info Block
	 */
	@SuppressWarnings("unused")
	public @NonNull RtcpInnerSenderInfoBlock getSenderInfoBlock() {
		return senderInfoBlock.clone();
	}

	/**
	 * Get the Reception Report Block with the given number.
	 * @param itemNr Reception Report Block number, starting with 1
	 * @return Report Block
	 */
	@SuppressWarnings("unused")
	public Optional<RtcpInnerRecpReportBlock> getReportBlock(int itemNr) {
		if (itemNr < 1 || itemNr > recpReportBlocks.size()) {
			throw new IllegalArgumentException("Invalid Reception Report Block number");
		}
		Optional<RtcpInnerRecpReportBlock> optBlock = recpReportBlocks.stream()
				.filter(rb -> rb.getItemNr() == itemNr)
				.findFirst();
		return optBlock.map(RtcpInnerRecpReportBlock::clone);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				mainPktHd.toString(true) +
				", SSRC Sender: " + hdSsrcSender.toHexString(true) +
				", " + senderInfoBlock +
				", RRB Count: " + recpReportBlocks.size() +
				", " + recpReportBlocks +
				"]";
	}

}
