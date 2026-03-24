package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;

class Common {

	static final HexFormat HEX = HexFormat.of();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// RFC 3711 test vector master material (deterministic, no sensitive runtime secrets)
	static final BufferExt DEFAULT_MASTER_KEY = createBufferFromHex("E1F97A0D3E018BE0D64FA32C06DE4139");
	static final BufferExt DEFAULT_MASTER_SALT = createBufferFromHex("0EC675AD498AFEEBB6960B3AABE6");

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull BufferExt createBufferFromHex(@NonNull String hexStr) {
		byte[] data = HEX.parseHex(hexStr);
		BufferExt buf = new BufferExt();
		buf.copyOf(data);
		return buf;
	}

	static @NonNull BufferExt createBufferFromBa(byte[] data) {
		BufferExt buf = new BufferExt();
		buf.copyOf(data);
		return buf;
	}

	static byte[] createByteArrayFromBuffer(@NonNull BufferExt buf) {
		byte[] data = new byte[buf.getUsed()];
		buf.copyInto(0, data, 0, data.length);
		return data;
	}

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull SessionKeys createSessionKeysDefaultRtp() throws SrtpSecurityException {
		final Cipher cipherAesCtr = SrtxpContext.buildCipherObject();
		return SrtpKeyDerivation.deriveForRtp(
				cipherAesCtr,
				Common.DEFAULT_MASTER_KEY,
				Common.DEFAULT_MASTER_SALT,
				KeySizes.AUTH_KEY_SIZE_160
			);
	}

	static @NonNull SessionKeys createSessionKeysDefaultRtcp() throws SrtpSecurityException {
		final Cipher cipherAesCtr = SrtxpContext.buildCipherObject();
		return SrtpKeyDerivation.deriveForRtcp(
				cipherAesCtr,
				Common.DEFAULT_MASTER_KEY,
				Common.DEFAULT_MASTER_SALT,
				KeySizes.AUTH_KEY_SIZE_160
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull SessionKeys createSessionKeysNonDefRtp(@NonNull BufferExt mk, @NonNull BufferExt ms) throws SrtpSecurityException {
		final Cipher cipherAesCtr = SrtxpContext.buildCipherObject();
		return SrtpKeyDerivation.deriveForRtp(
				cipherAesCtr,
				mk,
				ms,
				KeySizes.AUTH_KEY_SIZE_160
			);
	}

	static @NonNull SessionKeys createSessionKeysNonDefRtcp(@NonNull BufferExt mk, @NonNull BufferExt ms) throws SrtpSecurityException {
		final Cipher cipherAesCtr = SrtxpContext.buildCipherObject();
		return SrtpKeyDerivation.deriveForRtcp(
				cipherAesCtr,
				mk,
				ms,
				KeySizes.AUTH_KEY_SIZE_160
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	static void setPrivateBoolean(Object target, String fieldName, boolean value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setBoolean(target, value);
	}

	@SuppressWarnings("SameParameterValue")
	static void setPrivateLong(Object target, String fieldName, long value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setLong(target, value);
	}

	static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setInt(target, value);
	}

	@SuppressWarnings("SameParameterValue")
	static void setPrivateBufferExt(Object target, String fieldName, BufferExt value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(target, value.clone());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	static int getPrivateInt(Object target, String fieldName) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.getInt(target);
	}

	static long getPrivateLong(Object target, String fieldName) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.getLong(target);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static void srtpCtxInjectRtpKeys(SrtxpContext ctx, SessionKeys sessionKeys, long roc) throws Exception {
		Common.setPrivateBoolean(ctx, "haveMikey", true);
		Common.setPrivateLong(ctx, "ctxStateRtpRocOutbound", roc);
		Common.setPrivateInt(ctx, "ctxAuthKeyLength", KeySizes.AUTH_KEY_SIZE_160);
		ctx.setRtpSessionKeys(sessionKeys);
	}

	static void srtpCtxInjectRtcpKeys(SrtxpContext ctx, SessionKeys sessionKeys, int idx) throws Exception {
		Common.setPrivateBoolean(ctx, "haveMikey", true);
		Common.setPrivateInt(ctx, "ctxStateRtcpIndex", idx);
		Common.setPrivateInt(ctx, "ctxAuthKeyLength", KeySizes.AUTH_KEY_SIZE_160);
		ctx.setRtcpSessionKeys(sessionKeys);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	static byte[] buildExpectedSrtpPacket(
				byte[] plainRtpPacket,
				@NonNull SessionKeys rtpKeys,
				short seqNr,
				int ssrc,
				long stateRoc
			) throws Exception {
		int headerLen = 12;
		long packetIndex = (stateRoc << 16) | ((long)seqNr & 0xFFFFL);

		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];
		ByteBuffer ivByBuf = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		ivByBuf.putInt(0);
		ivByBuf.putInt(ssrc);
		ivByBuf.put((byte)((packetIndex >>> 40) & 0xFF));
		ivByBuf.put((byte)((packetIndex >>> 32) & 0xFF));
		ivByBuf.put((byte)((packetIndex >>> 24) & 0xFF));
		ivByBuf.put((byte)((packetIndex >>> 16) & 0xFF));
		ivByBuf.put((byte)((packetIndex >>> 8) & 0xFF));
		ivByBuf.put((byte)(packetIndex & 0xFF));
		ivByBuf.putShort((short)0);
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			iv[i] ^= rtpKeys.salt().get(i);
		}

		byte[] encrypted = plainRtpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(
				Cipher.ENCRYPT_MODE,
				new SecretKeySpec(rtpKeys.encKey().getBufPtr(), 0, rtpKeys.encKey().getUsed(), "AES"),
				new IvParameterSpec(iv)
			);
		cipher.doFinal(plainRtpPacket, headerLen, plainRtpPacket.length - headerLen, encrypted, headerLen);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(
				new SecretKeySpec(rtpKeys.authKey().getBufPtr(), 0, rtpKeys.authKey().getUsed(), "HmacSHA1")
			);
		mac.update(encrypted);
		byte[] rocBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt((int)(packetIndex >>> 16))
				.array();
		mac.update(rocBytes);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), KeySizes.AUTH_TAG_SIZE);

		return Common.concat(encrypted, tag10);
	}

	@SuppressWarnings("SameParameterValue")
	static byte[] buildExpectedSrtcpPacket(
				byte[] plainRtcpPacket,
				SessionKeys rtcpSessionKeys,
				int ssrc,
				int stateIndexOnly,
				int rtcpSrRrExtendedHeaderLen
			) throws Exception {
		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];
		ByteBuffer ivBuf = ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN);
		ivBuf.putInt(0);
		ivBuf.putInt(ssrc);
		ivBuf.putShort((short)0);
		ivBuf.putInt(stateIndexOnly);
		ivBuf.putShort((short)0);
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			iv[i] ^= rtcpSessionKeys.salt().get(i);
		}

		byte[] encrypted = plainRtcpPacket.clone();
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(
				Cipher.ENCRYPT_MODE,
				new SecretKeySpec(rtcpSessionKeys.encKey().getBufPtr(), 0, rtcpSessionKeys.encKey().getUsed(), "AES"),
				new IvParameterSpec(iv)
			);
		cipher.doFinal(
				plainRtcpPacket,
				rtcpSrRrExtendedHeaderLen,
				plainRtcpPacket.length - rtcpSrRrExtendedHeaderLen,
				encrypted,
				rtcpSrRrExtendedHeaderLen
			);

		int srtcpIndexField = 0x80000000 | (stateIndexOnly & 0x7FFFFFFF);
		byte[] indexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(srtcpIndexField).array();

		byte[] authInput = Common.concat(encrypted, indexBytes);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(
				new SecretKeySpec(rtcpSessionKeys.authKey().getBufPtr(), 0, rtcpSessionKeys.authKey().getUsed(), "HmacSHA1")
			);
		mac.update(authInput);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), 10);

		return Common.concat(authInput, tag10);
	}

}
