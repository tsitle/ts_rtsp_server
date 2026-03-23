package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;

import java.util.Arrays;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class JitsiSrtpProtectRoundTripTest {

	@Test
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v1() throws Exception {
		// derive RTP session keys
		SrtxpContext ctx = new SrtxpContext();
		SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();
		Common.srtpCtxInjectRtpKeys(ctx, rtpKeys, 0L);

		int seqNr = 0x1234;
		int ssrc = 0x11223344;

		byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,                    // V=2, PT=96
				(byte) (seqNr >>> 8), (byte) seqNr,          // sequence
				0x01, 0x02, 0x03, 0x04,                      // timestamp
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc             // SSRC
			};
		byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] originalRtp = Common.concat(rtpHeader, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = jitsiDecryptSrtp(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, ssrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	@Test
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v2() throws Exception {
		// derive RTP session keys using Jitsi KDF
		SessionKeys jitsiRtpKeys = JitsiCommon.jitsiCreateSessionKeysDefaultRtp();

		SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(ctx, jitsiRtpKeys, 0L);

		int seqNr = 0x1234;
		int ssrc = 0x11223344;

		byte[] rtpHeader = new byte[] {
				(byte) 0x80, (byte) 0x60,
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x01, 0x02, 0x03, 0x04,
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16),
				(byte) (ssrc >>> 8), (byte) ssrc
			};
		byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] originalRtp = Common.concat(rtpHeader, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = jitsiDecryptSrtp(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, ssrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] jitsiDecryptSrtp(
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterKey,
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterSalt,
				int ssrc,
				byte[] srtpPacket
			) throws Exception {
		org.jitsi.srtp.SrtpPolicy policyObj = JitsiCommon.jitsiCreateSrtpPolicy();

		org.jitsi.utils.logging2.Logger loggerObj = new org.jitsi.utils.logging2.LoggerImpl("someLogger", Level.WARNING);
		org.jitsi.srtp.SrtpContextFactory factoryObj = new org.jitsi.srtp.SrtpContextFactory(
				false,  // is sender?
				Common.createByteArrayFromBuffer(masterKey),
				Common.createByteArrayFromBuffer(masterSalt),
				policyObj,
				policyObj,
				loggerObj
			);

		org.jitsi.srtp.SrtpCryptoContext cryptoContext = factoryObj.deriveContext(ssrc, 0);

		org.jitsi.utils.ByteArrayBuffer pktBab = new JitsiSimpleByteArrayBuffer(srtpPacket);
		org.jitsi.srtp.SrtpErrorStatus ret = cryptoContext.reverseTransformPacket(pktBab, false);

		if (ret != null && ! String.valueOf(ret).equalsIgnoreCase("OK")) {
			throw new IllegalStateException("Jitsi reverseTransformPacket failed: " + ret);
		}

		byte[] buf = pktBab.getBuffer();
		int off = pktBab.getOffset();
		int len = pktBab.getLength();
		return Arrays.copyOfRange(buf, off, off + len);
	}

}
