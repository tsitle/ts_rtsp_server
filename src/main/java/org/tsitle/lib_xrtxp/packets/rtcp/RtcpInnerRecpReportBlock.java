package org.tsitle.lib_xrtxp.packets.rtcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.NtpTimestamp;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;

/**
 * RTCP Reception Report Block.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.4.1">RFC-3550 Section 6.4.1</a>
 */
public final class RtcpInnerRecpReportBlock implements Cloneable {

	/** Size of the RTCP packet payload */
	public static final int PAYLOAD_SIZE = 24;
	private static final float DLSR_IN_SECONDS_FACTOR = 65536.0f;

	/** Item number - only informative */
	private final int itemNr;

	/** SSRC of the source stream (32 bits) */
	private final int bdSsrcSource;
	/** Raw value of the fraction of RTP data packets from sender lost since the previous RR packet was sent (8 bits) */
	private final byte bdFractionLostBy;
	/** The total number of RTP data packets from sender that have been lost since the beginning of reception (24 bits) */
	private int bdCumLost;
	/** Rollover Counter for the highest sequence number received (16 bits) */
	private final short bdExtHighestSeqNr_rolloverCounter;
	/** Sequence number for the highest sequence number received (16 bits) */
	private final short bdExtHighestSeqNr_sequNr;
	/** Interarrival jitter (32 bits) */
	private final int bdJitter;
	/** Last SR (middle 32 bits of the last SR's NTP timestamp that the server (aka Sender) has sent to the client (aka Receiver), 32 bits) */
	private final int bdLsr;
	/** Delay since last SR in seconds*65536 (32 bits) */
	private final int bdDlsr;

	/**
	 * Constructor.
	 * @param itemNr Item number
	 * @param ssrcSource SSRC of the source stream
	 * @param fractionLostRaw Fraction loss
	 * @param cumLost Cumulative lost
	 * @param highSeqNrRolloverCounter Highest sequence number rollover counter
	 * @param highSeqNrSequNr Highest sequence number
	 * @param jitter Jitter
	 * @param lsr Last SR
	 * @param dlsrInSeconds Delay since last SR in seconds
	 */
	public RtcpInnerRecpReportBlock(
				int itemNr,
				int ssrcSource,
				byte fractionLostRaw,
				int cumLost,
				short highSeqNrRolloverCounter,
				short highSeqNrSequNr,
				int jitter,
				int lsr,
				float dlsrInSeconds
			) {
		this.itemNr = itemNr;
		this.bdSsrcSource = ssrcSource;
		this.bdFractionLostBy = fractionLostRaw;
		this.bdCumLost = (cumLost & 0x00FF_FFFF);
		this.bdExtHighestSeqNr_rolloverCounter = highSeqNrRolloverCounter;
		this.bdExtHighestSeqNr_sequNr = highSeqNrSequNr;
		this.bdJitter = jitter;
		this.bdLsr = lsr;
		this.bdDlsr = (int)(dlsrInSeconds * DLSR_IN_SECONDS_FACTOR);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getItemNr() { return itemNr; }
	@SuppressWarnings("unused")
	public int getSsrcSource() { return bdSsrcSource; }
	/**
	 * Returns the raw value of the fraction of RTP data packets from sender lost since the previous RR packet was sent
	 * @return Fraction lost
	 */
	@SuppressWarnings("unused")
	public byte getFractionLostRaw() { return bdFractionLostBy; }
	/**
	 * Returns the fraction of RTP data packets from sender lost since the previous RR packet was sent
	 * @return Fraction lost (range 0.0 .. 1.0)
	 */
	public float getFractionLostPercent() { return ((float)(bdFractionLostBy & 0xFF) / 256.0f); }
	@SuppressWarnings("unused")
	public int getCumLost() { return bdCumLost; }
	@SuppressWarnings("unused")
	public short getExtHighestSeqNr_rolloverCounter() { return bdExtHighestSeqNr_rolloverCounter; }
	@SuppressWarnings("unused")
	public short getExtHighestSeqNr_sequNr() { return bdExtHighestSeqNr_sequNr; }
	@SuppressWarnings("unused")
	public int getJitter() { return bdJitter; }
	@SuppressWarnings("unused")
	public int getLsr() { return bdLsr; }
	/** Returns the delay since last SR in seconds */
	@SuppressWarnings("unused")
	public float getDlsrInSeconds() { return ((float)bdDlsr / DLSR_IN_SECONDS_FACTOR); }

	/**
	 * Get the round-trip time in milliseconds.
	 * @param receivedAt Instant when the packet was received
	 * @return Round-trip time in milliseconds
	 */
	@SuppressWarnings("unused")
	public Optional<Long> getRoundTripTimeMillis(@NonNull Instant receivedAt) {
		if (bdLsr == 0L) {
			return Optional.empty();
		}
		/*
		 * LSR: the timestamp from the last SR packet (middle 32 bits of NTP timestamp)
		 * DLSR: how long the receiver waited (delay in seconds × 65536)
		 * A: Instant when packet was received, in NTP time
		 *
		 * RTT = A − LSR − DLSR
		 */
		long rcvdAtNtp32b = instantTo32bitNtpTimestamp(receivedAt);

		long lsrLong = Integer.toUnsignedLong(bdLsr);
		long dlsrLong = Integer.toUnsignedLong(bdDlsr);

		// all values are modulo 2^32 (wrap-around is valid)
		long rtt32b = ((rcvdAtNtp32b - lsrLong - dlsrLong) & 0xFFFF_FFFFL);

		// Convert 16.16 fixed-point seconds to milliseconds
		long rttMillis = Math.round(((double)rtt32b * 1000.0) / DLSR_IN_SECONDS_FACTOR);
		return Optional.of(rttMillis);
	}

	public void appendToBuffer(@NonNull ByteBuffer bb) {
		bb.putInt(bdSsrcSource);
		bb.put(bdFractionLostBy);
		int tmpCumLost = (bdCumLost & 0x00FF_FFFF);  // convert to 24-bit signed
		bb.put((byte)((tmpCumLost >> 16) & 0xFF));
		bb.put((byte)((tmpCumLost >> 8) & 0xFF));
		bb.put((byte)(tmpCumLost & 0xFF));
		bb.putShort(bdExtHighestSeqNr_rolloverCounter);
		bb.putShort(bdExtHighestSeqNr_sequNr);
		bb.putInt(bdJitter);
		bb.putInt(bdLsr);
		bb.putInt(bdDlsr);
	}

	public static @NonNull RtcpInnerRecpReportBlock decodeFromBuffer(int itemNr, @NonNull ByteBuffer bb) {
		RtcpInnerRecpReportBlock tmpBlock = new RtcpInnerRecpReportBlock(
				itemNr,
				bb.getInt(),  // ssrc
				bb.get(),  // fraction lost
				((bb.get() << 16) & 0xFF0000) | ((bb.get() << 8) & 0xFF00) | (bb.get() & 0xFF),  // cumulative lost
				bb.getShort(),  // highest seq nr rollover counter
				bb.getShort(),  // highest seq nr
				bb.getInt(),  // jitter
				bb.getInt(),  // last SR
				(float)bb.getInt() / DLSR_IN_SECONDS_FACTOR  // delay since last SR
			);
		// sign-extend the 24-bit value back to 32 bits
		tmpBlock.bdCumLost = ((tmpBlock.bdCumLost << 8) >> 8);
		//
		return tmpBlock;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"ItemNr: " + itemNr +
				", SSRC: 0x" + String.format("%08X", bdSsrcSource) +
				", Fraction Lost %: " + String.format("%.2f", getFractionLostPercent() * 100.0f) +
				", Cumulative Lost: " + bdCumLost +
				", Highest Seq Num: ro=" + Integer.toUnsignedString((int)bdExtHighestSeqNr_rolloverCounter & 0xFFFF) +
					"/sn=" + Integer.toUnsignedString((int)bdExtHighestSeqNr_sequNr & 0xFFFF) +
				", Jitter: " + Integer.toUnsignedString(bdJitter) +
				", LSR: " + Integer.toUnsignedString(bdLsr) +
				", DLsr: " + String.format("%.3fs", getDlsrInSeconds()) +
				"]";
	}

	@Override
	public @NonNull RtcpInnerRecpReportBlock clone() {
		try {
			return (RtcpInnerRecpReportBlock)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * This is only for use in RTCP packets, where the 32-bit timestamp is used.
	 * @param javaTs Instant
	 * @return 32-bit NTP timestamp (top 16 bits: integer seconds, bottom 16 bits: fractional seconds)
	 */
	private static long instantTo32bitNtpTimestamp(@NonNull Instant javaTs) {
		long unixSeconds = javaTs.getEpochSecond();
		long ntpSeconds = unixSeconds + NtpTimestamp.NTP_EPOCH_OFFSET_SECONDS;

		// NTP fractional part: 32-bit fraction of a second
		long nanos = javaTs.getNano();
		long ntpFraction32 = (nanos * 0x1_0000_0000L) / 1_000_000_000L;

		// Middle 32 bits = (low 16 bits of the seconds) << 16 | (high 16 bits of the fraction)
		long middle32 =
				((ntpSeconds & 0xFFFFL) << 16) |
						((ntpFraction32 >>> 16) & 0xFFFFL);

		return middle32 & 0xFFFF_FFFFL;
	}

}
