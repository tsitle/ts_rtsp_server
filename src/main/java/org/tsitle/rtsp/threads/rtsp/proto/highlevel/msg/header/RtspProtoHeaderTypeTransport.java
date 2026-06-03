package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

public class RtspProtoHeaderTypeTransport {

	/** Client's UDP port for inbound RTP packets */
	private int tpClientUdpPortRtp = -1;
	/** Client's UDP port for inbound/outbound RTCP packets */
	private int tpClientUdpPortRtcp = -1;
	/** Server's UDP port for outbound RTP packets */
	private int tpServerUdpPortRtp = -1;
	/** Server's UDP port for inbound/outbound RTCP packets */
	private int tpServerUdpPortRtcp = -1;
	/** Client's TCP channel for inbound RTP packets */
	private int tpClientTcpChannRtp = -1;
	/** Client's TCP channel for inbound/outbound RTCP packets */
	private int tpClientTcpChannRtcp = -1;
	/** Transport type protocol (true: UDP, false: TCP) */
	public boolean tpIsUdp = false;
	/** Transport casting type (true: unicast, false: multicast) */
	public boolean tpIsUnicast = false;
	/** Transport interleaved mode (true: interleaved (requires TCP), false: separate (requires UDP)) */
	public boolean tpIsInterleaved = false;
	/** Transport encryption type (true: SRTP/SRTCP, false: plain RTP/RTCP) */
	public boolean tpIsEncr = false;

	/** Destination IP address or hostname */
	public @NonNull String tpDestIpOrHost = "";
	/** Source IP address or hostname */
	public @NonNull String tpSourceIpOrHost = "";

	/** SSRC identifier for RTP/RTCP packets */
	private long tpSsrcId32bit = -1L;

	public void setClientUdpPortRtp16bit(int portNumber16bit) throws IllegalArgumentException {
		validatePortNumber("tpClientUdpPortRtp", portNumber16bit);
		this.tpClientUdpPortRtp = portNumber16bit;
	}

	public Optional<Short> getClientUdpPortRtp16bit() {
		return (tpClientUdpPortRtp < 0L ? Optional.empty() : Optional.of((short)tpClientUdpPortRtp));
	}

	public void setClientUdpPortRtcp16bit(int portNumber16bit) throws IllegalArgumentException {
		validatePortNumber("tpClientUdpPortRtcp", portNumber16bit);
		this.tpClientUdpPortRtcp = portNumber16bit;
	}

	public Optional<Short> getClientUdpPortRtcp16bit() {
		return (tpClientUdpPortRtcp < 0L ? Optional.empty() : Optional.of((short)tpClientUdpPortRtcp));
	}

	public void setServerUdpPortRtp16bit(int portNumber16bit) throws IllegalArgumentException {
		validatePortNumber("tpServerUdpPortRtp", portNumber16bit);
		this.tpServerUdpPortRtp = portNumber16bit;
	}

	public Optional<Short> getServerUdpPortRtp16bit() {
		return (tpServerUdpPortRtp < 0L ? Optional.empty() : Optional.of((short)tpServerUdpPortRtp));
	}

	public void setServerUdpPortRtcp16bit(int portNumber16bit) throws IllegalArgumentException {
		validatePortNumber("tpServerUdpPortRtcp", portNumber16bit);
		this.tpServerUdpPortRtcp = portNumber16bit;
	}

	public Optional<Short> getServerUdpPortRtcp16bit() {
		return (tpServerUdpPortRtcp < 0L ? Optional.empty() : Optional.of((short)tpServerUdpPortRtcp));
	}

	public void setClientTcpChannRtp16bit(int tcpChann16bit) throws IllegalArgumentException {
		validateTcpChannel("tpClientTcpChannRtp", tcpChann16bit);
		this.tpClientTcpChannRtp = tcpChann16bit;
	}

	public Optional<Short> getClientTcpChannRtp16bit() {
		return (tpClientTcpChannRtp < 0L ? Optional.empty() : Optional.of((short)tpClientTcpChannRtp));
	}

	public void setClientTcpChannRtcp16bit(int tcpChann16bit) throws IllegalArgumentException {
		validateTcpChannel("tpClientTcpChannRtcp", tcpChann16bit);
		this.tpClientTcpChannRtcp = tcpChann16bit;
	}

	public Optional<Short> getClientTcpChannRtcp16bit() {
		return (tpClientTcpChannRtcp < 0L ? Optional.empty() : Optional.of((short)tpClientTcpChannRtcp));
	}

	public void setSsrcId32bit(long ssrc32bit) throws IllegalArgumentException {
		validateSsrc("tpSsrcId32bit", ssrc32bit);
		this.tpSsrcId32bit = ssrc32bit;
	}

	public void clearSsrcId() {
		this.tpSsrcId32bit = -1L;
	}

	public Optional<Integer> getSsrcId32bit() {
		return (tpSsrcId32bit < 0L ? Optional.empty() : Optional.of((int)tpSsrcId32bit));
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				"tpIsUdp=" + tpIsUdp +
				(tpIsUdp ? ", " + optionalShortToStr("tpClientUdpPortRtp", getClientUdpPortRtp16bit()) : "") +
				(tpIsUdp ? ", " + optionalShortToStr("tpClientUdpPortRtcp", getClientUdpPortRtcp16bit()) : "") +
				(tpIsUdp ? ", " + optionalShortToStr("tpServerUdpPortRtp", getServerUdpPortRtp16bit()) : "") +
				(tpIsUdp ? ", " + optionalShortToStr("tpServerUdpPortRtcp", getServerUdpPortRtcp16bit()) : "") +
				(tpIsUdp ? "" : ", " + optionalShortToStr("tpClientTcpChannRtp", getClientTcpChannRtp16bit())) +
				(tpIsUdp ? "" : ", " + optionalShortToStr("tpClientTcpChannRtcp", getClientTcpChannRtcp16bit())) +
				", tpIsUnicast=" + tpIsUnicast +
				(tpIsUdp ? "" : ", tpIsInterleaved=" + tpIsInterleaved) +
				", tpIsEncr=" + tpIsEncr +
				", tpDestIpOrHost='" + tpDestIpOrHost + "'" +
				", tpSourceIpOrHost='" + tpSourceIpOrHost + "'" +
				", " + optionalIntToStr("tpSsrcId", getSsrcId32bit()) +
				"]";
	}

	private static void validatePortNumber(@NonNull String desc, int port) {
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException(desc + " must be between 1 and 65535, got: " + port);
		}
	}

	private static void validateTcpChannel(@NonNull String desc, int channel) {
		if (channel < 0 || channel > 65535) {
			throw new IllegalArgumentException(desc + " must be between 0 and 65535, got: " + channel);
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static void validateSsrc(@NonNull String desc, long ssrc) {
		if (ssrc < 1L || ssrc > 0xFFFFFFFFL) {
			throw new IllegalArgumentException(desc + " must be between 1 and 0xFFFFFFFF, got: " + ssrc);
		}
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private static @NonNull String optionalShortToStr(@NonNull String desc, @NonNull Optional<Short> value) {
		return desc + "=" + (value.isPresent() ? Short.toUnsignedInt(value.get()) : "unset");
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
		return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
	}

}
