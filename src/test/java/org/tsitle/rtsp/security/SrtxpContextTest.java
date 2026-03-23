package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketSR;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SrtxpContextTest {

	@Test
	void srtpContext_protectRtp_should_encrypt_payload_and_append_valid_auth_tag() throws Exception {
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();

		final SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtpKeys(ctx, rtpKeys, 0L);

		final short seqNr = 0x1234;
		final int ssrc = 0xDEC0ADDE;

		final byte[] header = new byte[] {
				(byte) 0x80, (byte) 0x60, // V=2,P=0,X=0,CC=0 ; M=0, PT=96
				(byte) (seqNr >>> 8), (byte) seqNr,
				0x11, 0x22, 0x33, 0x44,   // RTP timestamp
				(byte) (ssrc >>> 24), (byte) (ssrc >>> 16), (byte) (ssrc >>> 8), (byte) ssrc
			};
		final byte[] payload = Common.HEX.parseHex("00112233445566778899AABBCCDDEEFF00010203");
		final byte[] plainPacket = Common.concat(header, payload);

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainPacket);
		final BufferExt outBuf = new BufferExt();

		ctx.protectRtp(plainBuf, false, false, seqNr, ssrc, outBuf);

		final byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		final byte[] expected = Common.buildExpectedSrtpPacket(
				plainPacket,
				rtpKeys,
				seqNr,
				ssrc,
				0L
			);

		assertArrayEquals(expected, actual, "SRTP packet mismatch");
		assertFalse(Arrays.equals(
				Arrays.copyOfRange(plainPacket, 12, plainPacket.length),
				Arrays.copyOfRange(actual, 12, 12 + payload.length)
			), "Encrypted payload should differ from plaintext payload");
	}

	@Test
	void srtpContext_protectRtcpSrCompound_should_encrypt_payload_and_append_index_and_auth_tag() throws Exception {
		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp();

		final SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtcpKeys(ctx, rtcpKeys, 0);

		final int ssrc = 0xDEC0ADDE;
		final int hdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

		final byte[] plainRtcp = new byte[hdrLen + 12];
		for (int i = 0; i < plainRtcp.length; i++) {
			plainRtcp[i] = (byte) (0xA0 + i);
		}

		final BufferExt plainBuf = new BufferExt();
		plainBuf.copyOf(plainRtcp);
		final BufferExt outBuf = new BufferExt();

		final int beforeIndex = Common.getPrivateInt(ctx, "ctxStateRtcpIndex");
		ctx.protectRtcpSrCompound(plainBuf, ssrc, outBuf);
		final int afterIndex = Common.getPrivateInt(ctx, "ctxStateRtcpIndex");

		final byte[] actual = new byte[outBuf.getUsed()];
		outBuf.copyInto(0, actual, 0, actual.length);

		final byte[] expected = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				ssrc,
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
	void srtpContext_fuzz_randomized_rtp_and_rtcp_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 200;
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();
		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp();

		for (int i = 0; i < rounds; i++) {
			final byte[] rndMasterKey = new byte[16];
			final byte[] rndMasterSalt = new byte[14];
			rnd.nextBytes(rndMasterKey);
			rnd.nextBytes(rndMasterSalt);

			// ---------------- RTP ----------------
			final SrtxpContext rtpCtx = new SrtxpContext();
			Common.srtpCtxInjectRtpKeys(rtpCtx, rtpKeys, 0L);

			final short rndSeqNr = (short)rnd.nextInt(0x10000);
			final int rndSsrcRtp = rnd.nextInt();

			final int rndRtpPayloadLen = rnd.nextInt(1, 400);
			final byte[] rndRtpPayload = new byte[rndRtpPayloadLen];
			rnd.nextBytes(rndRtpPayload);

			final byte[] rtpHeader = new byte[] {
					(byte) 0x80, (byte) 0x60,
					(byte) (rndSeqNr >>> 8), (byte) rndSeqNr,
					(byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256),
					(byte) (rndSsrcRtp >>> 24), (byte) (rndSsrcRtp >>> 16), (byte) (rndSsrcRtp >>> 8), (byte) rndSsrcRtp
				};
			final byte[] plainRtp = Common.concat(rtpHeader, rndRtpPayload);

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
			final SrtxpContext rtcpCtx = new SrtxpContext();
			Common.srtpCtxInjectRtcpKeys(rtcpCtx, rtcpKeys, 0);

			final int rndSsrcRtcp = rnd.nextInt();
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
					Common.getPrivateInt(rtcpCtx, "ctxStateRtcpIndex"),
					"RTCP index mismatch at round=" + i
				);
		}
	}

	@Test
	void srtpContext_fuzz_randomized_with_non_zero_roc_and_srtcp_index_should_match_reference() throws Exception {
		final SecureRandom rnd = new SecureRandom();
		final int rounds = 150;
		final SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp();
		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp();

		for (int i = 0; i < rounds; i++) {
			final byte[] rndMasterKey = new byte[16];
			final byte[] rndMasterSalt = new byte[14];
			rnd.nextBytes(rndMasterKey);
			rnd.nextBytes(rndMasterSalt);

			// ---------------- RTP with non-zero ROC ----------------
			final SrtxpContext rtpCtx = new SrtxpContext();
			final long rndStateRoc = rnd.nextInt(1, 100_000); // non-zero, keeps math simple and fast
			Common.srtpCtxInjectRtpKeys(rtpCtx, rtpKeys, rndStateRoc);

			final short rndSeqNr = (short)rnd.nextInt(0x10000);
			final int rndSsrcRtp = rnd.nextInt();

			final int rndRtpPayloadLen = rnd.nextInt(1, 500);
			final byte[] rndRtpPayload = new byte[rndRtpPayloadLen];
			rnd.nextBytes(rndRtpPayload);

			final byte[] rtpHeader = new byte[] {
					(byte) 0x80, (byte) 0x60,
					(byte) (rndSeqNr >>> 8), (byte) rndSeqNr,
					(byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256), (byte) rnd.nextInt(256),
					(byte) (rndSsrcRtp >>> 24), (byte) (rndSsrcRtp >>> 16), (byte) (rndSsrcRtp >>> 8), (byte) rndSsrcRtp
				};
			final byte[] plainRtp = Common.concat(rtpHeader, rndRtpPayload);

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
			final SrtxpContext rtcpCtx = new SrtxpContext();
			final int rndStateStartIndex = rnd.nextInt(1, 0x7FFFFFFF); // non-zero, 31-bit
			Common.srtpCtxInjectRtcpKeys(rtcpCtx, rtcpKeys, rndStateStartIndex);

			final int rndSsrcRtcp = rnd.nextInt();
			final int rtcpHdrLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
			final int rndRtcpPayloadLen = rnd.nextInt(1, 300);

			final byte[] rndPlainRtcp = new byte[rtcpHdrLen + rndRtcpPayloadLen];
			rnd.nextBytes(rndPlainRtcp);

			final BufferExt rtcpIn = new BufferExt();
			rtcpIn.copyOf(rndPlainRtcp);
			final BufferExt rtcpOut = new BufferExt();

			final int before = Common.getPrivateInt(rtcpCtx, "ctxStateRtcpIndex");
			rtcpCtx.protectRtcpSrCompound(rtcpIn, rndSsrcRtcp, rtcpOut);
			final int after = Common.getPrivateInt(rtcpCtx, "ctxStateRtcpIndex");

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
	void srtpContext_rtcp_index_should_wrap_at_31_bits_boundary() throws Exception {
		final SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp();

		final SrtxpContext ctx = new SrtxpContext();
		Common.srtpCtxInjectRtcpKeys(ctx, rtcpKeys, 0x7FFFFFFE);

		final int ssrc = 0x55667788;
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
		ctx.protectRtcpSrCompound(in1, ssrc, out1);
		assertEquals(0x7FFFFFFF, Common.getPrivateInt(ctx, "ctxStateRtcpIndex"));

		// 2nd call uses index 0x7FFFFFFF, then wraps to 0
		ctx.protectRtcpSrCompound(in2, ssrc, out2);
		assertEquals(0, Common.getPrivateInt(ctx, "ctxStateRtcpIndex"));

		final byte[] actual1 = new byte[out1.getUsed()];
		out1.copyInto(0, actual1, 0, actual1.length);
		final byte[] expected1 = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				ssrc,
				0x7FFFFFFE,
				hdrLen
			);
		assertArrayEquals(expected1, actual1, "SRTCP packet mismatch for index 0x7FFFFFFE");

		final byte[] actual2 = new byte[out2.getUsed()];
		out2.copyInto(0, actual2, 0, actual2.length);
		final byte[] expected2 = Common.buildExpectedSrtcpPacket(
				plainRtcp,
				rtcpKeys,
				ssrc,
				0x7FFFFFFF,
				hdrLen
			);
		assertArrayEquals(expected2, actual2, "SRTCP packet mismatch for index 0x7FFFFFFF");
	}

}
