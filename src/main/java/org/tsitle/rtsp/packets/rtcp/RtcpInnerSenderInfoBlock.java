package org.tsitle.rtsp.packets.rtcp;

import org.tsitle.rtsp.helpers.NtpTimestampHelper;

import java.nio.ByteBuffer;
import java.time.Instant;

public class RtcpInnerSenderInfoBlock implements Cloneable {

	/** Size of the RTCP packet payload */
	public static final int PAYLOAD_SIZE = 20;

	/** NTP timestamp - most significant word (seconds relative to 0h UTC on 1 January 1900, integer part, 32 bits) */
	private final int bdNtpTsMsw;
	/** NTP timestamp - least significant word (seconds relative to 0h UTC on 1 January 1900, fractional part, 32 bits) */
	private final int bdNtpTsLsw;
	/** RTP timestamp (32 bits) */
	private final int bdRtpTs;
	/** Sender's packet count (32 bits) */
	private final int bdSendersPktCount;
	/** Sender's octet count (32 bits) */
	private final int bdSendersOctCount;

	/**
	 * Constructor.
	 * @param ntpTsFull NTP timestamp
	 * @param rtpTs RTP timestamp
	 * @param sendersPktCount Sender's packet count
	 * @param sendersOctCount Sender's octet count
	 */
	public RtcpInnerSenderInfoBlock(
				long ntpTsFull,
				int rtpTs,
				int sendersPktCount,
				int sendersOctCount
			) {
		this(
				(int)(ntpTsFull >>> 32),
				(int)(ntpTsFull & 0xFFFF_FFFFL),
				rtpTs,
				sendersPktCount,
				sendersOctCount
			);
	}

	/**
	 * Constructor.
	 * @param ntpTsMsw NTP timestamp - most significant word (integer part)
	 * @param ntpTsLsw NTP timestamp - least significant word (fractional part)
	 * @param rtpTs RTP timestamp
	 * @param sendersPktCount Sender's packet count
	 * @param sendersOctCount Sender's octet count
	 */
	public RtcpInnerSenderInfoBlock(
				int ntpTsMsw,
				int ntpTsLsw,
				int rtpTs,
				int sendersPktCount,
				int sendersOctCount
			) {
		this.bdNtpTsMsw = ntpTsMsw;
		this.bdNtpTsLsw = ntpTsLsw;
		this.bdRtpTs = rtpTs;
		this.bdSendersPktCount = sendersPktCount;
		this.bdSendersOctCount = sendersOctCount;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public int getNtpTsMsw() { return bdNtpTsMsw; }
	@SuppressWarnings("unused")
	public int getNtpTsLsw() { return bdNtpTsLsw; }
	public long getNtpTsFull() { return (((long)bdNtpTsMsw << 32) | bdNtpTsLsw); }
	public Instant getNtpTsInstant() { return NtpTimestampHelper.ntpTimestampToInstant(getNtpTsFull()); }
	@SuppressWarnings("unused")
	public int getRtpTs() { return bdRtpTs; }
	@SuppressWarnings("unused")
	public int getSendersPktCount() { return bdSendersPktCount; }
	@SuppressWarnings("unused")
	public int getSendersOctCount() { return bdSendersOctCount; }

	public void appendToBuffer(ByteBuffer bb) {
		bb.putInt(bdNtpTsMsw);
		bb.putInt(bdNtpTsLsw);
		bb.putInt(bdRtpTs);
		bb.putInt(bdSendersPktCount);
		bb.putInt(bdSendersOctCount);
	}

	public static RtcpInnerSenderInfoBlock decodeFromBuffer(ByteBuffer bb) {
		return new RtcpInnerSenderInfoBlock(
				bb.getInt(),  // ntpTsMsw
				bb.getInt(),  // ntpTsLsw
				bb.getInt(),  // rtpTs
				bb.getInt(),  // sendersPktCount
				bb.getInt()  // sendersOctCount
			);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"NTPTS: " + NtpTimestampHelper.ntpTimestampToInstant(getNtpTsFull()) +
				", RTPTS: " + Integer.toUnsignedString(bdRtpTs) +
				", SendersPktCount: " + Integer.toUnsignedString(bdSendersPktCount) +
				", SendersOctCount: " + Integer.toUnsignedString(bdSendersOctCount) +
				"]";
	}

	@Override
	public RtcpInnerSenderInfoBlock clone() {
		try {
			return (RtcpInnerSenderInfoBlock)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
