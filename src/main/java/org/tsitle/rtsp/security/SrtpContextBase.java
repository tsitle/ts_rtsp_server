package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Base class for contexts for SRTP/RTP packets
 */
public abstract class SrtpContextBase extends SrtxpContextBase {

	/** Size of the unencrypted RTP header in bytes */
	protected static final int RTP_PLAIN_HEADER_SIZE = RtpPacketContainerBase.RTP_CONT_HEADER_SIZE;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	protected SrtpContextBase(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		super(true, kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the extra packet length for encrypted SRTP packets
	 * @return Extra packet length
	 */
	public int getSrtpExtraPacketLength() {
		return (KeySizes.AUTH_TAG_SIZE + ctxKmd.mki().getUsed());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void buildIvForRtp(long packetIndex, int ssrc, @NonNull BufferExt curIvBuf) throws SrtxpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		final byte[] tmpIvBytes = new byte[KeySizes.AES_128_KEY_SIZE];
		Arrays.fill(tmpIvBytes, (byte)0);

		/*
		 * RFC 3711 AES-CM counter-block layout (big-endian):
		 * IV = (0x00000000 || SSRC || (packetIndex << 16)) XOR (sessionSalt || 0x0000)
		 *
		 * packetIndex = (ROC << 16) | SEQ (48-bit effective value).
		 *
		 * Byte layout before XOR:
		 *   [0..3]   = 0x00000000
		 *   [4..7]   = SSRC
		 *   [8..13]  = packetIndex (48 bits)
		 *   [14..15] = 0x0000
		 */
		ByteBuffer buf = ByteBuffer.wrap(tmpIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);
		buf.putInt(ssrc);
		/*buf.put((byte)((packetIndex >>> 40) & 0xFF));
		buf.put((byte)((packetIndex >>> 32) & 0xFF));
		buf.put((byte)((packetIndex >>> 24) & 0xFF));
		buf.put((byte)((packetIndex >>> 16) & 0xFF));
		buf.put((byte)((packetIndex >>> 8) & 0xFF));
		buf.put((byte)(packetIndex & 0xFF));
		buf.putShort((short)0);*/
		buf.putLong(packetIndex << 16);

		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			tmpIvBytes[i] ^= ctxSessionKeysRtp.salt().get(i);
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

}
