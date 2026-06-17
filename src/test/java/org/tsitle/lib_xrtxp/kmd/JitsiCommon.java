package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.types.SessionKeys;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;

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
		return new org.jitsi.srtp.SrtpPolicy(
				org.jitsi.srtp.SrtpPolicy.AESCM_ENCRYPTION,
				Common.ENCR_KEY_SIZE_FOR_ALL_TESTS,
				org.jitsi.srtp.SrtpPolicy.HMACSHA1_AUTHENTICATION,
				Common.AUTH_KEY_SIZE_FOR_ALL_TESTS,
				Common.AUTH_TAG_SIZE_FOR_ALL_TESTS,
				KeySizes.SALT_SIZE
			);
	}

	static @NonNull SessionKeys jitsiCreateSessionKeysDefaultRtp() throws Exception {
		// Derive keys via Jitsi reflection (labels 0,1,2 for RTP)
		byte[] rtpEnc = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 0, Common.ENCR_KEY_SIZE_FOR_ALL_TESTS);
		byte[] rtpAuth = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 1, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] rtpSalt = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 2, KeySizes.SALT_SIZE);

		return new SessionKeys(new BufferExt(rtpEnc), new BufferExt(rtpAuth), new BufferExt(rtpSalt));
	}

	static @NonNull SessionKeys jitsiCreateSessionKeysDefaultRtcp() throws Exception {
		// Derive keys via Jitsi reflection (labels 3,4,5 for RTCP)
		byte[] rtcpEnc = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 3, Common.ENCR_KEY_SIZE_FOR_ALL_TESTS);
		byte[] rtcpAuth = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 4, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] rtcpSalt = jitsiDerive(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, 5, KeySizes.SALT_SIZE);

		return new SessionKeys(new BufferExt(rtcpEnc), new BufferExt(rtcpAuth), new BufferExt(rtcpSalt));
	}

}
