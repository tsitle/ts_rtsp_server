package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketSR;
import org.tsitle.rtsp.security.constants.KeySizes;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Base class for contexts for SRTCP/RTCP packets
 */
public class SrtcpContextBase extends SrtxpContextBase {

	/** Size of the SRTCP Index Field in bytes */
	protected static final int SRTCP_INDEX_FIELD_SIZE = 4;

	/** Size of the unencrypted RTCP header in bytes */
	protected static final int RTCP_PLAIN_HEADER_SIZE = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public SrtcpContextBase(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		super(false, kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the extra packet length for encrypted SRTCP packets
	 * @return Extra packet length
	 */
	public int getSrtcpExtraPacketLength() {
		return (ctxKmd.authTagLen() + SRTCP_INDEX_FIELD_SIZE + ctxKmd.mki().sizeBytes());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void buildIvForRtcp(int packetIndex, @NonNull RtspProtoIdXsrc ssrc, @NonNull BufferExt curIvBuf)
			throws SrtxpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		final byte[] tmpIvBytes = new byte[KeySizes.IV_SIZE];  // IV is always 128 bits (16 bytes) regardless of key size
		Arrays.fill(tmpIvBytes, (byte)0);

		/*
		 * RFC-3711 AES-CM counter-block layout (big-endian):
		 * IV = (0x00000000 || SSRC || (packetIndex << 16)) XOR (sessionSalt || 0x0000)
		 *
		 * Byte layout before XOR:
		 *   [0..3]   = 0x00000000
		 *   [4..7]   = SSRC
		 *   [8..9]   = 0x0000
		 *   [10..13] = packetIndex (31-bit value, E-bit stripped already)
		 *   [14..15] = 0x0000
		 */
		ByteBuffer buf = ByteBuffer.wrap(tmpIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);
		buf.putInt(ssrc.getId32bit().orElse(0L).intValue());
		buf.putShort((short)0);
		buf.putInt(packetIndex);
		buf.putShort((short)0);

		// XOR first 112 bits with session salt
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			tmpIvBytes[i] ^= ctxSessionKeysRtcp.salt().get(i);
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

}
