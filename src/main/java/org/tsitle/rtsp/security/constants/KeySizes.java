package org.tsitle.rtsp.security.constants;

public class KeySizes {

	/** Size of the AES Encryption key in bytes - for AES-CM-128 */
	public static final int AES_KEY_SIZE_128 = 16;
	/** Size of the AES Encryption key in bytes - for AES-CM-256 */
	public static final int AES_KEY_SIZE_256 = 32;
	/** Size of the Authentication Key in bytes - for HMAC-SHA1-80 */
	public static final int AUTH_KEY_SIZE_080 = 10;
	/** Size of the Authentication Key in bytes - for HMAC-SHA1-160 */
	public static final int AUTH_KEY_SIZE_160 = 20;
	/** Size of the Salt in bytes */
	public static final int SALT_SIZE = 14;

	/** Size of the SHA1-160 hash in bytes - for HMAC-SHA1-160 */
	public static final int SHA1_SIZE_160 = 20;

	/** Size of the Initialization Vector in bytes */
	public static final int IV_SIZE = 16;

}
