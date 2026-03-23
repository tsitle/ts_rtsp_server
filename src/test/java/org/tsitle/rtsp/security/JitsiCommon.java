package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class JitsiCommon {

	static byte[] jitsiDerive(
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterKey,
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterSalt,
				int label,
				int outLen
			) throws Exception {
		org.jitsi.srtp.SrtpPolicy policyObj = jitsiCreateSrtpPolicy();
		Class<?> policyClz = policyObj.getClass();

		Class<?> kdfClz = Class.forName("org.jitsi.srtp.SrtpKdf");
		Constructor<?> kdfCtor = kdfClz.getDeclaredConstructor(byte[].class, byte[].class, policyClz);
		kdfCtor.setAccessible(true);
		Object kdf = kdfCtor.newInstance(
				Common.createByteArrayFromBuffer(masterKey),
				Common.createByteArrayFromBuffer(masterSalt),
				policyObj
			);

		byte[] out = new byte[outLen];

		Method derive = kdfClz.getDeclaredMethod("deriveSessionKey", byte[].class, byte.class);
		derive.setAccessible(true);
		derive.invoke(kdf, out, (byte) (label & 0xFF));

		return out;
	}

	static org.jitsi.srtp.@NonNull SrtpPolicy jitsiCreateSrtpPolicy() throws AssertionError {
		if (KeySizes.AES_128_KEY_SIZE != 16) {
			throw new AssertionError("Invalid AES Key size");
		}
		if (KeySizes.AUTH_KEY_SIZE_160 != 20) {
			throw new AssertionError("Invalid Auth Key size");
		}
		if (KeySizes.AUTH_TAG_SIZE != 10) {
			throw new AssertionError("Invalid Auth Tag size");
		}
		if (KeySizes.SALT_SIZE != 14) {
			throw new AssertionError("Invalid Salt size");
		}
		return new org.jitsi.srtp.SrtpPolicy(
				org.jitsi.srtp.SrtpPolicy.AESCM_ENCRYPTION,
				KeySizes.AES_128_KEY_SIZE,
				org.jitsi.srtp.SrtpPolicy.HMACSHA1_AUTHENTICATION,
				KeySizes.AUTH_KEY_SIZE_160,
				KeySizes.AUTH_TAG_SIZE,
				KeySizes.SALT_SIZE
			);
	}

	static @NonNull SessionKeys jitsiCreateSessionKeysDefaultRtp() throws Exception {
		// Derive keys via Jitsi reflection (labels 0,1,2 for RTP)
		byte[] rtpEnc = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 0, KeySizes.AES_128_KEY_SIZE);
		byte[] rtpAuth = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 1, KeySizes.AUTH_KEY_SIZE_160);
		byte[] rtpSalt = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 2, KeySizes.SALT_SIZE);

		return new SessionKeys(
				Common.createBufferFromBa(rtpEnc),
				Common.createBufferFromBa(rtpAuth),
				Common.createBufferFromBa(rtpSalt)
			);
	}

	static @NonNull SessionKeys jitsiCreateSessionKeysDefaultRtcp() throws Exception {
		// Derive keys via Jitsi reflection (labels 3,4,5 for RTCP)
		byte[] rtcpEnc = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 3, KeySizes.AES_128_KEY_SIZE);
		byte[] rtcpAuth = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 4, KeySizes.AUTH_KEY_SIZE_160);
		byte[] rtcpSalt = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 5, KeySizes.SALT_SIZE);

		return new SessionKeys(
				Common.createBufferFromBa(rtcpEnc),
				Common.createBufferFromBa(rtcpAuth),
				Common.createBufferFromBa(rtcpSalt)
			);
	}

}
