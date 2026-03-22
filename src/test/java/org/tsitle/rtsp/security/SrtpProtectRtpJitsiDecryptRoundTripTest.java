package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SrtpProtectRtpJitsiDecryptRoundTripTest {

	private static final HexFormat HEX = HexFormat.of();

	@Test
	@Disabled
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v1() throws Exception {
		// RFC 3711 test vector master material
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		// Derive RTP session keys using your own derivation (same as production path)
		SrtpKeyDerivation.SessionKeys rtpKeys =
				SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, 20);

		SrtpContext ctx = new SrtpContext();
		injectRtpKeys(ctx, rtpKeys.encKey(), rtpKeys.authKey(), rtpKeys.salt(), 0L);

		int seqNr = 0x1234;
		int ssrc = 0x11223344;

		byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,                    // V=2, PT=96
				(byte) (seqNr >>> 8), (byte) seqNr,          // sequence
				0x01, 0x02, 0x03, 0x04,                      // timestamp
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc             // SSRC
			};
		byte[] payload = HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] originalRtp = concat(rtpHeader, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = decryptSrtpWithJitsi(masterKey, masterSalt, ssrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	@Test
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v2() throws Exception {
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		// IMPORTANT: derive RTP session keys using Jitsi KDF
		byte[] rtpEnc = jitsiDerive(masterKey, masterSalt, 0, 16);
		byte[] rtpAuth = jitsiDerive(masterKey, masterSalt, 1, 20);
		byte[] rtpSalt = jitsiDerive(masterKey, masterSalt, 2, 14);

		SrtpContext ctx = new SrtpContext();
		injectRtpKeys(ctx, rtpEnc, rtpAuth, rtpSalt, 0L);

		int seqNr = 0x1234;
		int ssrc = 0x11223344;

		byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x01, 0x02, 0x03, 0x04,
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc
		};
		byte[] payload = HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] originalRtp = concat(rtpHeader, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = decryptSrtpWithJitsi(masterKey, masterSalt, ssrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Jitsi decrypt (reflection-based to tolerate minor API differences)
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
		Object kdf = kdfCtor.newInstance(masterKey, masterSalt, policy);

		byte[] out = new byte[outLen];
		Method derive = kdfClz.getDeclaredMethod("deriveSessionKey", byte[].class, byte.class);
		derive.setAccessible(true);
		derive.invoke(kdf, out, (byte) (label & 0xFF));
		return out;
	}

	private static byte[] decryptSrtpWithJitsi(byte[] masterKey, byte[] masterSalt, int ssrc, byte[] srtpPacket)
			throws Exception {
		Class<?> srtpPolicyClz = Class.forName("org.jitsi.srtp.SrtpPolicy");
		int encType = srtpPolicyClz.getField("AESCM_ENCRYPTION").getInt(null);
		int authType = srtpPolicyClz.getField("HMACSHA1_AUTHENTICATION").getInt(null);

		Object policy = srtpPolicyClz
				.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
				.newInstance(encType, 16, authType, 20, 10, 14);

		Class<?> factoryClz = Class.forName("org.jitsi.srtp.SrtpContextFactory");
		Object factory = constructSrtpContextFactory(factoryClz, srtpPolicyClz, masterKey, masterSalt, policy);

		Method deriveContext = factoryClz.getMethod("deriveContext", int.class, int.class);
		Object cryptoContext = deriveContext.invoke(factory, ssrc, 0);

		Method reverseTransform = selectReverseTransformMethod(cryptoContext.getClass());
		Class<?> pktType = reverseTransform.getParameterTypes()[0];
		Object pkt = createPacketCarrier(pktType, srtpPacket);

		Object ret;
		if (reverseTransform.getParameterCount() == 2) {
			// IMPORTANT: false => do NOT skip decryption
			ret = reverseTransform.invoke(cryptoContext, pkt, false);
		} else {
			ret = reverseTransform.invoke(cryptoContext, pkt);
		}

		if (ret != null && !String.valueOf(ret).equalsIgnoreCase("OK")) {
			throw new IllegalStateException("Jitsi reverseTransformPacket failed: " + ret);
		}

		return extractPacketBytes(pkt);
	}

	private static Method selectReverseTransformMethod(Class<?> cryptoCtxClass) {
		Method oneArg = null;
		Method twoArg = null;

		for (Method m : cryptoCtxClass.getMethods()) {
			if (!m.getName().equals("reverseTransformPacket")) continue;
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 2 && (p[1] == boolean.class || p[1] == Boolean.class)) {
				twoArg = m;
				break; // prefer this one
			}
			if (p.length == 1) {
				oneArg = m;
			}
		}

		Method chosen = (twoArg != null ? twoArg : oneArg);
		if (chosen == null) {
			throw new IllegalStateException("No suitable reverseTransformPacket overload found on " + cryptoCtxClass.getName());
		}
		chosen.setAccessible(true);
		return chosen;
	}

	private static Object constructSrtpContextFactory(
			Class<?> factoryClz,
			Class<?> srtpPolicyClz,
			byte[] masterKey,
			byte[] masterSalt,
			Object policy
	) throws Exception {
		Exception lastError = null;

		for (Constructor<?> ctor : factoryClz.getDeclaredConstructors()) {
			try {
				ctor.setAccessible(true);
				Class<?>[] p = ctor.getParameterTypes();
				Object[] args = new Object[p.length];

				int byteArrayIdx = 0;
				for (int i = 0; i < p.length; i++) {
					Class<?> t = p[i];
					if (t == boolean.class || t == Boolean.class) {
						args[i] = false; // receiver side
					} else if (t == byte[].class) {
						args[i] = (byteArrayIdx++ == 0) ? masterKey : masterSalt;
					} else if (srtpPolicyClz.isAssignableFrom(t)) {
						args[i] = policy;
					} else if ("org.jitsi.utils.logging2.Logger".equals(t.getName())) {
						args[i] = createLoggerArg(t);
					} else if (t == int.class || t == Integer.class) {
						args[i] = 0;
					} else if (t == long.class || t == Long.class) {
						args[i] = 0L;
					} else {
						throw new IllegalArgumentException("Unsupported ctor parameter type: " + t.getName());
					}
				}

				return ctor.newInstance(args);
			} catch (Exception e) {
				lastError = e;
			}
		}

		throw new IllegalStateException("Could not instantiate SrtpContextFactory with available constructors", lastError);
	}

	private static Object createLoggerArg(Class<?> loggerType) {
		if (!loggerType.isInterface()) {
			throw new IllegalStateException("Unsupported logger type: " + loggerType.getName());
		}

		final Object[] selfRef = new Object[1];
		Object proxy = Proxy.newProxyInstance(
				loggerType.getClassLoader(),
				new Class<?>[]{loggerType},
				(proxyObj, method, args) -> {
					String name = method.getName();

					// Critical for Jitsi internals:
					if ("createChildLogger".equals(name)) {
						return selfRef[0];
					}

					// If method returns Logger, never return null.
					if (loggerType.isAssignableFrom(method.getReturnType())) {
						return selfRef[0];
					}

					Class<?> rt = method.getReturnType();
					if (rt == boolean.class) return false;
					if (rt == byte.class) return (byte) 0;
					if (rt == short.class) return (short) 0;
					if (rt == int.class) return 0;
					if (rt == long.class) return 0L;
					if (rt == float.class) return 0f;
					if (rt == double.class) return 0d;
					if (rt == char.class) return '\0';
					return null;
				}
		);
		selfRef[0] = proxy;
		return proxy;
	}

	private static Object createPacketCarrier(Class<?> pktType, byte[] src) throws Exception {
		// Preferred path: constructor(byte[], int, int)
		try {
			Constructor<?> c = pktType.getConstructor(byte[].class, int.class, int.class);
			byte[] copy = Arrays.copyOf(src, src.length);
			return c.newInstance(copy, 0, copy.length);
		} catch (NoSuchMethodException ignore) {
			// fall back to proxy below
		}

		if (!pktType.isInterface()) {
			throw new IllegalStateException("Cannot construct packet carrier for type: " + pktType.getName());
		}

		final class PacketState {
			byte[] buf = Arrays.copyOf(src, src.length);
			int offset = 0;
			int length = src.length;
		}
		PacketState st = new PacketState();

		return Proxy.newProxyInstance(
				pktType.getClassLoader(),
				new Class<?>[]{pktType},
				(proxy, method, args) -> {
					String n = method.getName();
					switch (n) {
						case "getBuffer": return st.buf;
						case "getOffset": return st.offset;
						case "getLength": return st.length;
						case "setOffset":
							st.offset = (int) args[0];
							return null;
						case "setLength":
							st.length = (int) args[0];
							return null;
						case "setOffsetLength":
							st.offset = (int) args[0];
							st.length = (int) args[1];
							return null;
						case "isInvalid":
							return false;
						case "readRegionToBuff": {
							int off = (int) args[0];
							int len = (int) args[1];
							byte[] out = (byte[]) args[2];
							System.arraycopy(st.buf, st.offset + off, out, 0, len);
							return null;
						}
						case "grow": {
							int newCapacity = (int) args[0];
							if (st.buf.length < newCapacity) st.buf = Arrays.copyOf(st.buf, newCapacity);
							return null;
						}
						case "append": {
							byte[] in = (byte[]) args[0];
							int len = (int) args[1];
							int needed = st.offset + st.length + len;
							if (st.buf.length < needed) st.buf = Arrays.copyOf(st.buf, needed);
							System.arraycopy(in, 0, st.buf, st.offset + st.length, len);
							st.length += len;
							return null;
						}
						case "shrink":
							st.length = Math.max(0, st.length - (int) args[0]);
							return null;
						default:
							Class<?> rt = method.getReturnType();
							if (rt == boolean.class) return false;
							if (rt == byte.class) return (byte) 0;
							if (rt == short.class) return (short) 0;
							if (rt == int.class) return 0;
							if (rt == long.class) return 0L;
							if (rt == float.class) return 0f;
							if (rt == double.class) return 0d;
							if (rt == char.class) return '\0';
							return null;
					}
				}
		);
	}

	private static byte[] extractPacketBytes(Object pkt) throws Exception {
		Method getBuffer = pkt.getClass().getMethod("getBuffer");
		Method getOffset = pkt.getClass().getMethod("getOffset");
		Method getLength = pkt.getClass().getMethod("getLength");

		byte[] buf = (byte[]) getBuffer.invoke(pkt);
		int off = (int) getOffset.invoke(pkt);
		int len = (int) getLength.invoke(pkt);
		return Arrays.copyOfRange(buf, off, off + len);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Private-field test injection helpers (same approach as your existing tests)
	// -----------------------------------------------------------------------------------------------------------------

	private static void injectRtpKeys(SrtpContext ctx, byte[] encKey, byte[] authKey, byte[] salt, long roc)
			throws Exception {
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionEncKey", encKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionAuthKey", authKey);
		copyIntoPrivateByteArray(ctx, "ctxRtpSessionSalt", salt);
		setPrivateBoolean(ctx, "haveMikey", true);
		setPrivateLong(ctx, "ctxRtpRoc", roc);
	}

	private static void copyIntoPrivateByteArray(Object target, String fieldName, byte[] value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		byte[] dst = (byte[]) f.get(target);
		System.arraycopy(value, 0, dst, 0, Math.min(dst.length, value.length));
	}

	private static void setPrivateBoolean(Object target, String fieldName, boolean value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setBoolean(target, value);
	}

	private static void setPrivateLong(Object target, String fieldName, long value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.setLong(target, value);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		ByteBuffer out = ByteBuffer.allocate(a.length + b.length);
		out.put(a);
		out.put(b);
		return out.array();
	}

}
