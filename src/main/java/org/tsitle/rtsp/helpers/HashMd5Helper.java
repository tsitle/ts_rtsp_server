package org.tsitle.rtsp.helpers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashMd5Helper {

	public static String hashOfBytes(byte[] bytes) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		md.update(bytes);
		byte[] digest = md.digest();
		StringBuilder resS = new StringBuilder();
		for (byte b : digest) {
			resS.append(String.format("%02X", b));
		}
		return resS.toString();
	}

}
