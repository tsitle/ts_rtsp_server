package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketSR;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JitsiSrtpContextCompatibilityTest {

	@Test
	void rtp_should_match_when_keys_are_derived_by_jitsi_kdf() throws Exception {
		// derive keys via Jitsi reflection
		SessionKeys jitsiRtpKeys = JitsiCommon.jitsiCreateSessionKeysDefaultRtp();

		SrtpContextOutbound ctx = Common.createSrtpCtxOutboundDefault();
		Common.srtpCtxInjectKeys(ctx, jitsiRtpKeys, 0L);

		short hdSeqNr = 0x1234;
		int hdSsrc = 0x10203040;

		final byte[] payload = Common.HEX.parseHex("445566778899AAEEFF01AB23CD45EF00445566778899AAEEFF01AB23CD45EF01445566778899AAEEFF01AB23CD45EF00445566778899AAEEFF01AB23CD45EF02");
		final byte[] plain = Common.buildRtpPacket(hdSeqNr, hdSsrc, payload);

		BufferExt in = new BufferExt();
		in.copyOf(plain);
		BufferExt out = new BufferExt();

		ctx.protectRtp(in, false, false, hdSeqNr, hdSsrc, out);

		byte[] actual = new byte[out.getUsed()];
		out.copyInto(0, actual, 0, actual.length);

		byte[] expected = Common.buildExpectedSrtpPacket(plain, jitsiRtpKeys, hdSeqNr, hdSsrc, 0L);
		assertArrayEquals(expected, actual);
	}

	@Test
	void rtcp_should_match_when_keys_are_derived_by_jitsi_kdf() throws Exception {
		final int packetIndex = 0x12345678;

		// Derive keys via Jitsi reflection
		SessionKeys jitsiRtcpKeys = JitsiCommon.jitsiCreateSessionKeysDefaultRtcp();

		SrtcpContextOutbound ctx = Common.createSrtcpCtxOutboundDefault();
		Common.srtcpCtxInjectKeys(ctx, jitsiRtcpKeys, packetIndex);

		int ssrc = 0x10203040;
		int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		byte[] plainRtcp = new byte[hdrLen + 20];
		for (int i = 0; i < plainRtcp.length; i++) {
			plainRtcp[i] = (byte) (0x40 + i);
		}

		BufferExt in = new BufferExt();
		in.copyOf(plainRtcp);
		BufferExt out = new BufferExt();

		int before = Common.getPrivateInt(ctx, "ctxStateRtcpIndex");
		ctx.protectRtcpSrCompound(in, ssrc, out);
		int after = Common.getPrivateInt(ctx, "ctxStateRtcpIndex");

		byte[] actual = new byte[out.getUsed()];
		out.copyInto(0, actual, 0, actual.length);

		byte[] expected = Common.buildExpectedSrtcpPacket(plainRtcp, jitsiRtcpKeys, ssrc, packetIndex, hdrLen);

		assertArrayEquals(expected, actual);
		assertEquals((before + 1) & 0x7FFFFFFF, after);
	}

}
