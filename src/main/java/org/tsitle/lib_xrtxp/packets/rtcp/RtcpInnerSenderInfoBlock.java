package org.tsitle.lib_xrtxp.packets.rtcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.NtpTimestamp;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import java.nio.ByteBuffer;
import java.time.Instant;

public final class RtcpInnerSenderInfoBlock implements Cloneable {

	/** Size of the RTCP packet payload */
	public static final int PAYLOAD_SIZE = 20;

	/** NTP timestamp - most significant word (seconds relative to 0h UTC on 1 January 1900, integer part, 32 bits) */
	private final int bdNtpTsMsw;
	/** NTP timestamp - least significant word (seconds relative to 0h UTC on 1 January 1900, fractional part, 32 bits) */
	private final int bdNtpTsLsw;
	/** RTP timestamp (32 bits) */
	private @NonNull RtspProtoRtpTimestamp bdRtpTs;
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
				@NonNull NtpTimestamp ntpTsFull,
				@NonNull RtspProtoRtpTimestamp rtpTs,
				int sendersPktCount,
				int sendersOctCount
			) {
		this(
				ntpTsFull.getSeconds32bit().orElseThrow().intValue(),
				ntpTsFull.getFraction32bit().orElseThrow().intValue(),
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
				@NonNull RtspProtoRtpTimestamp rtpTs,
				int sendersPktCount,
				int sendersOctCount
			) {
		this.bdNtpTsMsw = ntpTsMsw;
		this.bdNtpTsLsw = ntpTsLsw;
		this.bdRtpTs = rtpTs.clone();
		this.bdSendersPktCount = sendersPktCount;
		this.bdSendersOctCount = sendersOctCount;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public int getNtpTsMsw() { return bdNtpTsMsw; }
	@SuppressWarnings("unused")
	public int getNtpTsLsw() { return bdNtpTsLsw; }
	public @NonNull NtpTimestamp getNtpTsFull() {
		return NtpTimestamp.withOverflow(
				Integer.toUnsignedLong(bdNtpTsMsw),
				Integer.toUnsignedLong(bdNtpTsLsw)
			);
	}
	@SuppressWarnings("unused")
	public @NonNull Instant getNtpTsAsInstant() { return getNtpTsFull().toInstant(); }
	@SuppressWarnings("unused")
	public @NonNull RtspProtoRtpTimestamp getRtpTs() { return bdRtpTs.clone(); }
	@SuppressWarnings("unused")
	public int getSendersPktCount() { return bdSendersPktCount; }
	@SuppressWarnings("unused")
	public int getSendersOctCount() { return bdSendersOctCount; }

	public void appendToBuffer(@NonNull ByteBuffer bb) {
		bb.putInt(bdNtpTsMsw);
		bb.putInt(bdNtpTsLsw);
		bb.putInt(bdRtpTs.getTs32bit().orElse(0L).intValue());
		bb.putInt(bdSendersPktCount);
		bb.putInt(bdSendersOctCount);
	}

	public static @NonNull RtcpInnerSenderInfoBlock decodeFromBuffer(@NonNull ByteBuffer bb) {
		int tmpNtpTsMsw = bb.getInt();
		int tmpNtpTsLsw = bb.getInt();
		int tmpRtpTsInt = bb.getInt();
		int tmpSenderPktCount = bb.getInt();
		int tmpSenderOctCount = bb.getInt();

		RtspProtoRtpTimestamp tmpTs;
		try {
			tmpTs = RtspProtoRtpTimestamp.of(Integer.toUnsignedLong(tmpRtpTsInt));
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
			tmpTs = RtspProtoRtpTimestamp.ofZero();
		}
		return new RtcpInnerSenderInfoBlock(
				tmpNtpTsMsw,
				tmpNtpTsLsw,
				tmpTs,  // rtpTs
				tmpSenderPktCount,
				tmpSenderOctCount
			);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"NTPTS: " + getNtpTsFull().toInstant() +
				", RTPTS: " + bdRtpTs +
				", SendersPktCount: " + Integer.toUnsignedString(bdSendersPktCount) +
				", SendersOctCount: " + Integer.toUnsignedString(bdSendersOctCount) +
				"]";
	}

	@Override
	public @NonNull RtcpInnerSenderInfoBlock clone() {
		try {
			RtcpInnerSenderInfoBlock cloned = (RtcpInnerSenderInfoBlock)super.clone();
			cloned.bdRtpTs = bdRtpTs.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
