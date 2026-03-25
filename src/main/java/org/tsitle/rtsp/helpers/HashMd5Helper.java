package org.tsitle.rtsp.helpers;

import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashMd5Helper {

	private HashMd5Helper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String hashOfBytes(byte[] bytes, boolean outputUppercase) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("MD5 algorithm not available");
		}
		md.update(bytes);
		byte[] digest = md.digest();
		StringBuilder resS = new StringBuilder();
		for (byte b : digest) {
			resS.append(String.format("%02" + (outputUppercase ? "X" : "x"), b));
		}
		return resS.toString();
	}

	public static @NonNull String hashOfString(@NonNull String input, boolean outputUppercase) {
		return hashOfBytes(input.getBytes(StandardCharsets.UTF_8), outputUppercase);
	}

}
