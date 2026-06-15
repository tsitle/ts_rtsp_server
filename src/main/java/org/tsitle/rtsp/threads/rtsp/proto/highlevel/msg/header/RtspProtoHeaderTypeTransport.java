package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspTransportMode;

import java.util.Optional;

public class RtspProtoHeaderTypeTransport {

	public @NonNull RtspProtoDataCntSubStreamTp tpSubStream = new RtspProtoDataCntSubStreamTp();

	/** Destination IP address or hostname */
	public @NonNull String tpDestIpOrHost = "";
	/** Source IP address or hostname */
	public @NonNull String tpSourceIpOrHost = "";

	/** SSRC identifier for RTP/RTCP packets */
	private long tpSsrcId32bit = -1L;

	/** Mode (either PLAY or RECORD) */
	public @NonNull RtspTransportMode tpMode = RtspTransportMode.NONE;

	public void setSsrcId32bit(long ssrc32bit) throws RtspProtoNumberRangeException {
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
				"tpSubStream=" + tpSubStream +
				", tpDestIpOrHost='" + tpDestIpOrHost + "'" +
				", tpSourceIpOrHost='" + tpSourceIpOrHost + "'" +
				", " + optionalIntToStr("tpSsrcId", getSsrcId32bit()) +
				"]";
	}

	@SuppressWarnings("SameParameterValue")
	private static void validateSsrc(@NonNull String desc, long ssrc) throws RtspProtoNumberRangeException {
		if (ssrc < 1L || ssrc > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException(desc + " must be between 1 and 0xFFFFFFFF, got: " + ssrc);
		}
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
		return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
	}

}
