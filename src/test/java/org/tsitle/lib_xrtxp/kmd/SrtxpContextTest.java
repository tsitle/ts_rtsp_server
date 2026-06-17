package org.tsitle.lib_xrtxp.kmd;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.types.SessionKeys;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpPacketSR;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SrtxpContextTest {

	@Test
	void protectRtp_should_encrypt_payload_and_append_valid_auth_tag() throws Exception {
		final RtspProtoRtpSeqNr hdSeqNr = RtspProtoRtpSeqNr.of(0x1234);
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0xDEC0ADDEL);

		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(hdSsrc);

		final SrtpContextOutbound ctx = Common.createSrtpCtxOutboundDefault(hdSsrc);
		Common.srtpCtxOutboundInjectStateRtpRocOutbound(ctx, 0);
		Common.srtpCtxInjectKeys(ctx, rtpKeys);

		final byte[] payload = Common.HEX.parseHex("445566778899AAEEFF01AB23CD45EF00445566778899AAEEFF01AB23CD45EF01445566778899AAEEFF01AB23CD45EF00445566778899AAEEFF01AB23CD45EF02");
		final byte[] plainPacket = Common.buildRtpPacket(hdSeqNr, hdSsrc, payload);

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainPacket);
		final BufferExt outBuf = new BufferExt();

		ctx.protectRtp(plainBuf, false, false, hdSeqNr, hdSsrc, outBuf);

		final byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		final byte[] expected = Common.buildExpectedSrtpPacket(
				plainPacket,
				rtpKeys,
				hdSeqNr,
				hdSsrc,
				0L
			);

		assertArrayEquals(expected, actual, "SRTP packet mismatch");
		assertFalse(Arrays.equals(
				Arrays.copyOfRange(plainPacket, 12, plainPacket.length),
				Arrays.copyOfRange(actual, 12, 12 + payload.length)
			), "Encrypted payload should differ from plaintext payload");
	}

	@Test
	void protectRtcpSrCompound_should_encrypt_payload_and_append_index_and_auth_tag() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0xDEC0ADDEL);

		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		final SrtcpContextOutbound ctx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(ctx, 0);
		Common.srtcpCtxInjectKeys(ctx, rtcpKeys);

		final int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		final byte[] plainRtcp = new byte[hdrLen + 12];
		for (int i = 0; i < plainRtcp.length; i++) {
			plainRtcp[i] = (byte) (0xA0 + i);
		}

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainRtcp);
		final BufferExt outBuf = new BufferExt();

		final int beforeIndex = Common.srtcpCtxOutboundReadStateRtcpIndex(ctx);
		ctx.protectRtcpSrCompound(plainBuf, hdSsrc, outBuf);
		final int afterIndex = Common.srtcpCtxOutboundReadStateRtcpIndex(ctx);

		final byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		final byte[] expected = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				hdSsrc,
				0,
				hdrLen
			);

		assertArrayEquals(expected, actual, "SRTCP packet mismatch");
		assertEquals((beforeIndex + 1) & 0x7FFFFFFF, afterIndex, "SRTCP index should increment modulo 31 bits");

		assertFalse(Arrays.equals(
				Arrays.copyOfRange(plainRtcp, hdrLen, plainRtcp.length),
				Arrays.copyOfRange(actual, hdrLen, hdrLen + (plainRtcp.length - hdrLen))
			), "Encrypted RTCP payload should differ from plaintext payload");
	}

	@Test
	void fuzz_randomized_rtp_and_rtcp_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 200;

		for (int i = 0; i < rounds; i++) {
			final byte[] rndMasterKey = new byte[Common.ENCR_KEY_SIZE_FOR_ALL_TESTS];
			final byte[] rndMasterSalt = new byte[KeySizes.SALT_SIZE];
			rnd.nextBytes(rndMasterKey);
			rnd.nextBytes(rndMasterSalt);

			//
			final RtspProtoRtpSeqNr rndSeqNr = RtspProtoRtpSeqNr.withOverflow(rnd.nextInt(0x10000));
			final RtspProtoIdXsrc rndSsrcRtp = RtspProtoIdXsrc.of(Integer.toUnsignedLong(rnd.nextInt()));

			final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(rndSsrcRtp);
			final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(rndSsrcRtp);

			// ---------------- RTP ----------------
			final SrtpContextOutbound rtpCtx = Common.createSrtpCtxOutboundDefault(rndSsrcRtp);
			Common.srtpCtxOutboundInjectStateRtpRocOutbound(rtpCtx, 0);
			Common.srtpCtxInjectKeys(rtpCtx, rtpKeys);

			final int rndRtpPayloadLen = rnd.nextInt(1, 400);
			final byte[] rndRtpPayload = new byte[rndRtpPayloadLen];
			rnd.nextBytes(rndRtpPayload);

			final byte[] plainRtp = Common.buildRtpPacket(rndSeqNr, rndSsrcRtp, rndRtpPayload);

			final BufferExt rtpIn = new BufferExt();
			rtpIn.copyOf(plainRtp);
			final BufferExt rtpOut = new BufferExt();

			rtpCtx.protectRtp(rtpIn, false, false, rndSeqNr, rndSsrcRtp, rtpOut);

			final byte[] rtpActual = new byte[rtpOut.getUsed()];
			rtpOut.copyInto(0, rtpActual, 0, rtpActual.length);

			final byte[] rtpExpected = Common.buildExpectedSrtpPacket(
					plainRtp,
					rtpKeys,
					rndSeqNr,
					rndSsrcRtp,
					0L
				);

			assertArrayEquals(rtpExpected, rtpActual, "RTP mismatch at round=" + i);

			// ---------------- RTCP ----------------
			final SrtcpContextOutbound rtcpCtx = Common.createSrtcpCtxOutboundDefault(rndSsrcRtp);
			Common.srtcpCtxOutboundInjectStateRtcpIndex(rtcpCtx, 0);
			Common.srtcpCtxInjectKeys(rtcpCtx, rtcpKeys);

			final RtspProtoIdXsrc rndSsrcRtcp = RtspProtoIdXsrc.of(Integer.toUnsignedLong(rnd.nextInt()));
			final int rtcpHdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
			final int rndRtcpPayloadLen = rnd.nextInt(1, 300);

			final byte[] rndPlainRtcp = new byte[rtcpHdrLen + rndRtcpPayloadLen];
			rnd.nextBytes(rndPlainRtcp);

			final BufferExt rtcpIn = new BufferExt();
			rtcpIn.copyOf(rndPlainRtcp);
			final BufferExt rtcpOut = new BufferExt();

			rtcpCtx.protectRtcpSrCompound(rtcpIn, rndSsrcRtcp, rtcpOut);

			final byte[] rtcpActual = new byte[rtcpOut.getUsed()];
			rtcpOut.copyInto(0, rtcpActual, 0, rtcpActual.length);

			final byte[] rtcpExpected = Common.buildExpectedSrtcpPacket(
					rndPlainRtcp,
					rtcpKeys,
					rndSsrcRtcp,
					0,
					rtcpHdrLen
				);

			assertArrayEquals(rtcpExpected, rtcpActual, "RTCP mismatch at round=" + i);
			assertEquals(
					1,
					Common.srtcpCtxOutboundReadStateRtcpIndex(rtcpCtx),
					"RTCP index mismatch at round=" + i
				);
		}
	}

	@Test
	void fuzz_randomized_with_non_zero_roc_and_srtcp_index_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 150;

		for (int i = 0; i < rounds; i++) {
			final byte[] rndMasterKey = new byte[Common.ENCR_KEY_SIZE_FOR_ALL_TESTS];
			final byte[] rndMasterSalt = new byte[KeySizes.SALT_SIZE];
			rnd.nextBytes(rndMasterKey);
			rnd.nextBytes(rndMasterSalt);

			//
			final RtspProtoRtpSeqNr rndSeqNr = RtspProtoRtpSeqNr.withOverflow(rnd.nextInt(0x10000));
			final RtspProtoIdXsrc rndSsrcRtp = RtspProtoIdXsrc.of(Integer.toUnsignedLong(rnd.nextInt()));

			final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(rndSsrcRtp);
			final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(rndSsrcRtp);

			// ---------------- RTP with non-zero ROC ----------------
			final SrtpContextOutbound rtpCtx = Common.createSrtpCtxOutboundDefault(rndSsrcRtp);
			final int rndStateRoc = rnd.nextInt(1, 100_000); // non-zero, keeps math simple and fast
			Common.srtpCtxOutboundInjectStateRtpRocOutbound(rtpCtx, rndStateRoc);
			Common.srtpCtxInjectKeys(rtpCtx, rtpKeys);

			final int rndRtpPayloadLen = rnd.nextInt(1, 500);
			final byte[] rndRtpPayload = new byte[rndRtpPayloadLen];
			rnd.nextBytes(rndRtpPayload);

			final byte[] plainRtp = Common.buildRtpPacket(rndSeqNr, rndSsrcRtp, rndRtpPayload);

			final BufferExt rtpIn = new BufferExt();
			rtpIn.copyOf(plainRtp);
			final BufferExt rtpOut = new BufferExt();

			rtpCtx.protectRtp(rtpIn, false, false, rndSeqNr, rndSsrcRtp, rtpOut);

			final byte[] rtpActual = new byte[rtpOut.getUsed()];
			rtpOut.copyInto(0, rtpActual, 0, rtpActual.length);

			final byte[] rtpExpected = Common.buildExpectedSrtpPacket(
					plainRtp,
					rtpKeys,
					rndSeqNr,
					rndSsrcRtp,
					rndStateRoc
				);

			assertArrayEquals(rtpExpected, rtpActual, "RTP mismatch with non-zero ROC at round=" + i);

			// ---------------- RTCP with non-zero start index ----------------
			final SrtcpContextOutbound rtcpCtx = Common.createSrtcpCtxOutboundDefault(rndSsrcRtp);
			final int rndStateStartIndex = rnd.nextInt(1, 0x7FFFFFFF); // non-zero, 31-bit
			Common.srtcpCtxOutboundInjectStateRtcpIndex(rtcpCtx, rndStateStartIndex);
			Common.srtcpCtxInjectKeys(rtcpCtx, rtcpKeys);

			final RtspProtoIdXsrc rndSsrcRtcp = RtspProtoIdXsrc.of(Integer.toUnsignedLong(rnd.nextInt()));
			final int rtcpHdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
			final int rndRtcpPayloadLen = rnd.nextInt(1, 300);

			final byte[] rndPlainRtcp = new byte[rtcpHdrLen + rndRtcpPayloadLen];
			rnd.nextBytes(rndPlainRtcp);

			final BufferExt rtcpIn = new BufferExt();
			rtcpIn.copyOf(rndPlainRtcp);
			final BufferExt rtcpOut = new BufferExt();

			final int before = Common.srtcpCtxOutboundReadStateRtcpIndex(rtcpCtx);
			rtcpCtx.protectRtcpSrCompound(rtcpIn, rndSsrcRtcp, rtcpOut);
			final int after = Common.srtcpCtxOutboundReadStateRtcpIndex(rtcpCtx);

			final byte[] rtcpActual = new byte[rtcpOut.getUsed()];
			rtcpOut.copyInto(0, rtcpActual, 0, rtcpActual.length);

			final byte[] rtcpExpected = Common.buildExpectedSrtcpPacket(
					rndPlainRtcp,
					rtcpKeys,
					rndSsrcRtcp,
					rndStateStartIndex,
					rtcpHdrLen
				);

			assertArrayEquals(rtcpExpected, rtcpActual, "RTCP mismatch with non-zero index at round=" + i);
			assertEquals((before + 1) & 0x7FFFFFFF, after, "RTCP index increment mismatch at round=" + i);
		}
	}

	@Test
	void rtcp_index_should_wrap_at_31_bits_boundary() throws Exception {
		final RtspProtoIdXsrc hdSsrc = RtspProtoIdXsrc.of(0x55667788L);

		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(hdSsrc);

		final SrtcpContextOutbound ctx = Common.createSrtcpCtxOutboundDefault(hdSsrc);
		Common.srtcpCtxOutboundInjectStateRtcpIndex(ctx, 0x7FFFFFFE);
		Common.srtcpCtxInjectKeys(ctx, rtcpKeys);

		final int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		final byte[] plainRtcp = new byte[hdrLen + 8];
		Arrays.fill(plainRtcp, (byte) 0x5A);

		final BufferExt in1 = new BufferExt();
		in1.copyOf(plainRtcp);
		final BufferExt out1 = new BufferExt();

		final BufferExt in2 = new BufferExt();
		in2.copyOf(plainRtcp);
		final BufferExt out2 = new BufferExt();

		// 1st call uses index 0x7FFFFFFE, then increments to 0x7FFFFFFF
		ctx.protectRtcpSrCompound(in1, hdSsrc, out1);
		assertEquals(0x7FFFFFFF, Common.srtcpCtxOutboundReadStateRtcpIndex(ctx));

		// 2nd call uses index 0x7FFFFFFF, then wraps to 0
		ctx.protectRtcpSrCompound(in2, hdSsrc, out2);
		assertEquals(0, Common.srtcpCtxOutboundReadStateRtcpIndex(ctx));

		final byte[] actual1 = new byte[out1.getUsed()];
		out1.copyInto(0, actual1, 0, actual1.length);
		final byte[] expected1 = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				hdSsrc,
				0x7FFFFFFE,
				hdrLen
			);
		assertArrayEquals(expected1, actual1, "SRTCP packet mismatch for index 0x7FFFFFFE");

		final byte[] actual2 = new byte[out2.getUsed()];
		out2.copyInto(0, actual2, 0, actual2.length);
		final byte[] expected2 = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				hdSsrc,
				0x7FFFFFFF,
				hdrLen
			);
		assertArrayEquals(expected2, actual2, "SRTCP packet mismatch for index 0x7FFFFFFF");
	}

}
