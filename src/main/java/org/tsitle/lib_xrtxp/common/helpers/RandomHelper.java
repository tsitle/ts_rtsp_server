package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.security.SecureRandom;

/**
 * Random number helper.
 */
public final class RandomHelper {

	private RandomHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static short getRandomUint16() {
		return (short)(Math.random() * Short.MAX_VALUE);
	}

	@SuppressWarnings("unused")
	public static int getRandomIntRange(int min, int max) {
		if (min > max) {
			throw new IllegalArgumentException("min must be less than or equal to max");
		}
		return (int) (Math.random() * (max - min + 1) + min);
	}

	public static int getRandomUint32(boolean allowZero) {
		int resI;
		do {
			resI = (int)(Math.random() * Integer.MAX_VALUE);
		} while (! allowZero && resI == 0);
		return resI;
	}

	public static void getSecureRandomBytes(int length, @NonNull BufferExt buffer) {
		if (length < 1) {
			throw new IllegalArgumentException("length must be greater than 0");
		}
		buffer.increaseSize(length);
		buffer.setUsed(length);

		final SecureRandom rnd = new SecureRandom();
		byte[] tmpBa = new byte[length];
		rnd.nextBytes(tmpBa);
		buffer.copyOf(tmpBa);
	}
}
