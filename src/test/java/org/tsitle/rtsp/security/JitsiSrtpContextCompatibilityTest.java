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

		SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(ctx, jitsiRtpKeys, 0L);

		short seqNr = 0x1234;
		int ssrc = 0x10203040;

		byte[] header = new byte[]{
				(byte) 0x80, (byte) 0x60,
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x01, 0x02, 0x03, 0x04,
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16), (byte) (ssrc >>> 8), (byte) ssrc
			};
		byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF");
		byte[] plain = Common.concat(header, payload);

		BufferExt in = new BufferExt();
		in.copyOf(plain);
		BufferExt out = new BufferExt();

		ctx.protectRtp(in, false, false, seqNr, ssrc, out);

		byte[] actual = new byte[out.getUsed()];
		out.copyInto(0, actual, 0, actual.length);

		byte[] expected = Common.buildExpectedSrtpPacket(plain, jitsiRtpKeys, seqNr, ssrc, 0L);
		assertArrayEquals(expected, actual);
	}

	@Test
	void rtcp_should_match_when_keys_are_derived_by_jitsi_kdf() throws Exception {
		final int packetIndex = 0x12345678;

		// Derive keys via Jitsi reflection
		SessionKeys jitsiRtcpKeys = JitsiCommon.jitsiCreateSessionKeysDefaultRtcp();

		SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtcpKeys(ctx, jitsiRtcpKeys, packetIndex);

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
