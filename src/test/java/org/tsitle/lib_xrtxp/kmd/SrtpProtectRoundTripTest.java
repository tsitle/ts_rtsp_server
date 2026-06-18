package org.tsitle.lib_xrtxp.kmd;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.types.DynInteger;
import org.tsitle.lib_xrtxp.kmd.types.SessionKeys;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidMkiException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;

import static org.junit.jupiter.api.Assertions.*;

class SrtpProtectRoundTripTest {

	@Test
	void protectRtp_then_unprotectSrtp_should_restore_original_packet() throws Exception {
		final RtspProtoRtpSeqNr hdSeqNr = RtspProtoRtpSeqNr.of(0x1234);
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x11223344L);

		// Arrange
		final SrtpContextOutbound senderCtx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		final SrtpContextInbound receiverCtx = Common.createSrtpCtxInboundDefault(hdSsrc);
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(senderCtx, 0);
		Common.srtpCtxInjectKeys(senderCtx, rtpKeys);

		final byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		final byte[] originalRtp = Common.buildRtpPacket(hdSeqNr, hdSsrc, payload);

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(originalRtp);

		final BufferExt encryptedBuf = new BufferExt();
		final BufferExt decryptedBuf = new BufferExt();

		// Act: encrypt then decrypt
		senderCtx.protectRtp(plainBuf, false, false, hdSeqNr, hdSsrc, encryptedBuf);
		receiverCtx.unprotectSrtp(encryptedBuf, hdSeqNr, hdSsrc, decryptedBuf);

		// Assert
		assertEquals(plainBuf, decryptedBuf, "Decrypted RTP must match original RTP packet");
		assertNotEquals(plainBuf, encryptedBuf, "Encrypted SRTP packet should differ from original RTP packet");
	}

	@Test
	void protectAndUnprotect_should_handle_roc_wrap_from_seq_ffff_to_0000() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x11223344L);

		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);

		final SrtpContextOutbound senderCtx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		final SrtpContextInbound receiverCtx = Common.createSrtpCtxInboundDefault(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(senderCtx, 0);
		Common.srtpCtxInjectKeys(senderCtx, rtpKeys);
		Common.srtpCtxInjectKeys(receiverCtx, rtpKeys);

		// Packet 1: sequence at wrap boundary (0xFFFF)
		final RtspProtoRtpSeqNr hdSeqNr1 = RtspProtoRtpSeqNr.of(0xFFFF);
		final byte[] payload1 = Common.HEX.parseHex("0102030405060708090A0B0C0D0E0F10");
		final byte[] pkt1 = Common.buildRtpPacket(hdSeqNr1, hdSsrc, payload1);

		final BufferExt in1 = new BufferExt();
		in1.copyOf(pkt1);
		final BufferExt enc1 = new BufferExt();
		final BufferExt dec1 = new BufferExt();

		senderCtx.protectRtp(in1, false, false, hdSeqNr1, hdSsrc, enc1);
		receiverCtx.unprotectSrtp(enc1, hdSeqNr1, hdSsrc, dec1);

		final byte[] decPkt1 = new byte[dec1.getUsed()];
		dec1.copyInto(0, decPkt1, 0, decPkt1.length);
		assertArrayEquals(pkt1, decPkt1, "Packet with SEQ=0xFFFF should decrypt correctly");

		assertEquals(1, Common.srtpCtxOutboundReadStateRtpRocOutbound(senderCtx), "Sender ROC should increment after SEQ wrap");
		assertEquals(1, Common.srtpCtxInboundReadStateSrtpRocInbound(receiverCtx), "Receiver ROC should increment after SEQ wrap");

		// Packet 2: post-wrap sequence (0x0000), must use ROC=1
		final RtspProtoRtpSeqNr hdSeqNr2 = RtspProtoRtpSeqNr.of(0x0000);
		final byte[] payload2 = Common.HEX.parseHex("A1A2A3A4A5A6A7A8A9AAABACADAEAFB0");
		final byte[] pkt2 = Common.buildRtpPacket(hdSeqNr2, hdSsrc, payload2);

		final BufferExt in2 = new BufferExt();
		in2.copyOf(pkt2);
		final BufferExt enc2 = new BufferExt();
		final BufferExt dec2 = new BufferExt();

		senderCtx.protectRtp(in2, false, false, hdSeqNr2, hdSsrc, enc2);
		receiverCtx.unprotectSrtp(enc2, hdSeqNr2, hdSsrc, dec2);

		final byte[] decPkt2 = new byte[dec2.getUsed()];
		dec2.copyInto(0, decPkt2, 0, decPkt2.length);
		assertArrayEquals(pkt2, decPkt2, "Packet with SEQ=0x0000 after wrap should decrypt correctly");
	}

	@Test
	void unprotectSrtp_should_reject_replay_after_roc_wrap() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0xABCD1234L);

		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);

		final SrtpContextOutbound senderCtx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		final SrtpContextInbound receiverCtx = Common.createSrtpCtxInboundDefault(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(senderCtx, 0);
		Common.srtpCtxInjectKeys(senderCtx, rtpKeys);
		Common.srtpCtxInjectKeys(receiverCtx, rtpKeys);

		// First packet at wrap boundary
		final RtspProtoRtpSeqNr hdSeqNrWrap = RtspProtoRtpSeqNr.of(0xFFFF);
		final byte[] payload1 = Common.HEX.parseHex("1112131415161718191A1B1C1D1E1F20");
		final byte[] pktWrap = Common.buildRtpPacket(hdSeqNrWrap, hdSsrc, payload1);

		final BufferExt inWrap = new BufferExt();
		inWrap.copyOf(pktWrap);
		final BufferExt encWrap = new BufferExt();
		final BufferExt decWrap = new BufferExt();

		senderCtx.protectRtp(inWrap, false, false, hdSeqNrWrap, hdSsrc, encWrap);
		receiverCtx.unprotectSrtp(encWrap, hdSeqNrWrap, hdSsrc, decWrap);

		final byte[] decWrapBytes = new byte[decWrap.getUsed()];
		decWrap.copyInto(0, decWrapBytes, 0, decWrapBytes.length);
		assertArrayEquals(pktWrap, decWrapBytes, "Wrap-boundary packet should decrypt correctly");

		// Second packet after wrap
		final RtspProtoRtpSeqNr hdSeqNrAfterWrap = RtspProtoRtpSeqNr.of(0x0000);
		final byte[] payload2 = Common.HEX.parseHex("2122232425262728292A2B2C2D2E2F30");
		final byte[] pktAfterWrap = Common.buildRtpPacket(hdSeqNrAfterWrap, hdSsrc, payload2);

		final BufferExt inAfterWrap = new BufferExt();
		inAfterWrap.copyOf(pktAfterWrap);
		final BufferExt encAfterWrap = new BufferExt();
		final BufferExt decAfterWrap = new BufferExt();

		senderCtx.protectRtp(inAfterWrap, false, false, hdSeqNrAfterWrap, hdSsrc, encAfterWrap);
		receiverCtx.unprotectSrtp(encAfterWrap, hdSeqNrAfterWrap, hdSsrc, decAfterWrap);

		final byte[] decAfterWrapBytes = new byte[decAfterWrap.getUsed()];
		decAfterWrap.copyInto(0, decAfterWrapBytes, 0, decAfterWrapBytes.length);
		assertArrayEquals(pktAfterWrap, decAfterWrapBytes, "Post-wrap packet should decrypt correctly");

		// Replay the already-accepted wrap packet -> must fail (Auth Tag mismatch because of ROC wrap)
		final BufferExt replayOut = new BufferExt();
		final SrtxpInvalidAuthTagException ex = assertThrows(
				SrtxpInvalidAuthTagException.class,
				() -> receiverCtx.unprotectSrtp(encWrap, hdSeqNrWrap, hdSsrc, replayOut),
				"Replayed packet must be rejected due to packet index"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid Auth Tag in SRTP packet"),
				"Replay rejection should be caused by SRTP packet index"
			);
	}

	@Test
	void unprotectSrtp_should_reject_replay_for_old_seqnr() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x11223344L);

		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);

		final SrtpContextOutbound senderCtx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		final SrtpContextInbound receiverCtx = Common.createSrtpCtxInboundDefault(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(senderCtx, 0);
		Common.srtpCtxInjectKeys(senderCtx, rtpKeys);
		Common.srtpCtxInjectKeys(receiverCtx, rtpKeys);

		// First packet far from wrap-boundary
		final RtspProtoRtpSeqNr hdSeqNrDoesntWrap = RtspProtoRtpSeqNr.of(0x0FFF);
		final byte[] payload1 = Common.HEX.parseHex("1112131415161718191A1B1C1D1E1F20");
		final byte[] pktDoesntWrap = Common.buildRtpPacket(hdSeqNrDoesntWrap, hdSsrc, payload1);

		final BufferExt inWrap = new BufferExt();
		inWrap.copyOf(pktDoesntWrap);
		final BufferExt encWrap = new BufferExt();
		final BufferExt decWrap = new BufferExt();

		senderCtx.protectRtp(inWrap, false, false, hdSeqNrDoesntWrap, hdSsrc, encWrap);
		receiverCtx.unprotectSrtp(encWrap, hdSeqNrDoesntWrap, hdSsrc, decWrap);

		final byte[] decWrapBytes = new byte[decWrap.getUsed()];
		decWrap.copyInto(0, decWrapBytes, 0, decWrapBytes.length);
		assertArrayEquals(pktDoesntWrap, decWrapBytes, "Wrap-boundary packet should decrypt correctly");

		// Replay the already-accepted packet -> must fail (index identical than last seen)
		final SrtxpSecurityException ex = assertThrows(
				SrtxpSecurityException.class,
				() -> receiverCtx.unprotectSrtp(encWrap, hdSeqNrDoesntWrap, hdSsrc, decWrap),
				"Replayed packet must be rejected due to packet index check"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid SRTP packet index"),
				"Replay rejection should be caused by SRTP packet index validation"
			);
	}

	@Test
	void protect_then_unprotect_srtp_with_mki_should_restore_original_and_reject_wrong_mki() throws Exception {
		final RtspProtoRtpSeqNr hdSeqNrDoesntWrap = RtspProtoRtpSeqNr.of(0x0FFF);
		final RtspProtoIdXsrc hdSenderSsrc = RtspProtoIdXsrc.of(0x10203040L);

		SessionKeys rtpKeys = Common.createSessionKeysDefaultRtcp(hdSenderSsrc);

		final SrtpContextOutbound senderCtx = Common.createSrtpCtxOutboundDefault(hdSenderSsrc);
		final SrtpContextInbound receiverCtx = Common.createSrtpCtxInboundDefault(hdSenderSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(senderCtx, 0);
		Common.srtpCtxInjectKeys(senderCtx, rtpKeys);
		Common.srtpCtxInjectKeys(receiverCtx, rtpKeys);

		DynInteger mki = DynInteger.of(16909060L, 4);
		senderCtx.setKmdMasterKeyIdentifier(mki);
		receiverCtx.setKmdMasterKeyIdentifier(mki);

		final byte[] payload1 = Common.HEX.parseHex("1112131415161718191A1B1C1D1E1F20");
		final byte[] pktOrg = Common.buildRtpPacket(hdSeqNrDoesntWrap, hdSenderSsrc, payload1);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(pktOrg);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtp(plainBuf, false, false, hdSeqNrDoesntWrap, hdSenderSsrc, encryptedBuf);

		BufferExt decryptedBuf = new BufferExt();
		receiverCtx.unprotectSrtp(encryptedBuf, hdSeqNrDoesntWrap, hdSenderSsrc, decryptedBuf);

		byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);
		assertArrayEquals(pktOrg, decrypted, "SRTP round-trip with MKI must restore original packet");

		byte[] tampered = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, tampered, 0, tampered.length);

		int mkiStart = tampered.length - Common.AUTH_TAG_SIZE_FOR_ALL_TESTS - mki.getSizeBytes();
		tampered[mkiStart] ^= 0x01;

		BufferExt tamperedBuf = new BufferExt();
		tamperedBuf.copyOf(tampered);

		Common.srtpCtxInboundInjectStateSrtpLastIndex(receiverCtx, -1L);  // by-pass replay protection

		BufferExt out = new BufferExt();
		SrtxpInvalidMkiException ex = assertThrows(
				SrtxpInvalidMkiException.class,
				() -> receiverCtx.unprotectSrtp(tamperedBuf, hdSeqNrDoesntWrap, hdSenderSsrc, out),
				"Packet with wrong MKI must be rejected"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid MKI in SRTP packet"),
				"Failure reason should indicate MKI validation"
			);
	}

}
