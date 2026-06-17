package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpSeqNr;

import java.util.Arrays;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class JitsiSrtpProtectRoundTripTest {

	@Test
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v1() throws Exception {
		final RtspProtoRtpSeqNr hdSeqNr = RtspProtoRtpSeqNr.of(0x1289);
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0xAB12CD34L);

		// derive RTP session keys
		SrtpContextOutbound ctx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(ctx, 0);
		Common.srtpCtxInjectKeys(ctx, rtpKeys);

		final byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		final byte[] originalRtp = Common.buildRtpPacket(hdSeqNr, hdSsrc, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, hdSeqNr, hdSsrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = jitsiDecryptSrtp(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, hdSsrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	@Test
	void protectRtp_then_jitsi_decrypt_should_restore_original_packet_v2() throws Exception {
		final RtspProtoRtpSeqNr hdSeqNr = RtspProtoRtpSeqNr.of(0x1234);
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x11223344L);

		// derive RTP session keys using Jitsi KDF
		SessionKeys jitsiRtpKeys = JitsiCommon.jitsiCreateSessionKeysDefaultRtp();

		SrtpContextOutbound ctx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(ctx, 0);
		Common.srtpCtxInjectKeys(ctx, jitsiRtpKeys);

		final byte[] payload = Common.HEX.parseHex("445566778899AABBCCDDEEFF01AB23CD45EF");
		final byte[] originalRtp = Common.buildRtpPacket(hdSeqNr, hdSsrc, payload);

		BufferExt in = new BufferExt();
		in.copyOf(originalRtp);
		BufferExt encrypted = new BufferExt();

		ctx.protectRtp(in, false, false, hdSeqNr, hdSsrc, encrypted);

		byte[] srtpPacket = new byte[encrypted.getUsed()];
		encrypted.copyInto(0, srtpPacket, 0, srtpPacket.length);

		byte[] decryptedByJitsi = jitsiDecryptSrtp(Common.DEFAULT_MASTER_KEY, Common.DEFAULT_MASTER_SALT, hdSsrc, srtpPacket);

		assertArrayEquals(originalRtp, decryptedByJitsi,
				"Jitsi-decrypted RTP must match original RTP packet byte-for-byte");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] jitsiDecryptSrtp(
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterKey,
				@SuppressWarnings("SameParameterValue") @NonNull BufferExt masterSalt,
				@NonNull RtspProtoIdXsrc ssrc,
				byte[] srtpPacket
			) throws Exception {
		org.jitsi.srtp.SrtpPolicy policyObj = JitsiCommon.jitsiCreateSrtpPolicy();

		org.jitsi.utils.logging2.Logger loggerObj = new org.jitsi.utils.logging2.LoggerImpl("someLogger", Level.WARNING);
		org.jitsi.srtp.SrtpContextFactory factoryObj = new org.jitsi.srtp.SrtpContextFactory(
				false,  // is sender?
				Common.createByteArrayFromBuffer(masterKey),
				Common.createByteArrayFromBuffer(masterSalt),
				policyObj,  // SRTP Policy
				policyObj,  // SRTCP Policy
				loggerObj
			);

		int tmpSsrcInt = ssrc.getId32bit().orElse(0L).intValue();
		org.jitsi.srtp.SrtpCryptoContext cryptoContext = factoryObj.deriveContext(tmpSsrcInt, 0);

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
