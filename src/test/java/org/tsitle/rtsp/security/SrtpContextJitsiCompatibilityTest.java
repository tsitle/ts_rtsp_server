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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SrtpContextJitsiCompatibilityTest {

	private static final HexFormat HEX = HexFormat.of();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void rtp_should_match_when_keys_are_derived_by_jitsi_kdf() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		// Derive keys via Jitsi reflection (labels 0,1,2 for RTP)
		byte[] rtpEnc = jitsiDerive(masterKey, masterSalt, 0, 16);
		byte[] rtpAuth = jitsiDerive(masterKey, masterSalt, 1, 20);
		byte[] rtpSalt = jitsiDerive(masterKey, masterSalt, 2, 14);

		SrtpContext ctx = new SrtpContext();
		injectRtpKeys(ctx, rtpEnc, rtpAuth, rtpSalt);

		int seqNr = 0x2222;
		int ssrc = 0x10203040;

		byte[] header = new byte[]{
				(byte) 0x80, (byte) 0x60,
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x01, 0x02, 0x03, 0x04,
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16), (byte) (ssrc >>> 8), (byte) ssrc
			};
		byte[] payload = HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] plain = concat(header, payload);

		BufferExt in = new BufferExt();
		in.copyOf(plain);
		BufferExt out = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, out);

		byte[] actual = new byte[out.getUsed()];
		out.copyInto(0, actual, 0, actual.length);

		byte[] expected = buildExpectedSrtpPacket(plain, rtpEnc, rtpAuth, rtpSalt, seqNr, ssrc, 0L);
		assertArrayEquals(expected, actual);
	}

	@Test
	void rtcp_should_match_when_keys_are_derived_by_jitsi_kdf() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		// Derive keys via Jitsi reflection (labels 3,4,5 for RTCP)
		byte[] rtcpEnc = jitsiDerive(masterKey, masterSalt, 3, 16);
		byte[] rtcpAuth = jitsiDerive(masterKey, masterSalt, 4, 20);
		byte[] rtcpSalt = jitsiDerive(masterKey, masterSalt, 5, 14);

		SrtpContext ctx = new SrtpContext();
		injectRtcpKeys(ctx, rtcpEnc, rtcpAuth, rtcpSalt, 0);

		int ssrc = 0x10203040;
		int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		byte[] plainRtcp = new byte[hdrLen + 20];
		for (int i = 0; i < plainRtcp.length; i++) {
			plainRtcp[i] = (byte) (0x40 + i);
		}

		BufferExt in = new BufferExt();
		in.copyOf(plainRtcp);
		BufferExt out = new BufferExt();

		int before = getPrivateInt(ctx, "ctxRtcpIndex");
		ctx.protectRtcpSrCompound(in, ssrc, out);
		int after = getPrivateInt(ctx, "ctxRtcpIndex");

		byte[] actual = new byte[out.getUsed()];
		out.copyInto(0, actual, 0, actual.length);

		byte[] expected = buildExpectedSrtcpPacket(plainRtcp, rtcpEnc, rtcpAuth, rtcpSalt, ssrc, 0, hdrLen);

		assertArrayEquals(expected, actual);
		assertEquals((before + 1) & 0x7FFFFFFF, after);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] jitsiDerive(byte[] masterKey, byte[] masterSalt, int label, int outLen) throws Exception {
		Class<?> policyClz = Class.forName("org.jitsi.srtp.SrtpPolicy");
		Object policy = policyClz.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
				.newInstance(
						policyClz.getField("AESCM_ENCRYPTION").getInt(null), 16,
						policyClz.getField("HMACSHA1_AUTHENTICATION").getInt(null), 20,
						10, 14
				);

		Class<?> kdfClz = Class.forName("org.jitsi.srtp.SrtpKdf");
		Constructor<?> kdfCtor = kdfClz.getDeclaredConstructor(byte[].class, byte[].class, policyClz);
		kdfCtor.setAccessible(true);
		@SuppressWarnings("JavaReflectionInvocation") Object kdf = kdfCtor.newInstance(masterKey, masterSalt, policy);

		byte[] out = new byte[outLen];

		Method derive = kdfClz.getDeclaredMethod("deriveSessionKey", byte[].class, byte.class);
		derive.setAccessible(true);
		derive.invoke(kdf, out, (byte) (label & 0xFF));

		return out;
	}

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildExpectedSrtpPacket(
			byte[] plainRtpPacket, byte[] encKey, byte[] authKey, byte[] salt, int seqNr, int ssrc, long roc
	) throws Exception {
		int headerLen = 12;
		long packetIndex = (roc << 16) | (seqNr & 0xFFFFL);

		byte[] iv = new byte[16];
		ByteBuffer bb = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		bb.putInt(0).putInt(ssrc).putLong(packetIndex);
		for (int i = 0; i < 14; i++) iv[i] ^= salt[i];

		byte[] encrypted = plainRtpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
		cipher.doFinal(plainRtpPacket, headerLen, plainRtpPacket.length - headerLen, encrypted, headerLen);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(authKey, "HmacSHA1"));
		mac.update(encrypted);
		mac.update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) (packetIndex >>> 16)).array());
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), 10);

		return concat(encrypted, tag10);
	}

	@SuppressWarnings("SameParameterValue")
	private static byte[] buildExpectedSrtcpPacket(
			byte[] plainRtcpPacket, byte[] encKey, byte[] authKey, byte[] salt, int ssrc, int idx, int hdrLen
	) throws Exception {
		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];
		ByteBuffer bb = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		bb.putInt(0).putInt(ssrc).putInt(idx).putInt(0);
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) iv[i] ^= salt[i];

		byte[] encrypted = plainRtcpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
		cipher.doFinal(plainRtcpPacket, hdrLen, plainRtcpPacket.length - hdrLen, encrypted, hdrLen);

		int srtcpIndexField = 0x80000000 | (idx & 0x7FFFFFFF);
		byte[] indexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(srtcpIndexField).array();
		byte[] authInput = concat(encrypted, indexBytes);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(authKey, "HmacSHA1"));
		mac.update(authInput);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), 10);

		return concat(authInput, tag10);
	}

	private static void injectRtpKeys(SrtpContext ctx, byte[] encKey, byte[] authKey, byte[] salt) throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateLong(ctx, "ctxRtpRoc", 0L);
	}

	@SuppressWarnings("SameParameterValue")
	private static void injectRtcpKeys(SrtpContext ctx, byte[] encKey, byte[] authKey, byte[] salt, int idx) throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtcpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateInt(ctx, "ctxRtcpIndex", idx);
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
