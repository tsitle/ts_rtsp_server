package org.tsitle.rtsp.packets.rtp;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DebugRtpPacketTest {

	@Test
	public void debugRtpPacket() {
		final BufferExt tmpBufferExt = BufferExt.decodeHexString("80 00 bf 65 a8 9f ed 09 f2 3d d9 09");
		RtpBaseContainerInfo rbci = RtpPacketContainerBase.parsePacketHeader(tmpBufferExt);
		RtpPacketContainerBase tmpRtpPacket = new RtpPacketContainerBase(rbci.payloadType(), tmpBufferExt);

		System.out.println("RTP packet header: " + tmpRtpPacket);

		assertEquals(0xF23DD909, tmpRtpPacket.getSsrcId());
	}

}
