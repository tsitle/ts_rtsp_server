package org.tsitle.rtsp.helpers;

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

	public static int getRandomUint32() {
		return (int)(Math.random() * Integer.MAX_VALUE);
	}

}
