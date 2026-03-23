package org.tsitle.rtsp.security.constants;

public class KeySizes {

	/** Size of the AES-128 key in bytes */
	public static final int AES_128_KEY_SIZE = 16;
	/** Size of the Authentication Key in bytes - for HMAC-SHA1-80 */
	public static final int AUTH_KEY_SIZE_080 = 10;
	/** Size of the Authentication Key in bytes - for HMAC-SHA1-160 */
	public static final int AUTH_KEY_SIZE_160 = 20;
	/** Size of the Salt in bytes */
	public static final int SALT_SIZE = 14;

	/** Size of the Authentication Tag in bytes */
	public static final int AUTH_TAG_SIZE = 10;

	/** Size of the Master Key Identifier in bytes */
	public static final int MKI_SIZE = 4;

}
