package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspTransportMode;

public final class RtspProtoHeaderTypeTransport {

	public final @NonNull RtspProtoDataCntSubStreamTp tpSubStream = new RtspProtoDataCntSubStreamTp();

	/** Destination IP address or hostname */
	public @NonNull String tpDestIpOrHost = "";
	/** Source IP address or hostname */
	public @NonNull String tpSourceIpOrHost = "";

	/** SSRC identifier for RTP/RTCP packets */
	public final @NonNull RtspProtoIdXsrc tpSsrcId = new RtspProtoIdXsrc();

	/** Mode (either PLAY or RECORD) */
	public @NonNull RtspTransportMode tpMode = RtspTransportMode.NONE;

	@Override
	public @NonNull String toString() {
		return "[" +
				"tpSubStream=" + tpSubStream +
				", tpDestIpOrHost='" + tpDestIpOrHost + "'" +
				", tpSourceIpOrHost='" + tpSourceIpOrHost + "'" +
				", tpSsrcId=" + (tpSsrcId.isEmpty() ? "unset" : tpSsrcId.toHexString(true)) +
				"]";
	}

}
