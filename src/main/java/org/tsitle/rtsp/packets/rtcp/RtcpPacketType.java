package org.tsitle.rtsp.packets.rtcp;

import org.jspecify.annotations.NonNull;

/**
 * RTCP packet types.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-12.1">RFC-3550 Section 12.1</a>
 * and <a href="https://datatracker.ietf.org/doc/html/rfc4585">RFC-4585</a>
 */
public enum RtcpPacketType {

	/** Unknown */
	UNKNOWN((byte)0),
	/** Sender Report (SR) - RFC-3550 */
	SR((byte)200),
	/** Receiver Report (RR) - RFC-3550 */
	RR((byte)201),
	/** Source Description (SDES) - RFC-3550 */
	SDES((byte)202),
	/** Goodbye (BYE) - RFC-3550 */
	BYE((byte)203),
	/** Application-specific (APP) - RFC-3550 */
	APP((byte)204),
	/** Transport layer FB message (RTPFB) - RFC-4585 */
	RTPFB((byte)205),
	/** Payload-specific FB message (PSFB) - RFC-4585 */
	PSFB((byte)206),
	/** Non-standard: Custom Feedback (CFB) */
	CFB((byte)250);

	private final byte value;

	RtcpPacketType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}

	public static @NonNull RtcpPacketType of(byte value) {
		for (RtcpPacketType type : RtcpPacketType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return UNKNOWN;
	}

}
