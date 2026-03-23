package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SrtpProtectRoundTripTest {

	@Test
	void protectRtp_then_unprotectSrtp_should_restore_original_packet() throws Exception {
		// Arrange
		final SrtxpContext ctx = new SrtxpContext();
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();
		Common.srtpCtxInjectRtpKeys(ctx, rtpKeys, 0L);

		final int seqNr = 0x1234;
		final int ssrc = 0x11223344;

		final byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,                    // V=2, P=0, X=0, CC=0; M=0, PT=96
				(byte) (seqNr >>> 8), (byte) seqNr,          // sequence number
				0x01, 0x02, 0x03, 0x04,                      // timestamp
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc             // SSRC
			};
		final byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		final byte[] originalRtp = Common.concat(rtpHeader, payload);

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(originalRtp);

		final BufferExt encryptedBuf = new BufferExt();
		final BufferExt decryptedBuf = new BufferExt();

		// Act: encrypt then decrypt
		ctx.protectRtp(plainBuf, false, false, seqNr, ssrc, encryptedBuf);
		ctx.unprotectSrtp(encryptedBuf, seqNr, ssrc, decryptedBuf);

		final byte[] encrypted = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, encrypted, 0, encrypted.length);

		final byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);

		// Assert
		assertArrayEquals(originalRtp, decrypted, "Decrypted RTP must match original RTP packet");
		assertFalse(Arrays.equals(originalRtp, encrypted), "Encrypted SRTP packet should differ from original RTP packet");
	}

	@Test
	void protectAndUnprotect_should_handle_roc_wrap_from_seq_ffff_to_0000() throws Exception {
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();

		final SrtxpContext senderCtx = new SrtxpContext();
		final SrtxpContext receiverCtx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(senderCtx, rtpKeys, 0L);
		Common.srtpCtxInjectRtpKeys(receiverCtx, rtpKeys, 0L);

		final int ssrc = 0x11223344;

		// Packet 1: sequence at wrap boundary (0xFFFF)
		final int seq1 = 0xFFFF;
		final byte[] pkt1 = buildRtpPacket(seq1, ssrc, Common.HEX.parseHex("0102030405060708090A0B0C0D0E0F10"));

		final BufferExt in1 = new BufferExt();
		in1.copyOf(pkt1);
		final BufferExt enc1 = new BufferExt();
		final BufferExt dec1 = new BufferExt();

		senderCtx.protectRtp(in1, false, false, seq1, ssrc, enc1);
		receiverCtx.unprotectSrtp(enc1, seq1, ssrc, dec1);

		final byte[] decPkt1 = new byte[dec1.getUsed()];
		dec1.copyInto(0, decPkt1, 0, decPkt1.length);
		assertArrayEquals(pkt1, decPkt1, "Packet with SEQ=0xFFFF should decrypt correctly");

		assertEquals(1L, Common.getPrivateLong(senderCtx, "ctxStateRtpRocOutbound"), "Sender ROC should increment after SEQ wrap");
		assertEquals(1L, Common.getPrivateLong(receiverCtx, "ctxStateRtpRocInbound"), "Receiver ROC should increment after SEQ wrap");

		// Packet 2: post-wrap sequence (0x0000), must use ROC=1
		final int seq2 = 0x0000;
		final byte[] pkt2 = buildRtpPacket(seq2, ssrc, Common.HEX.parseHex("A1A2A3A4A5A6A7A8A9AAABACADAEAFB0"));

		final BufferExt in2 = new BufferExt();
		in2.copyOf(pkt2);
		final BufferExt enc2 = new BufferExt();
		final BufferExt dec2 = new BufferExt();

		senderCtx.protectRtp(in2, false, false, seq2, ssrc, enc2);
		receiverCtx.unprotectSrtp(enc2, seq2, ssrc, dec2);

		final byte[] decPkt2 = new byte[dec2.getUsed()];
		dec2.copyInto(0, decPkt2, 0, decPkt2.length);
		assertArrayEquals(pkt2, decPkt2, "Packet with SEQ=0x0000 after wrap should decrypt correctly");
	}

	@Test
	void unprotectSrtp_should_reject_replay_after_roc_wrap() throws Exception {
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();

		final SrtxpContext senderCtx = new SrtxpContext();
		final SrtxpContext receiverCtx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(senderCtx, rtpKeys, 0L);
		Common.srtpCtxInjectRtpKeys(receiverCtx, rtpKeys, 0L);

		final int ssrc = 0x11223344;

		// First packet at wrap boundary
		final int seqWrap = 0xFFFF;
		final byte[] pktWrap = buildRtpPacket(seqWrap, ssrc, Common.HEX.parseHex("1112131415161718191A1B1C1D1E1F20"));

		final BufferExt inWrap = new BufferExt();
		inWrap.copyOf(pktWrap);
		final BufferExt encWrap = new BufferExt();
		final BufferExt decWrap = new BufferExt();

		senderCtx.protectRtp(inWrap, false, false, seqWrap, ssrc, encWrap);
		receiverCtx.unprotectSrtp(encWrap, seqWrap, ssrc, decWrap);

		final byte[] decWrapBytes = new byte[decWrap.getUsed()];
		decWrap.copyInto(0, decWrapBytes, 0, decWrapBytes.length);
		assertArrayEquals(pktWrap, decWrapBytes, "Wrap-boundary packet should decrypt correctly");

		// Second packet after wrap
		final int seqAfterWrap = 0x0000;
		final byte[] pktAfterWrap = buildRtpPacket(seqAfterWrap, ssrc, Common.HEX.parseHex("2122232425262728292A2B2C2D2E2F30"));

		final BufferExt inAfterWrap = new BufferExt();
		inAfterWrap.copyOf(pktAfterWrap);
		final BufferExt encAfterWrap = new BufferExt();
		final BufferExt decAfterWrap = new BufferExt();

		senderCtx.protectRtp(inAfterWrap, false, false, seqAfterWrap, ssrc, encAfterWrap);
		receiverCtx.unprotectSrtp(encAfterWrap, seqAfterWrap, ssrc, decAfterWrap);

		final byte[] decAfterWrapBytes = new byte[decAfterWrap.getUsed()];
		decAfterWrap.copyInto(0, decAfterWrapBytes, 0, decAfterWrapBytes.length);
		assertArrayEquals(pktAfterWrap, decAfterWrapBytes, "Post-wrap packet should decrypt correctly");

		// Replay the already-accepted wrap packet -> must fail (Auth Tag mismatch because of ROC wrap)
		final BufferExt replayOut = new BufferExt();
		final SrtpSecurityException ex = assertThrows(
				SrtpSecurityException.class,
				() -> receiverCtx.unprotectSrtp(encWrap, seqWrap, ssrc, replayOut),
				"Replayed packet must be rejected due to packet index"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid Auth Tag in SRTP packet"),
				"Replay rejection should be caused by SRTP packet index"
			);
	}

	@Test
	void unprotectSrtp_should_reject_replay_for_old_seqnr() throws Exception {
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();

		final SrtxpContext senderCtx = new SrtxpContext();
		final SrtxpContext receiverCtx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(senderCtx, rtpKeys, 0L);
		Common.srtpCtxInjectRtpKeys(receiverCtx, rtpKeys, 0L);

		final int ssrc = 0x11223344;

		// First packet far from wrap-boundary
		final int seqNoWrap = 0x0FFF;
		final byte[] pktWrap = buildRtpPacket(seqNoWrap, ssrc, Common.HEX.parseHex("1112131415161718191A1B1C1D1E1F20"));

		final BufferExt inWrap = new BufferExt();
		inWrap.copyOf(pktWrap);
		final BufferExt encWrap = new BufferExt();
		final BufferExt decWrap = new BufferExt();

		senderCtx.protectRtp(inWrap, false, false, seqNoWrap, ssrc, encWrap);
		receiverCtx.unprotectSrtp(encWrap, seqNoWrap, ssrc, decWrap);

		final byte[] decWrapBytes = new byte[decWrap.getUsed()];
		decWrap.copyInto(0, decWrapBytes, 0, decWrapBytes.length);
		assertArrayEquals(pktWrap, decWrapBytes, "Wrap-boundary packet should decrypt correctly");

		// Replay the already-accepted packet -> must fail (index identical than last seen)
		final SrtpSecurityException ex = assertThrows(
				SrtpSecurityException.class,
				() -> receiverCtx.unprotectSrtp(encWrap, seqNoWrap, ssrc, decWrap),
				"Replayed packet must be rejected due to packet index check"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid SRTP packet index"),
				"Replay rejection should be caused by SRTP packet index validation"
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildRtpPacket(int seqNr, int ssrc, byte[] payload) {
		final byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x01, 0x02, 0x03, 0x04,
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc
			};
		return Common.concat(rtpHeader, payload);
	}

}
