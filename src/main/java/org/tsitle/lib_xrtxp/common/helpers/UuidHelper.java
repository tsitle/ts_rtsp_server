package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

public final class UuidHelper {

	private UuidHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String generateUuid(boolean onlyHexChars) {
		String resS = generateCustomUuid();
		if (onlyHexChars) {
			resS = convertCustomToDefault(resS);
		}
		return resS;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String convertCustomToDefault(@NonNull String customUuid) {
		return customUuid
				.replace("j", "1")
				.replace("k", "2")
				.replace("h", "2")
				.replace("m", "3")
				.replace("u", "3")
				.replace("q", "a")
				.replace("r", "b")
				.replace("z", "f")
				.replace("s", "c")
				.replace("t", "c")
				.replace("x", "d")
				.replace("v", "d")
				.replace("y", "e")
				.replace("w", "e")
				.replace("n", "a")
				.replace("p", "b")
				.replace("g", "f")
				.replace("i", "f");
	}

	private static @NonNull String generateCustomUuid() {
		// we generate the random blocks first, so that the NTP timestamp doesn't provide too much information
		String blockRnd1 = String.format("%04x", RandomHelper.getRandomUint16())
				.replace("1", "j")  // we replace the digits just for fun (no sortability required)
				.replace("2", (Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 2 == 0 ? "k" : "h"))
				.replace("3", (Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 2 == 0 ? "m" : "u"));
		String tmp8chars = String.format("%04x%04x",
					RandomHelper.getRandomUint16(),
					RandomHelper.getRandomUint16()
				)
				.replace("a", "q")  // we replace the letters just for fun (no sortability required)
				.replace("b", "r")
				.replace("f", "z");
		StringBuilder blockRnd2dot1 = new StringBuilder();
		for (char c : tmp8chars.toCharArray()) {
			if (c == 'c') {
				c = (Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 2 == 0 ? 's' : 't');
			} else if (c == 'd') {
				c = (Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 2 == 0 ? 'x' : 'v');
			} else if (c == 'e') {
				c = (Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 2 == 0 ? 'y' : 'w');
			}
			blockRnd2dot1.append(c);
		}
		String blockRnd2dot2 = "";
		for (int i = 0; i < Math.clamp(Short.toUnsignedInt(RandomHelper.getRandomUint16()) % 10, 1, 10); i++) {
			blockRnd2dot2 = String.format("%04x", RandomHelper.getRandomUint16())
					.replace("a", "n")  // we replace the letters just for fun (no sortability required)
					.replace("b", "p");
		}

		// we use the NTP timestamp as a base for the UUID. This will provide sortability
		NtpTimestamp tmpTs = NtpTimestamp.ofNow();
		String blockTs1 = String.format("%08x", tmpTs.getSeconds32bit().orElseThrow());
		String blockTs2 = String.format("%04x", (tmpTs.getFraction32bit().orElseThrow() >>> 16) & 0xFFFFL)
				.replace("f", "g");  // we replace this letter just for fun (but keeping sortability)
		String blockTs3 = String.format("%04x", tmpTs.getFraction32bit().orElseThrow() & 0xFFFFL)
				.replace("f", "i");  // we replace this letter just for fun (but keeping sortability)

		/*
		 * The resulting UUID has an increased alphabet size (+18 characters --> 34), which increases the entropy of the UUID.
		 * Example output: 'ede77ae5-cbgb-bia4-6eje-1vr179zq18d0'
		 */
		return String.format("%s-%s-%s-%s-%s%s", blockTs1, blockTs2, blockTs3, blockRnd1, blockRnd2dot1, blockRnd2dot2);
	}

}
