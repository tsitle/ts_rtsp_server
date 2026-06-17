package org.tsitle.rtsp.packets.rtcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * RTCP Goodbye Packet.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.6">RFC-3550 Section 6.6</a>
 */
public final class RtcpPacketBYE {

	/** Size of one SSRC/CSRC */
	public static final int XSRC_ENTRY_SIZE = 4;

	/** Sender Synchronization/Contributing Source Identifiers that are unsubscribing from the stream (32 bits each) */
	private final List<@NonNull RtspProtoIdXsrc> bdXsrcList = new ArrayList<>();
	/** Optional Reason for Leaving (UTF-8 encoded string, max. 255 bytes) */
	private final @NonNull String bdReasonForLeaving;

	/** Packet header */
	private final RtcpPacketHeader mainPktHd;
	/** Bitstream of the payload */
	private final BufferExt rawPayload = new BufferExt();

	/**
	 * Constructor.
	 * @param xsrcList Sender Synchronization/Contributing Source Identifiers that are unsubscribing from the stream (can be empty)
	 * @param optionalReasonForLeaving Optional reason for leaving the session (UTF-8 encoded string, max. 255 bytes)
	 */
	public RtcpPacketBYE(@Nullable List<@NonNull RtspProtoIdXsrc> xsrcList, @Nullable String optionalReasonForLeaving) {
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
		this.bdReasonForLeaving = (optionalReasonForLeaving == null ? "" : optionalReasonForLeaving);

		// Construct the bitstream
		byte[] tmpBuf = new byte[allItemsPayloadSize + additionalPayloadSize];
		ByteBuffer bb = ByteBuffer.wrap(tmpBuf);  // big-endian by default
		if (xsrcList != null) {
			for (RtspProtoIdXsrc xsrc : xsrcList) {
				bb.putInt(xsrc.getId32bit().orElse(0L).intValue());
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
	public RtcpPacketBYE(@NonNull RtcpPacketHeader mainPacketHeader, @NonNull BufferExt packet) {
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
		ByteBuffer bb = ByteBuffer.wrap(this.rawPayload.getBaPtr(), 0, this.rawPayload.getUsed());  // big-endian by default
		int totalBytesRead = RtcpPacketHeader.HEADER_SIZE;
		for (int i = 1; i <= mainPacketHeader.getItemsCount(); i++) {
			RtspProtoIdXsrc tmpSsrc = RtspProtoIdXsrc.ofEmpty();
			try {
				tmpSsrc.setId32bit(Integer.toUnsignedLong(bb.getInt()));
			} catch (RtspProtoNumberRangeException e) {
				// this will never happen
			}
			this.bdXsrcList.add(tmpSsrc);
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
	public void copyRawPacketDataInto(@NonNull BufferExt packetBuf) {
		// construct the packet = header + payload
		mainPktHd.copyRawPacketHeaderDataInto(packetBuf);
		packetBuf.copyFrom(rawPayload, 0, packetBuf.getUsed(), rawPayload.getUsed());
	}

	/**
	 * Returns the total length of the raw RTCP packet, including the header and payload.
	 * @return Size of the raw packet
	 */
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
	public @NonNull List<@NonNull RtspProtoIdXsrc> getXsrcList() {
		List<RtspProtoIdXsrc> resL = new ArrayList<>();
		for (RtspProtoIdXsrc xsrc : bdXsrcList) {
			resL.add(xsrc.clone());
		}
		return resL;
	}

	/**
	 * Get the optional reason for leaving the session.
	 * @return Reason for leaving
	 */
	@SuppressWarnings("unused")
	public @NonNull String getReasonForLeaving() {
		return bdReasonForLeaving;
	}

	@Override
	public @NonNull String toString() {
		String tmpXsrcs = String.join(", ",
				bdXsrcList.stream()
						.map(xsrc -> (xsrc.isEmpty() ? "unset" : xsrc.toHexString(true)))
						.toArray(String[]::new)
			);
		return getClass().getSimpleName() + " [" +
				mainPktHd.toString(true) +
				", XSRC Count: " + bdXsrcList.size() +
				", XSRCs: " + tmpXsrcs +
				", RFL: '" + bdReasonForLeaving + "'" +
				"]";
	}

}
