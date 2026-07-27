package org.tsitle.lib_xrtxp.packets.rtcp;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.types.TimestampEpoch;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrRoundTripTimeTest {

	@Test
	void decodeRoundTripTime2_ffmpeg() {
		final int bdLsr = 0x194a15df;
		final int bdDlsr = 0x00002f68;
		final double DLSR_IN_SECONDS_FACTOR = 65536.0;

		RtcpInnerRecpReportBlock rrBlock = new RtcpInnerRecpReportBlock(
				1,
				0x4005b90d,
				(byte)0x00,
				0x00000000,
				(short)0x0000,
				(short)0x247b,
				0x00000009,
				bdLsr,
				bdDlsr / DLSR_IN_SECONDS_FACTOR
			);
		TimestampEpoch receivedAt = TimestampEpoch.ofEpochNsUnsigned64bit(1785174730272483685L);

		Optional<Long> tmpOptRttMs = rrBlock.getRoundTripTimeMillis(receivedAt);

		assertTrue(tmpOptRttMs.isPresent());
		assertEquals(2L, tmpOptRttMs.get());
	}

}
