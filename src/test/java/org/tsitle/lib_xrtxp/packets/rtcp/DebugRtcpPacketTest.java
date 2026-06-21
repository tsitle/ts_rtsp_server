package org.tsitle.lib_xrtxp.packets.rtcp;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.MikeyGenerator;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextInbound;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextOutbound;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRtcpPacketTest {

	static class ParseCompoundResult {
		RtcpPacketRR pktRr;
		RtcpPacketSDES pktSdes;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void testDebugRtcpPacketInbound() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0xDEADBEEFL);
		final String sdesCname = "some-cname-" + hdSsrc.toHexString(false);

		SrtxpKmd kmdNr1 = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(1L), hdSsrc);
		SrtxpKmd kmdNr2 = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(2L), hdSsrc);

		final String mikeyMsgNr1 = MikeyGenerator.generate(kmdNr1);
		System.out.println("GEN_MIKEY_1 = \"" + mikeyMsgNr1 + "\"");
		final String mikeyMsgNr2 = MikeyGenerator.generate(kmdNr2);
		System.out.println("GEN_MIKEY_2 = \"" + mikeyMsgNr2 + "\"");

		SrtcpContextOutbound srtcpContextOutboundNr1 = new SrtcpContextOutbound(kmdNr1);
		SrtcpContextOutbound srtcpContextOutboundNr2 = new SrtcpContextOutbound(kmdNr2);

		SrtcpContextInbound srtcpContextInboundNr1 = new SrtcpContextInbound(kmdNr1);
		SrtcpContextInbound srtcpContextInboundNr2 = new SrtcpContextInbound(kmdNr2);

		// ---------

		byte[] rawPktCompoundBa = buildCompoundRtcpRrPlusSdes(hdSsrc, sdesCname);
		BufferExt rawPktCompoundBe = new BufferExt(rawPktCompoundBa);

		ParseCompoundResult pcr = parseRawCompoundBuffer(rawPktCompoundBe, hdSsrc, sdesCname);
		System.out.println(pcr.pktRr);
		System.out.println(pcr.pktSdes);

		// ---------

		for (int ix = 1; ix <= 5; ix++) {
			BufferExt encrPktBuf = new BufferExt();
			srtcpContextOutboundNr1.protectRtcpSrCompound(rawPktCompoundBe, hdSsrc, encrPktBuf);
			System.out.println("GEN_SRTCP_MKI1_PKT" + ix + " = \"" + encrPktBuf.toBase64String() + "\"");

			BufferExt decrPktBuf = new BufferExt();
			srtcpContextInboundNr1.unprotectSrtcpCompound(encrPktBuf, decrPktBuf);

			ParseCompoundResult tmpDecrPcr = parseRawCompoundBuffer(decrPktBuf, hdSsrc, sdesCname);
			assertEquals(pcr.pktRr.toString(), tmpDecrPcr.pktRr.toString());
			assertEquals(pcr.pktSdes.toString(), tmpDecrPcr.pktSdes.toString());
		}

		// ---------

		for (int ix = 1; ix <= 5; ix++) {
			BufferExt encrPktBuf = new BufferExt();
			srtcpContextOutboundNr2.protectRtcpSrCompound(rawPktCompoundBe, hdSsrc, encrPktBuf);
			System.out.println("GEN_SRTCP_MKI2_PKT" + ix + " = \"" + encrPktBuf.toBase64String() + "\"");

			BufferExt decrPktBuf = new BufferExt();
			srtcpContextInboundNr2.unprotectSrtcpCompound(encrPktBuf, decrPktBuf);

			ParseCompoundResult tmpDecrPcr = parseRawCompoundBuffer(decrPktBuf, hdSsrc, sdesCname);
			assertEquals(pcr.pktRr.toString(), tmpDecrPcr.pktRr.toString());
			assertEquals(pcr.pktSdes.toString(), tmpDecrPcr.pktSdes.toString());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private static ParseCompoundResult parseRawCompoundBuffer(
				BufferExt rawCompoundBuf,
				RtspProtoIdXsrc expectedSsrcId,
				String expectedCname
			) {
		ParseCompoundResult resObj = new ParseCompoundResult();

		RtcpPacketHeader pktHeaderNrA = new RtcpPacketHeader(rawCompoundBuf);
		final int tmpPktSzNrA = pktHeaderNrA.getPacketSize();
		BufferExt rawPktBeNrA = new BufferExt();
		rawPktBeNrA.copyOf(rawCompoundBuf, 0, tmpPktSzNrA);  // contains the first packet

		BufferExt rawPktBeNrB = new BufferExt();
		rawPktBeNrB.copyOf(rawCompoundBuf, tmpPktSzNrA, rawCompoundBuf.getUsed() - tmpPktSzNrA);  // contains the second packet

		// ---------

		resObj.pktRr = new RtcpPacketRR(pktHeaderNrA, rawPktBeNrA);
		assertEquals(expectedSsrcId, resObj.pktRr.getSsrcSender());

		RtcpPacketHeader pktHeaderNrB = new RtcpPacketHeader(rawPktBeNrB);
		resObj.pktSdes = new RtcpPacketSDES(pktHeaderNrB, rawPktBeNrB);

		for (int ix = 1; ix <= resObj.pktSdes.getItemsCount(); ix++) {
			Optional<RtcpInnerXsrcBlock> item = resObj.pktSdes.getXsrcBlock(ix);
			assertTrue(item.isPresent());
			assertEquals(expectedSsrcId, item.get().getXsrcId());
			for (RtcpInnerXsrcBlock.BlockEntry entry : item.get().getBlockEntries()) {
				assertEquals(RtcpInnerXsrcBlock.BlockType.CNAME, entry.getType());
				assertEquals(expectedCname, entry.getValue());
			}
		}

		return resObj;
	}

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildCompoundRtcpRrPlusSdes(RtspProtoIdXsrc senderSsrc, String cname) {
		byte[] cnameBytes = cname.getBytes(StandardCharsets.UTF_8);
		if (cnameBytes.length == 0 || cnameBytes.length > 255) {
			throw new IllegalArgumentException("SDES CNAME length must be in range [1..255]");
		}

		int tmpSsrcInt = senderSsrc.getId32bit().orElse(0L).intValue();

		// RTCP RR (RC=0): 8 bytes total (length=1)
		byte[] rr = new byte[8];
		ByteBuffer rrBuf = ByteBuffer.wrap(rr).order(ByteOrder.BIG_ENDIAN);
		rrBuf.put((byte) 0x80).put((byte) 0xC9).putShort((short) 0x0001);  // V=2, PT=RR(201), length=1
		rrBuf.putInt(tmpSsrcInt);                                          // reporter SSRC

		// RTCP SDES (SC=1), one chunk with one CNAME item, padded to 32-bit boundary
		int chunkNoPadLen = 4 + 2 + cnameBytes.length + 1;                 // SSRC + (type,len) + cname + END(0)
		int chunkLen = (chunkNoPadLen + 3) & ~0x03;                        // align to 4 bytes
		byte[] sdes = new byte[4 + chunkLen];                              // header + payload
		ByteBuffer sdesBuf = ByteBuffer.wrap(sdes).order(ByteOrder.BIG_ENDIAN);

		short sdesLengthField = (short) ((sdes.length / 4) - 1);             // RTCP length in 32-bit words minus 1
		sdesBuf.put((byte) 0x81).put((byte) 0xCA).putShort(sdesLengthField); // V=2, SC=1, PT=SDES(202)
		sdesBuf.putInt(tmpSsrcInt);                                          // chunk SSRC/CSRC
		sdesBuf.put((byte) 0x01);                                            // SDES item type: CNAME
		sdesBuf.put((byte) (cnameBytes.length & 0xFF));                      // CNAME length
		sdesBuf.put(cnameBytes);
		sdesBuf.put((byte) 0x00);                                            // END item
		while (sdesBuf.hasRemaining()) {
			sdesBuf.put((byte) 0x00);                                        // required padding
		}

		byte[] compound = new byte[rr.length + sdes.length];
		System.arraycopy(rr, 0, compound, 0, rr.length);
		System.arraycopy(sdes, 0, compound, rr.length, sdes.length);
		return compound;
	}

}
