package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SrtcpProtectRoundTripTest {

	@Test
	void protect_then_unprotect_srtcp_compound_sr_should_restore_original_packet() throws Exception {
		final int hdSsrc = 0x11223344;

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
			int mkiLen = rnd.nextInt(100);
			subfnc_rtcp_randomized_sub1(rnd, mkeyLen, authKeyLen, authTagLen, mkiLen);
		}
	}

	void subfnc_rtcp_randomized_sub1(
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

		final int hdSsrc = rnd.nextInt();

		SrtxpKmd rtcpKmd = new SrtxpKmd(
				mkeyLen,
				new BufferExt(masterKeyBa),
				new BufferExt(masterSaltBa),
				authKeyLen,
				authTagLen,
				new BufferExt(mkiBa),
				hdSsrc,
				0L
			);
		SrtcpContextOutbound senderCtx = new SrtcpContextOutbound(rtcpKmd);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		SrtcpContextInbound receiverCtx = new SrtcpContextInbound(rtcpKmd);

		for (int i = 0; i < rounds; i++) {
			subfnc_rtcp_randomized_sub2(rnd, senderCtx, receiverCtx, i, hdSsrc);
		}
	}

	void subfnc_rtcp_randomized_sub2(
				final SecureRandom rnd,
				final SrtcpContextOutbound senderCtx,
				final SrtcpContextInbound receiverCtx,
				final int roundNr,
				final int hdSsrc
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
		final int hdSsrc = 0x55667788;

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
		SrtxpSecurityException ex = assertThrows(
				SrtxpSecurityException.class,
				() -> receiverCtx.unprotectSrtcpCompound(tamperedBuf, out)
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid Auth Tag"),
				"Tampered packet must fail auth-tag validation"
			);
	}

	@Test
	void protect_then_unprotect_srtcp_compound_sr_with_mki_should_restore_original_and_reject_wrong_mki() throws Exception {
		final int hdSsrc = 0x10203040;

		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		SrtcpContextOutbound senderCtx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		SrtcpContextInbound receiverCtx = Common.createSrtcpCtxInboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(senderCtx, 0);
		Common.srtcpCtxInjectKeys(senderCtx, rtcpKeys);
		Common.srtcpCtxInjectKeys(receiverCtx, rtcpKeys);

		BufferExt mki = BufferExt.decodeHexString("0x01020304");
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

		byte[] tampered = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, tampered, 0, tampered.length);

		int mkiStart = tampered.length - Common.AUTH_TAG_SIZE_FOR_ALL_TESTS - mki.getUsed();
		tampered[mkiStart] ^= 0x01;

		BufferExt tamperedBuf = new BufferExt();
		tamperedBuf.copyOf(tampered);

		BufferExt out = new BufferExt();
		SrtxpSecurityException ex = assertThrows(
				SrtxpSecurityException.class,
				() -> receiverCtx.unprotectSrtcpCompound(tamperedBuf, out),
				"Packet with wrong MKI must be rejected"
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid MKI in SRTCP packet"),
				"Failure reason should indicate MKI validation"
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] buildCompoundRtcpSrPlusBye(int senderSsrc) {
		// RTCP SR (RC=0): 28 bytes total (length=6)
		byte[] sr = new byte[] {
				(byte) 0x80, (byte) 0xC8, 0x00, 0x06,                         // V=2, PT=SR(200), length=6
				(byte) (senderSsrc >>> 24), (byte) (senderSsrc >>> 16),
				(byte) (senderSsrc >>> 8), (byte) senderSsrc,                 // sender SSRC
				0x01, 0x02, 0x03, 0x04,                                       // NTP MSW
				0x05, 0x06, 0x07, 0x08,                                       // NTP LSW
				0x11, 0x22, 0x33, 0x44,                                       // RTP timestamp
				0x00, 0x00, 0x00, 0x64,                                       // sender's packet count
				0x00, 0x00, 0x10, 0x00                                        // sender's octet count
		};

		// RTCP BYE (SC=1): 8 bytes total (length=1)
		byte[] bye = new byte[] {
				(byte) 0x81, (byte) 0xCB, 0x00, 0x01,                         // V=2, PT=BYE(203), length=1
				(byte) (senderSsrc >>> 24), (byte) (senderSsrc >>> 16),
				(byte) (senderSsrc >>> 8), (byte) senderSsrc
		};

		byte[] compound = new byte[sr.length + bye.length];
		System.arraycopy(sr, 0, compound, 0, sr.length);
		System.arraycopy(bye, 0, compound, sr.length, bye.length);
		return compound;
	}

	private static byte[] buildCompoundRtcpSrPlusBye(
				int senderSsrc,
				long ntpMsw,
				long ntpLsw,
				long rtpTs,
				long pktCount,
				long octetCount
			) {
		byte[] sr = new byte[28];
		ByteBuffer srBuf = ByteBuffer.wrap(sr).order(ByteOrder.BIG_ENDIAN);
		srBuf.put((byte) 0x80).put((byte) 0xC8).putShort((short) 0x0006);
		srBuf.putInt(senderSsrc);
		srBuf.putInt((int) ntpMsw);
		srBuf.putInt((int) ntpLsw);
		srBuf.putInt((int) rtpTs);
		srBuf.putInt((int) pktCount);
		srBuf.putInt((int) octetCount);

		byte[] bye = new byte[8];
		ByteBuffer byeBuf = ByteBuffer.wrap(bye).order(ByteOrder.BIG_ENDIAN);
		byeBuf.put((byte) 0x81).put((byte) 0xCB).putShort((short) 0x0001);
		byeBuf.putInt(senderSsrc);

		byte[] compound = new byte[sr.length + bye.length];
		System.arraycopy(sr, 0, compound, 0, sr.length);
		System.arraycopy(bye, 0, compound, sr.length, bye.length);
		return compound;
	}

}
