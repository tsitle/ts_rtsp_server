package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class SrtcpSrCompoundRoundTripTest {

	private static final HexFormat HEX = HexFormat.of();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void protect_then_unprotect_srtcp_compound_sr_should_restore_original_packet() throws Exception {
		// RFC 3711 test vector master material (deterministic, no sensitive runtime secrets)
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		SrtpKeyDerivation.SessionKeys rtcpKeys =
				SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext senderCtx = new SrtpContext();
		SrtpContext receiverCtx = new SrtpContext();
		injectRtcpKeys(senderCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);
		injectRtcpKeys(receiverCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);

		int senderSsrc = 0x11223344;
		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(senderSsrc);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, senderSsrc, encryptedBuf);

		byte[] encrypted = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, encrypted, 0, encrypted.length);
		assertFalse(Arrays.equals(compoundRtcp, encrypted), "Encrypted packet should differ from plaintext");

		BufferExt decryptedBuf = new BufferExt();
		boolean wasDecrypted = receiverCtx.unprotectRtcpCompound(encryptedBuf, decryptedBuf);
		assertTrue(wasDecrypted, "Packet should be recognized and decrypted as SRTCP");

		byte[] decrypted = new byte[decryptedBuf.getUsed()];
		decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);

		assertArrayEquals(compoundRtcp, decrypted, "Decrypted compound RTCP packet must match original");
	}

	@Test
	void protect_then_unprotect_randomized_packets_should_restore_original_and_reject_replay() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 64;

		byte[] masterKey = new byte[16];
		byte[] masterSalt = new byte[14];
		rnd.nextBytes(masterKey);
		rnd.nextBytes(masterSalt);

		SrtpKeyDerivation.SessionKeys rtcpKeys =
				SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext senderCtx = new SrtpContext();
		SrtpContext receiverCtx = new SrtpContext();
		injectRtcpKeys(senderCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);
		injectRtcpKeys(receiverCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);

		int senderSsrc = rnd.nextInt();

		for (int i = 0; i < rounds; i++) {
			long ntpMsw = Integer.toUnsignedLong(rnd.nextInt());
			long ntpLsw = Integer.toUnsignedLong(rnd.nextInt());
			long rtpTs = Integer.toUnsignedLong(rnd.nextInt());
			long pktCount = Integer.toUnsignedLong(rnd.nextInt());
			long octetCount = Integer.toUnsignedLong(rnd.nextInt());

			byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(
					senderSsrc, ntpMsw, ntpLsw, rtpTs, pktCount, octetCount
				);

			BufferExt plainBuf = new BufferExt();
			plainBuf.copyOf(compoundRtcp);

			BufferExt encryptedBuf = new BufferExt();
			senderCtx.protectRtcpSrCompound(plainBuf, senderSsrc, encryptedBuf);

			BufferExt decryptedBuf = new BufferExt();
			boolean wasDecrypted = receiverCtx.unprotectRtcpCompound(encryptedBuf, decryptedBuf);
			assertTrue(wasDecrypted, "Round " + i + ": packet should be decrypted");

			byte[] decrypted = new byte[decryptedBuf.getUsed()];
			decryptedBuf.copyInto(0, decrypted, 0, decrypted.length);
			assertArrayEquals(compoundRtcp, decrypted, "Round " + i + ": decrypted packet mismatch");

			BufferExt replayOut = new BufferExt();
			assertThrows(
					SrtpSecurityException.class,
					() -> receiverCtx.unprotectRtcpCompound(encryptedBuf, replayOut),
					"Round " + i + ": replayed packet must be rejected due to index check"
				);
		}
	}

	@Test
	void unprotect_should_fail_when_encrypted_packet_is_tampered() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		SrtpKeyDerivation.SessionKeys rtcpKeys =
				SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext senderCtx = new SrtpContext();
		SrtpContext receiverCtx = new SrtpContext();
		injectRtcpKeys(senderCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);
		injectRtcpKeys(receiverCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);

		int senderSsrc = 0x55667788;
		byte[] compoundRtcp = buildCompoundRtcpSrPlusBye(senderSsrc);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(compoundRtcp);

		BufferExt encryptedBuf = new BufferExt();
		senderCtx.protectRtcpSrCompound(plainBuf, senderSsrc, encryptedBuf);

		byte[] tampered = new byte[encryptedBuf.getUsed()];
		encryptedBuf.copyInto(0, tampered, 0, tampered.length);

		// Flip one bit in encrypted payload area (beyond RTCP header+SR SSRC)
		int tamperPos = 12;
		tampered[tamperPos] ^= 0x01;

		BufferExt tamperedBuf = new BufferExt();
		tamperedBuf.copyOf(tampered);

		BufferExt out = new BufferExt();
		SrtpSecurityException ex = assertThrows(
				SrtpSecurityException.class,
				() -> receiverCtx.unprotectRtcpCompound(tamperedBuf, out)
			);

		assertTrue(
				ex.getMessage() != null && ex.getMessage().contains("Invalid Auth Tag"),
				"Tampered packet must fail auth-tag validation"
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

	@SuppressWarnings("SameParameterValue")
	private static void injectRtcpKeys(
				SrtpContext ctx,
				byte[] encKey,
				byte[] authKey,
				byte[] salt,
				int srtcpIndex
			) throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateInt(ctx, "ctxRtcpIndex", srtcpIndex);
	}

	private static void copyIntoPrivateByteArray(Object target, String fieldName, byte[] value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		byte[] dst = (byte[]) f.get(target);
		System.arraycopy(value, 0, dst, 0, dst.length);
	}

	@SuppressWarnings("SameParameterValue")
	private static void setPrivateBoolean(Object target, String fieldName, boolean value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setBoolean(target, value);
	}

	@SuppressWarnings("SameParameterValue")
	private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setInt(target, value);
	}

}
