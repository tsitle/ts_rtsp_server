package org.tsitle.rtsp.threads.rtsp.proto.sdp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspKeymgmtKmdsOutbound;

public interface SdpProducerInterface {

	/**
	 * Builds a Session Description (SDP) response for a DESCRIBE request.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param idInputSource Input Source
	 * @param serverIpOrName Server's IP address or hostname
	 * @param outputSdp Output for the SDP lines
	 * @throws RtspSdpException If an error occurs during SDP generation
	 */
	void buildSdpForDescribe(
			@NonNull RtspProtoIdInputSource idInputSource,
			@NonNull String serverIpOrName,
			@NonNull RtspProtoDataCntSdp outputSdp
		) throws RtspSdpException;

	/**
	 * Builds an updated Session Description (SDP) for an ANNOUNCE request.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param idInputSource Input Source
	 * @param serverIpOrName Server's IP address or hostname
	 * @param kmdsOutbound Optional Key Management Data for outbound RTP/SRTP packets
	 * @param outputSdp Output for the SDP lines
	 * @throws RtspSdpException If an error occurs during SDP generation
	 */
	void buildUpdatedSdpForAnnounce(
			@NonNull RtspProtoIdInputSource idInputSource,
			@NonNull String serverIpOrName,
			@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
			@NonNull RtspProtoDataCntSdp outputSdp
		) throws RtspSdpException;

}
