package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketSR;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

public class SrtpContextTest {

	private static final HexFormat HEX = HexFormat.of();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void srtpContext_protectRtp_should_encrypt_payload_and_append_valid_auth_tag() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");
		SrtpKeyDerivation.SessionKeys rtpKeys = SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext ctx = new SrtpContext();
		injectRtpKeys(ctx, rtpKeys.encKey(), rtpKeys.authKey(), rtpKeys.salt());

		int seqNr = 0x1234;
		int ssrc = 0xDEC0ADDE;

		byte[] header = new byte[] {
				(byte) 0x80, (byte) 0x60, // V=2,P=0,X=0,CC=0 ; M=0, PT=96
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x11, 0x22, 0x33, 0x44,   // RTP timestamp
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16), (byte) (ssrc >>> 8), (byte) ssrc
			};
		byte[] payload = HEX.parseHex("00112233445566778899AABBCCDDEEFF00010203");
		byte[] plainPacket = concat(header, payload);

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainPacket);
		BufferExt outBuf = new BufferExt();

		ctx.protectRtp(plainBuf, false, false, seqNr, ssrc, outBuf);

		byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		byte[] expected = buildExpectedSrtpPacket(
				plainPacket,
				rtpKeys.encKey(),
				rtpKeys.authKey(),
				rtpKeys.salt(),
				seqNr,
				ssrc,
				0L
			);

		assertArrayEquals(expected, actual, "SRTP packet mismatch");
		assertFalse(Arrays.equals(
				Arrays.copyOfRange(plainPacket, 12, plainPacket.length),
				Arrays.copyOfRange(actual, 12, 12 + payload.length)
			), "Encrypted payload should differ from plaintext payload");
	}

	@Test
	void srtpContext_protectRtcpSrCompound_should_encrypt_payload_and_append_index_and_auth_tag() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");
		SrtpKeyDerivation.SessionKeys rtcpKeys = SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext ctx = new SrtpContext();
		injectRtcpKeys(ctx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);

		int ssrc = 0xDEC0ADDE;
		int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		byte[] plainRtcp = new byte[hdrLen + 12];
		for (int i = 0; i < plainRtcp.length; i++) {
			plainRtcp[i] = (byte) (0xA0 + i);
		}

		BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainRtcp);
		BufferExt outBuf = new BufferExt();

		int beforeIndex = getPrivateInt(ctx, "ctxRtcpIndex");
		ctx.protectRtcpSrCompound(plainBuf, ssrc, outBuf);
		int afterIndex = getPrivateInt(ctx, "ctxRtcpIndex");

		byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		byte[] expected = buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys.encKey(),
				rtcpKeys.authKey(),
				rtcpKeys.salt(),
				ssrc,
				0,
				hdrLen
		);

		assertArrayEquals(expected, actual, "SRTCP packet mismatch");
		assertEquals((beforeIndex + 1) & 0x7FFFFFFF, afterIndex, "SRTCP index should increment modulo 31 bits");

		assertFalse(Arrays.equals(
				Arrays.copyOfRange(plainRtcp, hdrLen, plainRtcp.length),
				Arrays.copyOfRange(actual, hdrLen, hdrLen + (plainRtcp.length - hdrLen))
		), "Encrypted RTCP payload should differ from plaintext payload");
	}

	@Test
	void srtpContext_fuzz_randomized_rtp_and_rtcp_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 200;

		for (int i = 0; i < rounds; i++) {
			byte[] masterKey = new byte[16];
			byte[] masterSalt = new byte[14];
			rnd.nextBytes(masterKey);
			rnd.nextBytes(masterSalt);

			SrtpKeyDerivation.SessionKeys rtpKeys = SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);
			SrtpKeyDerivation.SessionKeys rtcpKeys = SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

			// ---------------- RTP ----------------
			SrtpContext rtpCtx = new SrtpContext();
			injectRtpKeys(rtpCtx, rtpKeys.encKey(), rtpKeys.authKey(), rtpKeys.salt());

			int seqNr = rnd.nextInt(0x10000);
			int ssrcRtp = rnd.nextInt();

			int rtpPayloadLen = rnd.nextInt(1, 400);
			byte[] rtpPayload = new byte[rtpPayloadLen];
			rnd.nextBytes(rtpPayload);

			byte[] rtpHeader = new byte[] {
					(byte) 0x80, (byte) 0x60,
					(byte) (seqNr >>> 8), (byte) seqNr,
					(byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256),
					(byte) (ssrcRtp >>> 24), (byte) (ssrcRtp >>> 16), (byte) (ssrcRtp >>> 8), (byte) ssrcRtp
				};
			byte[] plainRtp = concat(rtpHeader, rtpPayload);

			BufferExt rtpIn = new BufferExt();
			rtpIn.copyOf(plainRtp);
			BufferExt rtpOut = new BufferExt();

			rtpCtx.protectRtp(rtpIn, false, false, seqNr, ssrcRtp, rtpOut);

			byte[] rtpActual = new byte[rtpOut.getUsed()];
			rtpOut.copyInto(0, rtpActual, 0, rtpActual.length);

			byte[] rtpExpected = buildExpectedSrtpPacket(
					plainRtp,
					rtpKeys.encKey(),
					rtpKeys.authKey(),
					rtpKeys.salt(),
					seqNr,
					ssrcRtp,
					0L
				);

			assertArrayEquals(rtpExpected, rtpActual, "RTP mismatch at round=" + i);

			// ---------------- RTCP ----------------
			SrtpContext rtcpCtx = new SrtpContext();
			injectRtcpKeys(rtcpCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0);

			int ssrcRtcp = rnd.nextInt();
			int rtcpHdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
			int rtcpPayloadLen = rnd.nextInt(1, 300);

			byte[] plainRtcp = new byte[rtcpHdrLen + rtcpPayloadLen];
			rnd.nextBytes(plainRtcp);

			BufferExt rtcpIn = new BufferExt();
			rtcpIn.copyOf(plainRtcp);
			BufferExt rtcpOut = new BufferExt();

			rtcpCtx.protectRtcpSrCompound(rtcpIn, ssrcRtcp, rtcpOut);

			byte[] rtcpActual = new byte[rtcpOut.getUsed()];
			rtcpOut.copyInto(0, rtcpActual, 0, rtcpActual.length);

			byte[] rtcpExpected = buildExpectedSrtcpPacket(
					plainRtcp,
					rtcpKeys.encKey(),
					rtcpKeys.authKey(),
					rtcpKeys.salt(),
					ssrcRtcp,
					0,
					rtcpHdrLen
				);

			assertArrayEquals(rtcpExpected, rtcpActual, "RTCP mismatch at round=" + i);
			assertEquals(1, getPrivateInt(rtcpCtx, "ctxRtcpIndex"), "RTCP index mismatch at round=" + i);
		}
	}

	@Test
	void srtpContext_fuzz_randomized_with_non_zero_roc_and_srtcp_index_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 150;

		for (int i = 0; i < rounds; i++) {
			byte[] masterKey = new byte[16];
			byte[] masterSalt = new byte[14];
			rnd.nextBytes(masterKey);
			rnd.nextBytes(masterSalt);

			SrtpKeyDerivation.SessionKeys rtpKeys = SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);
			SrtpKeyDerivation.SessionKeys rtcpKeys = SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

			// ---------------- RTP with non-zero ROC ----------------
			SrtpContext rtpCtx = new SrtpContext();
			long roc = rnd.nextInt(1, 100_000); // non-zero, keeps math simple and fast
			injectRtpKeysWithRoc(rtpCtx, rtpKeys.encKey(), rtpKeys.authKey(), rtpKeys.salt(), roc);

			int seqNr = rnd.nextInt(0x10000);
			int ssrcRtp = rnd.nextInt();

			int rtpPayloadLen = rnd.nextInt(1, 500);
			byte[] rtpPayload = new byte[rtpPayloadLen];
			rnd.nextBytes(rtpPayload);

			byte[] rtpHeader = new byte[] {
					(byte) 0x80, (byte) 0x60,
					(byte) (seqNr >>> 8), (byte) seqNr,
					(byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256),
					(byte) (ssrcRtp >>> 24), (byte) (ssrcRtp >>> 16), (byte) (ssrcRtp >>> 8), (byte) ssrcRtp
				};
			byte[] plainRtp = concat(rtpHeader, rtpPayload);

			BufferExt rtpIn = new BufferExt();
			rtpIn.copyOf(plainRtp);
			BufferExt rtpOut = new BufferExt();

			rtpCtx.protectRtp(rtpIn, false, false, seqNr, ssrcRtp, rtpOut);

			byte[] rtpActual = new byte[rtpOut.getUsed()];
			rtpOut.copyInto(0, rtpActual, 0, rtpActual.length);

			byte[] rtpExpected = buildExpectedSrtpPacket(
					plainRtp,
					rtpKeys.encKey(),
					rtpKeys.authKey(),
					rtpKeys.salt(),
					seqNr,
					ssrcRtp,
					roc
				);

			assertArrayEquals(rtpExpected, rtpActual, "RTP mismatch with non-zero ROC at round=" + i);

			// ---------------- RTCP with non-zero start index ----------------
			SrtpContext rtcpCtx = new SrtpContext();
			int startSrtcpIndex = rnd.nextInt(1, 0x7FFFFFFF); // non-zero, 31-bit
			injectRtcpKeys(rtcpCtx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), startSrtcpIndex);

			int ssrcRtcp = rnd.nextInt();
			int rtcpHdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
			int rtcpPayloadLen = rnd.nextInt(1, 300);

			byte[] plainRtcp = new byte[rtcpHdrLen + rtcpPayloadLen];
			rnd.nextBytes(plainRtcp);

			BufferExt rtcpIn = new BufferExt();
			rtcpIn.copyOf(plainRtcp);
			BufferExt rtcpOut = new BufferExt();

			int before = getPrivateInt(rtcpCtx, "ctxRtcpIndex");
			rtcpCtx.protectRtcpSrCompound(rtcpIn, ssrcRtcp, rtcpOut);
			int after = getPrivateInt(rtcpCtx, "ctxRtcpIndex");

			byte[] rtcpActual = new byte[rtcpOut.getUsed()];
			rtcpOut.copyInto(0, rtcpActual, 0, rtcpActual.length);

			byte[] rtcpExpected = buildExpectedSrtcpPacket(
					plainRtcp,
					rtcpKeys.encKey(),
					rtcpKeys.authKey(),
					rtcpKeys.salt(),
					ssrcRtcp,
					startSrtcpIndex,
					rtcpHdrLen
				);

			assertArrayEquals(rtcpExpected, rtcpActual, "RTCP mismatch with non-zero index at round=" + i);
			assertEquals((before + 1) & 0x7FFFFFFF, after, "RTCP index increment mismatch at round=" + i);
		}
	}

	@Test
	void srtpContext_rtcp_index_should_wrap_at_31_bits_boundary() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");
		SrtpKeyDerivation.SessionKeys rtcpKeys = SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		SrtpContext ctx = new SrtpContext();
		injectRtcpKeys(ctx, rtcpKeys.encKey(), rtcpKeys.authKey(), rtcpKeys.salt(), 0x7FFFFFFE);

		int ssrc = 0x55667788;
		int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		byte[] plainRtcp = new byte[hdrLen + 8];
		Arrays.fill(plainRtcp, (byte) 0x5A);

		BufferExt in1 = new BufferExt();
		in1.copyOf(plainRtcp);
		BufferExt out1 = new BufferExt();

		BufferExt in2 = new BufferExt();
		in2.copyOf(plainRtcp);
		BufferExt out2 = new BufferExt();

		// 1st call uses index 0x7FFFFFFE, then increments to 0x7FFFFFFF
		ctx.protectRtcpSrCompound(in1, ssrc, out1);
		assertEquals(0x7FFFFFFF, getPrivateInt(ctx, "ctxRtcpIndex"));

		// 2nd call uses index 0x7FFFFFFF, then wraps to 0
		ctx.protectRtcpSrCompound(in2, ssrc, out2);
		assertEquals(0, getPrivateInt(ctx, "ctxRtcpIndex"));

		byte[] actual1 = new byte[out1.getUsed()];
		out1.copyInto(0, actual1, 0, actual1.length);
		byte[] expected1 = buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys.encKey(),
				rtcpKeys.authKey(),
				rtcpKeys.salt(),
				ssrc,
				0x7FFFFFFE,
				hdrLen
			);
		assertArrayEquals(expected1, actual1, "SRTCP packet mismatch for index 0x7FFFFFFE");

		byte[] actual2 = new byte[out2.getUsed()];
		out2.copyInto(0, actual2, 0, actual2.length);
		byte[] expected2 = buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys.encKey(),
				rtcpKeys.authKey(),
				rtcpKeys.salt(),
				ssrc,
				0x7FFFFFFF,
				hdrLen
			);
		assertArrayEquals(expected2, actual2, "SRTCP packet mismatch for index 0x7FFFFFFF");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void injectRtpKeys(SrtpContext ctx, byte[] encKey, byte[] authKey, byte[] salt) throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateLong(ctx, "ctxRtpRoc", 0L);
	}

	private static void injectRtpKeysWithRoc(
				SrtpContext ctx,
				byte[] encKey,
				byte[] authKey,
				byte[] salt,
				long roc
			) throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateLong(ctx, "ctxRtpRoc", roc);
	}

	@SuppressWarnings("SameParameterValue")
	private static void injectRtcpKeys(SrtpContext ctx, byte[] encKey, byte[] authKey, byte[] salt, int srtcpIndex)
			throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateInt(ctx, "ctxRtcpIndex", srtcpIndex);
	}

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildExpectedSrtpPacket(
				byte[] plainRtpPacket,
				byte[] rtpSessionEncKey,
				byte[] rtpSessionAuthKey,
				byte[] rtpSessionSalt,
				int seqNr,
				int ssrc,
				long roc
			) throws Exception {
		final int headerLen = 12;
		long packetIndex = (roc << 16) | (seqNr & 0xFFFFL);

		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];
		ByteBuffer ivBuf = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		ivBuf.putInt(0);
		ivBuf.putInt(ssrc);
		ivBuf.putLong(packetIndex);
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			iv[i] ^= rtpSessionSalt[i];
		}

		byte[] encrypted = plainRtpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rtpSessionEncKey, "AES"), new IvParameterSpec(iv));
		cipher.doFinal(
				plainRtpPacket,
				headerLen,
				plainRtpPacket.length - headerLen,
				encrypted,
				headerLen
		);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(rtpSessionAuthKey, "HmacSHA1"));
		mac.update(encrypted);

		byte[] rocBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) (packetIndex >>> 16)).array();
		mac.update(rocBytes);

		byte[] tag10 = Arrays.copyOf(mac.doFinal(), 10);

		return concat(encrypted, tag10);
	}

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildExpectedSrtcpPacket(
				byte[] plainRtcpPacket,
				byte[] rtcpSessionEncKey,
				byte[] rtcpSessionAuthKey,
				byte[] rtcpSessionSalt,
				int ssrc,
				int srtcpIndexOnly,
				int rtcpSrRrExtendedHeaderLen
			) throws Exception {
		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];
		ByteBuffer ivBuf = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		ivBuf.putInt(0);
		ivBuf.putInt(ssrc);
		ivBuf.putInt(srtcpIndexOnly);
		ivBuf.putInt(0);
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			iv[i] ^= rtcpSessionSalt[i];
		}

		byte[] encrypted = plainRtcpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rtcpSessionEncKey, "AES"), new IvParameterSpec(iv));
		cipher.doFinal(
				plainRtcpPacket,
				rtcpSrRrExtendedHeaderLen,
				plainRtcpPacket.length - rtcpSrRrExtendedHeaderLen,
				encrypted,
				rtcpSrRrExtendedHeaderLen
			);

		int srtcpIndexField = 0x80000000 | (srtcpIndexOnly & 0x7FFFFFFF);
		byte[] indexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(srtcpIndexField).array();

		byte[] authInput = concat(encrypted, indexBytes);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(rtcpSessionAuthKey, "HmacSHA1"));
		mac.update(authInput);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), 10);

		return concat(authInput, tag10);
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
	private static void setPrivateLong(Object target, String fieldName, long value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setLong(target, value);
	}

	@SuppressWarnings("SameParameterValue")
	private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setInt(target, value);
	}

	@SuppressWarnings("SameParameterValue")
	private static int getPrivateInt(Object target, String fieldName) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.getInt(target);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

}
