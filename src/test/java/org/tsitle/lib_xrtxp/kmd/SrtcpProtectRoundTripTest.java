package org.tsitle.lib_xrtxp.kmd;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidMkiException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;
import org.tsitle.lib_xrtxp.kmd.types.DynInteger;
import org.tsitle.lib_xrtxp.kmd.types.SessionKeys;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.packets.rtcp.*;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SrtcpProtectRoundTripTest {

	@Test
	void protect_then_unprotect_srtcp_compound_sr_should_restore_original_packet() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x11223344L);

		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		SrtcpContextOutbound senderCtx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		SrtcpContextInbound receiverCtx = Common.createSrtcpCtxInboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		Common.srtcpCtxInjectKeys(senderCtx, rtcpKeys);
		Common.srtcpCtxInjectKeys(receiverCtx, rtcpKeys);

		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(hdSsrc);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, hdSsrc, encryptedBuf);

		byte[] encrypted = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, encrypted, 0, encrypted.length);
		assertFalse(Arrays.equals(compoundRtcp, encrypted), "Encrypted packet should differ from plaintext");

		BufferExt decryptedBuf = new BufferExt();
		receiverCtx.unprotectSrtcpCompound(encryptedBuf, decryptedBuf);

		byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);

		assertArrayEquals(compoundRtcp, decrypted, "Decrypted compound RTCP packet must match original");
	}

	@Test
	void protect_then_unprotect_randomized_packets_should_restore_original_and_reject_replay() throws Exception {
		final int rounds = 100;

		final SecureRandom rnd = new SecureRandom();

		for (int i = 0; i < rounds; i++) {
			int mkeyLen = (rnd.nextInt(1000) > 500 ? KeySizes.AES_KEY_SIZE_128 : KeySizes.AES_KEY_SIZE_256);
			int authKeyLen = (rnd.nextInt(1000) > 500 ? KeySizes.AUTH_KEY_SIZE_080 : KeySizes.AUTH_KEY_SIZE_160);
			int authTagLen = (rnd.nextInt(1000) > 500 ? KeySizes.SHA1_SIZE_160 / 2 : KeySizes.SHA1_SIZE_160);
			int mkiLen = switch (rnd.nextInt(5)) {
					case 0 -> 0;
					case 1 -> 1;
					case 2 -> 2;
					case 3 -> 4;
					default -> 8;
				};
			subfnc_rtcp_randomized_sub1(rnd, mkeyLen, authKeyLen, authTagLen, mkiLen);
		}
	}

	private void subfnc_rtcp_randomized_sub1(
				final SecureRandom rnd,
				int mkeyLen,
				int authKeyLen,
				int authTagLen,
				int mkiLen
			) throws Exception {
		final int rounds = 64;

		byte[] masterKeyBa = new byte[mkeyLen];
		byte[] masterSaltBa = new byte[KeySizes.SALT_SIZE];
		byte[] mkiBa = new byte[mkiLen];
		rnd.nextBytes(masterKeyBa);
		rnd.nextBytes(masterSaltBa);
		if (mkiLen > 0) {
			rnd.nextBytes(mkiBa);
		}

		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(Integer.toUnsignedLong(rnd.nextInt()));

		SrtxpKmd rtcpKmd = new SrtxpKmd(
				false,
				-1,
				mkeyLen,
				new BufferExt(masterKeyBa),
				new BufferExt(masterSaltBa),
				authKeyLen,
				authTagLen,
				DynInteger.ofBufferBigEndian(new BufferExt(mkiBa)),
				hdSsrc,
				DynInteger.ofEmpty()
			);
		SrtcpContextOutbound senderCtx = new SrtcpContextOutbound(rtcpKmd);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		SrtcpContextInbound receiverCtx = new SrtcpContextInbound(rtcpKmd);

		for (int i = 0; i < rounds; i++) {
			subfnc_rtcp_randomized_sub2(rnd, senderCtx, receiverCtx, i, hdSsrc);
		}
	}

	private void subfnc_rtcp_randomized_sub2(
				final SecureRandom rnd,
				final SrtcpContextOutbound senderCtx,
				final SrtcpContextInbound receiverCtx,
				final int roundNr,
				final RtspProtoIdXsrc hdSsrc
			) throws Exception {
		long ntpMsw = Integer.toUnsignedLong(rnd.nextInt());
		long ntpLsw = Integer.toUnsignedLong(rnd.nextInt());
		long rtpTs = Integer.toUnsignedLong(rnd.nextInt());
		long pktCount = Integer.toUnsignedLong(rnd.nextInt());
		long octetCount = Integer.toUnsignedLong(rnd.nextInt());

		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(
				hdSsrc, ntpMsw, ntpLsw, rtpTs, pktCount, octetCount
			);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, hdSsrc, encryptedBuf);

		BufferExt decryptedBuf = new BufferExt();
		receiverCtx.unprotectSrtcpCompound(encryptedBuf, decryptedBuf);

		byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);
		assertArrayEquals(compoundRtcp, decrypted, "Round " + roundNr + ": decrypted packet mismatch");

		BufferExt replayOut = new BufferExt();
		assertThrows(
				SrtxpSecurityException.class,
				() -> receiverCtx.unprotectSrtcpCompound(encryptedBuf, replayOut),
				"Round " + roundNr + ": replayed packet must be rejected due to index check"
			);
	}

	@Test
	void unprotect_should_fail_when_encrypted_packet_is_tampered() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x55667788L);

		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		SrtcpContextOutbound senderCtx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		SrtcpContextInbound receiverCtx = Common.createSrtcpCtxInboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		Common.srtcpCtxInjectKeys(senderCtx, rtcpKeys);
		Common.srtcpCtxInjectKeys(receiverCtx, rtcpKeys);

		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(hdSsrc);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, hdSsrc, encryptedBuf);

		byte[] tampered = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, tampered, 0, tampered.length);

		// Flip one bit in encrypted payload area (beyond RTCP header+SR SSRC)
		int tamperPos = 12;
		tampered[tamperPos] ^= 0x01;

		BufferExt tamperedBuf = new BufferExt();
		tamperedBuf.copyOf(tampered);

		BufferExt out = new BufferExt();
		SrtxpInvalidAuthTagException ex = assertThrows(
				SrtxpInvalidAuthTagException.class,
				() -> receiverCtx.unprotectSrtcpCompound(tamperedBuf, out)
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid Auth Tag"),
				"Tampered packet must fail auth-tag validation"
			);
	}

	@Test
	void protect_then_unprotect_srtcp_compound_sr_with_mki_should_restore_original_and_reject_wrong_mki() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x10203040L);

		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		SrtcpContextOutbound senderCtx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		SrtcpContextInbound receiverCtx = Common.createSrtcpCtxInboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		Common.srtcpCtxInjectKeys(senderCtx, rtcpKeys);
		Common.srtcpCtxInjectKeys(receiverCtx, rtcpKeys);

		DynInteger mki = DynInteger.of(16909060L, 4);
		senderCtx.setKmdMasterKeyIdentifier(mki);
		receiverCtx.setKmdMasterKeyIdentifier(mki);

		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(hdSsrc);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, hdSsrc, encryptedBuf);

		BufferExt decryptedBuf = new BufferExt();
		receiverCtx.unprotectSrtcpCompound(encryptedBuf, decryptedBuf);

		byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);
		assertArrayEquals(compoundRtcp, decrypted, "SRTCP round-trip with MKI must restore original packet");

		// -----------------------------------------------

		byte[] tampered = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, tampered, 0, tampered.length);

		int mkiStart = tampered.length - Common.AUTH_TAG_SIZE_FOR_ALL_TESTS - mki.getSizeBytes();
		tampered[mkiStart] ^= 0x01;

		BufferExt tamperedBuf = new BufferExt();
		tamperedBuf.copyOf(tampered);

		BufferExt out = new BufferExt();
		SrtxpInvalidMkiException ex = assertThrows(
				SrtxpInvalidMkiException.class,
				() -> receiverCtx.unprotectSrtcpCompound(tamperedBuf, out),
				"Packet with wrong MKI must be rejected"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid MKI in SRTCP packet"),
				"Failure reason should indicate MKI validation"
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void roundtrip_with_known_packet() throws RtspProtoNumberRangeException, SrtxpSecurityException {
		final String mikeyMsgB64 = "AQAFADz5kKIBAADerb7vAAAAAAsA7d4+bsPm+vIKENrdvQhuG0nwXk0NIpVKWBoBAAAAHgABAQEBEAIBAQMB" +
				"FAQBDgUBAAcBAQgBAQoBAQsBCgAAACcAIQAe42MKCqTuSRR9d8pZmdL+34UEjtyJeGV8djDZHNkIBAAAAAEA";
		final String srtcpCompoundPktB64 = "gMkAAd6tvu/bQwNBO4mNZroz0pIDXKiaIXhTuZQ48pembvOGPy6P9IAAAAAAAAABp4pKbunMezuFDQ==";
		final RtspProtoIdXsrc expSenderSsrc = RtspProtoIdXsrc.of(0xDEADBEEFL);
		final String expCnameSdes = "some-cname-DEADBEEF";

		SrtxpKmd kmd = MikeyParser.parseMickeyMsgIntoKmd(mikeyMsgB64);

		BufferExt srtcpCompoundPktBuf = BufferExt.decodeBase64String(srtcpCompoundPktB64);

		SrtcpContextInbound contextInbound = new SrtcpContextInbound(kmd);
		BufferExt rtcpCompoundPktBuf = new BufferExt();
		contextInbound.unprotectSrtcpCompound(srtcpCompoundPktBuf, rtcpCompoundPktBuf);

		RtcpPacketHeader rtcpPacketHeader = new RtcpPacketHeader(rtcpCompoundPktBuf);
		assertEquals(RtcpPacketType.RR, rtcpPacketHeader.getPayloadType());

		BufferExt rtcpPktRrBuf = new BufferExt();
		BufferExt rtcpPktSdesBuf = new BufferExt();
		rtcpPktRrBuf.copyFrom(rtcpCompoundPktBuf, 0, 0, rtcpPacketHeader.getPacketSize());
		rtcpPktSdesBuf.copyFrom(
				rtcpCompoundPktBuf,
				rtcpPacketHeader.getPacketSize(),
				0,
				rtcpCompoundPktBuf.getUsed() - rtcpPacketHeader.getPacketSize()
			);

		RtcpPacketRR rtcpPacketRr = new RtcpPacketRR(rtcpPacketHeader, rtcpPktRrBuf);
		assertEquals(expSenderSsrc, rtcpPacketRr.getSsrcSender());

		rtcpPacketHeader = new RtcpPacketHeader(rtcpPktSdesBuf);
		assertEquals(RtcpPacketType.SDES, rtcpPacketHeader.getPayloadType());

		RtcpPacketSDES rtcpPacketSdes = new RtcpPacketSDES(rtcpPacketHeader, rtcpPktSdesBuf);
		assertEquals(1, rtcpPacketSdes.getItemsCount());
		assertTrue(rtcpPacketSdes.getXsrcBlock(1).isPresent());
		assertEquals(expSenderSsrc, rtcpPacketSdes.getXsrcBlock(1).orElseThrow().getXsrcId());
		assertEquals(1, rtcpPacketSdes.getXsrcBlock(1).orElseThrow().getBlockEntries().size());
		assertEquals(RtcpInnerXsrcBlock.BlockType.CNAME, rtcpPacketSdes.getXsrcBlock(1).orElseThrow().getBlockEntries().getFirst().getType());
		assertEquals(expCnameSdes, rtcpPacketSdes.getXsrcBlock(1).orElseThrow().getBlockEntries().getFirst().getValue());

		// -----------------------------------------------

		SrtcpContextOutbound contextOutbound = new SrtcpContextOutbound(kmd);

		BufferExt newlyEncrBuf = new BufferExt();
		contextOutbound.protectRtcpSrCompound(rtcpCompoundPktBuf, expSenderSsrc, newlyEncrBuf);
		assertEquals(srtcpCompoundPktBuf, newlyEncrBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] buildCompoundRtcpSrPlusBye(RtspProtoIdXsrc senderSsrc) {
		int tmpSsrcInt = senderSsrc.getId32bit().orElse(0L).intValue();
		// RTCP SR (RC=0): 28 bytes total (length=6)
		byte[] sr = new byte[] {
				(byte) 0x80, (byte) 0xC8, 0x00, 0x06,                         // V=2, PT=SR(200), length=6
				(byte) (tmpSsrcInt >>> 24), (byte) (tmpSsrcInt >>> 16),
				(byte) (tmpSsrcInt >>> 8), (byte) tmpSsrcInt,                 // sender SSRC
				0x01, 0x02, 0x03, 0x04,                                       // NTP MSW
				0x05, 0x06, 0x07, 0x08,                                       // NTP LSW
				0x11, 0x22, 0x33, 0x44,                                       // RTP timestamp
				0x00, 0x00, 0x00, 0x64,                                       // sender's packet count
				0x00, 0x00, 0x10, 0x00                                        // sender's octet count
		};

		// RTCP BYE (SC=1): 8 bytes total (length=1)
		byte[] bye = new byte[] {
				(byte) 0x81, (byte) 0xCB, 0x00, 0x01,                         // V=2, PT=BYE(203), length=1
				(byte) (tmpSsrcInt >>> 24), (byte) (tmpSsrcInt >>> 16),
				(byte) (tmpSsrcInt >>> 8), (byte) tmpSsrcInt
		};

		byte[] compound = new byte[sr.length + bye.length];
		System.arraycopy(sr, 0, compound, 0, sr.length);
		System.arraycopy(bye, 0, compound, sr.length, bye.length);
		return compound;
	}

	private static byte[] buildCompoundRtcpSrPlusBye(
				RtspProtoIdXsrc senderSsrc,
				long ntpMsw,
				long ntpLsw,
				long rtpTs,
				long pktCount,
				long octetCount
			) {
		int tmpSsrcInt = senderSsrc.getId32bit().orElse(0L).intValue();

		byte[] sr = new byte[28];
		ByteBuffer srBuf = ByteBuffer.wrap(sr).order(ByteOrder.BIG_ENDIAN);
		srBuf.put((byte) 0x80).put((byte) 0xC8).putShort((short) 0x0006);
		srBuf.putInt(tmpSsrcInt);
		srBuf.putInt((int) ntpMsw);
		srBuf.putInt((int) ntpLsw);
		srBuf.putInt((int) rtpTs);
		srBuf.putInt((int) pktCount);
		srBuf.putInt((int) octetCount);

		byte[] bye = new byte[8];
		ByteBuffer byeBuf = ByteBuffer.wrap(bye).order(ByteOrder.BIG_ENDIAN);
		byeBuf.put((byte) 0x81).put((byte) 0xCB).putShort((short) 0x0001);
		byeBuf.putInt(tmpSsrcInt);

		byte[] compound = new byte[sr.length + bye.length];
		System.arraycopy(sr, 0, compound, 0, sr.length);
		System.arraycopy(bye, 0, compound, sr.length, bye.length);
		return compound;
	}

}
