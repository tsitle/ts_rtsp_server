package org.tsitle.rtsp.packets.rtcp;

import org.tsitle.rtsp.buffers.BufferExt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * RTCP Goodbye Packet.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.6">RFC-3550 Section 6.6</a>
 */
public class RtcpPacketBYE {

	/** Size of one SSRC/CSRC */
	public static final int XSRC_ENTRY_SIZE = 4;

	/** Sender Synchronization/Contributing Source Identifiers that are unsubscribing from the stream (32 bits each) */
	private final List<Integer> bdXsrcList = new ArrayList<>();
	/** Optional Reason for Leaving (UTF-8 encoded string, max. 255 bytes) */
	private final String bdReasonForLeaving;

	/** Packet header */
	private final RtcpPacketHeader mainPktHd;
	/** Bitstream of the payload */
	private final BufferExt rawPayload = new BufferExt();

	/**
	 * Constructor.
	 * @param xsrcList Sender Synchronization/Contributing Source Identifiers that are unsubscribing from the stream (can be empty)
	 * @param optionalReasonForLeaving Optional reason for leaving the session (UTF-8 encoded string, max. 255 bytes)
	 */
	@SuppressWarnings("unused")
	public RtcpPacketBYE(List<Integer> xsrcList, String optionalReasonForLeaving) {
		final int lenRfl = (optionalReasonForLeaving == null ? 0 : optionalReasonForLeaving.length());
		if (optionalReasonForLeaving != null && lenRfl > 255) {
			throw new IllegalArgumentException("Invalid Reason-for-Leaving: max. 255 bytes");
		}
		int additionalPayloadSize = (lenRfl == 0 ? 0 : lenRfl + 1);
		if (lenRfl > 0 && additionalPayloadSize % 4 != 0) {
			additionalPayloadSize += (4 - (additionalPayloadSize % 4));
		}

		//
		final int allItemsPayloadSize = (xsrcList == null ? 0 : XSRC_ENTRY_SIZE * xsrcList.size());
		this.mainPktHd = new RtcpPacketHeader(
				RtcpPacketType.BYE,
				(byte)(xsrcList == null ? 0 : xsrcList.size()),
				allItemsPayloadSize + additionalPayloadSize
			);

		//
		if (xsrcList != null) {
			bdXsrcList.addAll(xsrcList);
		}
		this.bdReasonForLeaving = optionalReasonForLeaving;

		// Construct the bitstream
		byte[] tmpBuf = new byte[allItemsPayloadSize + additionalPayloadSize];
		ByteBuffer bb = ByteBuffer.wrap(tmpBuf);  // big-endian by default
		if (xsrcList != null) {
			for (Integer xsrc : xsrcList) {
				bb.putInt(xsrc);
			}
		}
		if (additionalPayloadSize > 0) {
			bb.put((byte)lenRfl);
			bb.put(optionalReasonForLeaving.getBytes());
			// padding
			additionalPayloadSize -= (lenRfl + 1);
			while (additionalPayloadSize-- > 0) {
				bb.put((byte)0);
			}
		}
		this.rawPayload.copyOf(tmpBuf);
	}

	/**
	 * Constructor.
	 * @param mainPacketHeader Packet header
	 * @param packet Raw packet bitstream which contains the main RTCP header and may contain zero or more SSRC/CSRC entries
	 */
	@SuppressWarnings("unused")
	public RtcpPacketBYE(RtcpPacketHeader mainPacketHeader, BufferExt packet) {
		if (mainPacketHeader.getPayloadType() != RtcpPacketType.BYE) {
			throw new IllegalArgumentException("Invalid RTCP packet type");
		}
		final int allItemsPayloadSize = (XSRC_ENTRY_SIZE * mainPacketHeader.getItemsCount());
		if (packet.getUsed() < RtcpPacketHeader.HEADER_SIZE + allItemsPayloadSize ||
				packet.getUsed() < mainPacketHeader.getPacketSize()) {
			throw new IllegalArgumentException("Invalid RTCP packet size");
		}
		this.mainPktHd = mainPacketHeader;
		this.rawPayload.copyOf(packet, RtcpPacketHeader.HEADER_SIZE, allItemsPayloadSize);

		// Parse payload fields
		ByteBuffer bb = ByteBuffer.wrap(this.rawPayload.getBufPtr());  // big-endian by default
		int totalBytesRead = RtcpPacketHeader.HEADER_SIZE;
		for (int i = 1; i <= mainPacketHeader.getItemsCount(); i++) {
			this.bdXsrcList.add(bb.getInt());
			totalBytesRead += XSRC_ENTRY_SIZE;
		}
		String tmpRflStr = "";
		if (totalBytesRead + 1 < mainPacketHeader.getPacketSize()) {
			byte tmpLenRfl = bb.get();
			++totalBytesRead;
			if (tmpLenRfl > mainPacketHeader.getPacketSize() - totalBytesRead) {
				throw new IllegalArgumentException("Invalid Reason-for-Leaving length");
			}
			byte[] tmpBufRfl = new byte[tmpLenRfl];
			bb.get(tmpBufRfl);
			tmpRflStr = new String(tmpBufRfl);
		}
		this.bdReasonForLeaving = tmpRflStr;
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
		int tmpLenRfl = bdReasonForLeaving.length();
		if (tmpLenRfl != 0) {
			++tmpLenRfl;
			if (tmpLenRfl % 4 != 0) {
				tmpLenRfl += (4 - (tmpLenRfl % 4));
			}
		}
		return (RtcpPacketHeader.HEADER_SIZE + (bdXsrcList.size() * XSRC_ENTRY_SIZE) + tmpLenRfl);
	}

	/**
	 * Get the Sender SSRC/CSRCs that are unsubscribing from the stream.
	 * @return SSRC/CSRCs
	 */
	@SuppressWarnings("unused")
	public List<Integer> getXsrcList() {
		return new ArrayList<>(bdXsrcList);
	}

	/**
	 * Get the optional reason for leaving the session.
	 * @return Reason for leaving
	 */
	@SuppressWarnings("unused")
	public String getReasonForLeaving() {
		return bdReasonForLeaving;
	}

	@Override
	public String toString() {
		String tmpXsrcs = String.join(", ",
				bdXsrcList.stream()
						.mapToInt(Integer::intValue)
						.mapToObj(x -> String.format("0x%08X", x))
						.toArray(String[]::new)
			);
		return getClass().getSimpleName() + " [" +
				mainPktHd +
				", XSRC Count: " + bdXsrcList.size() +
				", XSRCs: " + tmpXsrcs +
				", RFL: '" + bdReasonForLeaving + "'" +
				"]";
	}

}
