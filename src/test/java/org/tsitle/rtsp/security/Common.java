package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.packets.rtp.ParamsContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
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

	static final int ENCR_KEY_SIZE_FOR_ALL_TESTS = SrtxpKmd.DEFAULT_ENCR_KEY_LEN;
	//static final int ENCR_KEY_SIZE_FOR_ALL_TESTS = KeySizes.AES_KEY_SIZE_256;
	static final int AUTH_KEY_SIZE_FOR_ALL_TESTS = SrtxpKmd.DEFAULT_AUTH_KEY_LEN;
	//static final int AUTH_KEY_SIZE_FOR_ALL_TESTS = KeySizes.AUTH_KEY_SIZE_080;
	static final int AUTH_TAG_SIZE_FOR_ALL_TESTS = SrtxpKmd.DEFAULT_AUTH_TAG_LEN;
	//static final int AUTH_TAG_SIZE_FOR_ALL_TESTS = 17;

	// RFC-3711 test vector master material (deterministic, no sensitive runtime secrets)
	static final BufferExt MASTER_KEY_128 = BufferExt.decodeHexString("E1F97A0D3E018BE0D64FA32C06DE4139");
	static final BufferExt MASTER_KEY_256 = BufferExt.decodeHexString("AAF97A0D3E018BE0D64FA32C06DE4139E1F97A0D3E018BE0D64FA32C06DE41FF");
	static final BufferExt DEFAULT_MASTER_KEY = (ENCR_KEY_SIZE_FOR_ALL_TESTS == 16 ? MASTER_KEY_128 : MASTER_KEY_256);
	static final BufferExt DEFAULT_MASTER_SALT = BufferExt.decodeHexString("0EC675AD498AFEEBB6960B3AABE6");

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static byte[] createByteArrayFromBuffer(@NonNull BufferExt buf) {
		byte[] data = new byte[buf.getUsed()];
		buf.copyInto(0, data, 0, data.length);
		return data;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull SrtxpKmd createSrtxpKmdDefault(int ssrcId) {
		// sanity checks
		if (KeySizes.AES_KEY_SIZE_128 != 16) { throw new AssertionError("Invalid AES Key size"); }
		if (KeySizes.AES_KEY_SIZE_256 != 32) { throw new AssertionError("Invalid AES Key size"); }
		if (KeySizes.AUTH_KEY_SIZE_080 != 10) { throw new AssertionError("Invalid Auth Key size"); }
		if (KeySizes.AUTH_KEY_SIZE_160 != 20) { throw new AssertionError("Invalid Auth Key size"); }
		if (KeySizes.SALT_SIZE != 14) { throw new AssertionError("Invalid Salt size"); }
		if (KeySizes.IV_SIZE != 16) { throw new AssertionError("Invalid IV size"); }

		//
		return new SrtxpKmd(
				ENCR_KEY_SIZE_FOR_ALL_TESTS,
				DEFAULT_MASTER_KEY,
				DEFAULT_MASTER_SALT,
				AUTH_KEY_SIZE_FOR_ALL_TESTS,
				AUTH_TAG_SIZE_FOR_ALL_TESTS,
				new BufferExt(),
				ssrcId,
				0L
			);
	}

	static @NonNull SrtpContextInbound createSrtpCtxInboundDefault(int ssrcId) throws SrtxpSecurityException {
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return new SrtpContextInbound(kmd);
	}

	static @NonNull SrtpContextOutbound createSrtpCtxOutboundDefault(int ssrcId) throws SrtxpSecurityException {
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return new SrtpContextOutbound(kmd);
	}

	static @NonNull SrtcpContextInbound createSrtcpCtxInboundDefault(int ssrcId) throws SrtxpSecurityException {
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return new SrtcpContextInbound(kmd);
	}

	static @NonNull SrtcpContextOutbound createSrtcpCtxOutboundDefault(int ssrcId) throws SrtxpSecurityException {
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return new SrtcpContextOutbound(kmd);
	}

	static @NonNull SessionKeys createSessionKeysDefaultRtp(int ssrcId) throws SrtxpSecurityException {
		final Cipher cipherAesCtr = SrtxpContextBase.buildCipherObject();
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return SrtxpKeyDerivation.deriveForRtp(cipherAesCtr, kmd, 0L);
	}

	static @NonNull SessionKeys createSessionKeysDefaultRtcp(int ssrcId) throws SrtxpSecurityException {
		final Cipher cipherAesCtr = SrtxpContextBase.buildCipherObject();
		SrtxpKmd kmd = createSrtxpKmdDefault(ssrcId);
		return SrtxpKeyDerivation.deriveForRtcp(cipherAesCtr, kmd, 0L);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	static @NonNull SessionKeys createSessionKeysNonDefRtp(
				@NonNull BufferExt mk,
				@NonNull BufferExt ms,
				int authKeyLen,
				int authTagLen,
				int ssrcId
			) throws SrtxpSecurityException {
		final Cipher cipherAesCtr = SrtxpContextBase.buildCipherObject();
		SrtxpKmd kmd = new SrtxpKmd(
				mk.getUsed(),
				mk,
				ms,
				authKeyLen,
				authTagLen,
				new BufferExt(),
				ssrcId,
				0L
			);
		return SrtxpKeyDerivation.deriveForRtp(cipherAesCtr, kmd, 0L);
	}

	@SuppressWarnings("SameParameterValue")
	static @NonNull SessionKeys createSessionKeysNonDefRtcp(
				@NonNull BufferExt mk,
				@NonNull BufferExt ms,
				int authKeyLen,
				int authTagLen,
				int ssrcId
			) throws SrtxpSecurityException {
		final Cipher cipherAesCtr = SrtxpContextBase.buildCipherObject();
		SrtxpKmd kmd = new SrtxpKmd(
				mk.getUsed(),
				mk,
				ms,
				authKeyLen,
				authTagLen,
				new BufferExt(),
				ssrcId,
				0L
			);
		return SrtxpKeyDerivation.deriveForRtcp(cipherAesCtr, kmd, 0L);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	// -----------------------------------------------------------------------------------------------------------------

	static void srtpCtxInjectKeys(SrtpContextBase ctx, SessionKeys sessionKeys) throws Exception {
		ctx.setRtpSessionKeys(sessionKeys);
	}

	static void srtcpCtxInjectKeys(SrtcpContextBase ctx, SessionKeys sessionKeys) throws Exception {
		ctx.setRtcpSessionKeys(sessionKeys);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static void srtpCtxOutboundInjectStateRtpRocOutbound(SrtpContextOutbound ctx, int value) throws Exception {
		setPrivateInt(ctx, "ctxStateRtpRocOutbound", value);
	}

	static int srtpCtxOutboundReadStateRtpRocOutbound(SrtpContextOutbound ctx) throws Exception {
		return getPrivateInt(ctx, "ctxStateRtpRocOutbound");
	}

	static int srtpCtxInboundReadStateSrtpRocInbound(SrtpContextInbound ctx) throws Exception {
		return getPrivateInt(ctx, "ctxStateSrtpRocInbound");
	}

	@SuppressWarnings("SameParameterValue")
	static void srtpCtxInboundInjectStateSrtpLastIndex(SrtpContextInbound ctx, long value) throws Exception {
		setPrivateLong(ctx, "ctxStateSrtpLastIndex", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static void srtcpCtxOutboundInjectStateRtcpIndex(SrtcpContextOutbound ctx, int value) throws Exception {
		setPrivateInt(ctx, "ctxStateRtcpIndex", value);
	}

	static int srtcpCtxOutboundReadStateRtcpIndex(SrtcpContextOutbound ctx) throws Exception {
		return getPrivateInt(ctx, "ctxStateRtcpIndex");
	}

	// -----------------------------------------------------------------------------------------------------------------

	static byte[] buildRtpPacket(short hdSeqNr, int hdSsrc, byte[] payload) {
		RtpPacketContainerBase rtpPktCb = RtpPacketContainerBase.createPacketHeader(
				RtpPacketType.V_JPEG,
				new ParamsContainerBase(
						hdSsrc,
						hdSeqNr,
						false,
						0x01020304
					)
			);
		final byte[] originalHd = new byte[rtpPktCb.getPacketSize()];
		rtpPktCb.getPacketBufferPtr().copyInto(0, originalHd, 0, originalHd.length);

		return concat(originalHd, payload);
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

		byte[] iv = new byte[KeySizes.IV_SIZE];
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
				new SecretKeySpec(rtpKeys.encKey().getBaPtr(), 0, rtpKeys.encKey().getUsed(), "AES"),
				new IvParameterSpec(iv)
			);
		cipher.doFinal(plainRtpPacket, headerLen, plainRtpPacket.length - headerLen, encrypted, headerLen);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(
				new SecretKeySpec(rtpKeys.authKey().getBaPtr(), 0, rtpKeys.authKey().getUsed(), "HmacSHA1")
			);
		mac.update(encrypted);
		byte[] rocBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt((int)(packetIndex >>> 16))
				.array();
		mac.update(rocBytes);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), AUTH_TAG_SIZE_FOR_ALL_TESTS);

		return concat(encrypted, tag10);
	}

	@SuppressWarnings("SameParameterValue")
	static byte[] buildExpectedSrtcpPacket(
				byte[] plainRtcpPacket,
				SessionKeys rtcpSessionKeys,
				int ssrc,
				int stateIndexOnly,
				int rtcpSrRrExtendedHeaderLen
			) throws Exception {
		byte[] iv = new byte[KeySizes.IV_SIZE];
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
				new SecretKeySpec(rtcpSessionKeys.encKey().getBaPtr(), 0, rtcpSessionKeys.encKey().getUsed(), "AES"),
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

		byte[] authInput = concat(encrypted, indexBytes);

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(
				new SecretKeySpec(rtcpSessionKeys.authKey().getBaPtr(), 0, rtcpSessionKeys.authKey().getUsed(), "HmacSHA1")
			);
		mac.update(authInput);
		byte[] tag10 = Arrays.copyOf(mac.doFinal(), AUTH_TAG_SIZE_FOR_ALL_TESTS);

		return concat(authInput, tag10);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings({"SameParameterValue", "unused"})
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

	@SuppressWarnings({"SameParameterValue", "unused"})
	private static void setPrivateBufferExt(Object target, String fieldName, BufferExt value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(target, value.clone());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private static int getPrivateInt(Object target, String fieldName) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.getInt(target);
	}

	@SuppressWarnings("unused")
	private static long getPrivateLong(Object target, String fieldName) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.getLong(target);
	}

}
