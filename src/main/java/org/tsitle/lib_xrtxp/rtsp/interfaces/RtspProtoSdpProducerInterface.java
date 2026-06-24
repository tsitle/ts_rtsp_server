package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoAdSettingsStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdsStream;

public interface RtspProtoSdpProducerInterface {

	/**
	 * Builds a Session Description (SDP) response for a DESCRIBE request.<br />
	 * (for some examples see
	 *   <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 *   <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param cfgSubStreamIdPrefix Prefix for Sub-Stream IDs
	 * @param requireSrtp Whether SRTP is required
	 * @param idInputSource Input Source
	 * @param serverIpOrName Server's IP address or hostname
	 * @param clientUserAgent Client's User-Agent string
	 * @param clientIpAddr Client's IP address
	 * @param outputSdp Output for the SDP lines
	 * @param outputAdStreamSett Output for the stream settings
	 * @param outputKmdsOutbound Output for the KMDs
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void buildSdpForDescribe(
				@NonNull String cfgSubStreamIdPrefix,
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataCntSdpRaw outputSdp,
				@NonNull RtspProtoAdSettingsStream outputAdStreamSett,
				@NonNull RtspProtoKmdsStream outputKmdsOutbound
			) throws RtspProtoSdpException;

	/**
	 * Builds an updated Session Description (SDP) for an ANNOUNCE request.<br />
	 * (for some examples see
	 *   <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 *   <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param requireSrtp Whether SRTP is required
	 * @param idInputSource Input Source
	 * @param serverIpOrName Local host's IP address or hostname
	 * @param clientUserAgent Remote host's User-Agent string
	 * @param clientIpAddr Remote host's IP address
	 * @param inputAdStreamSett Input for the stream settings
	 * @param inputKmdsOutbound Optional Key Management Data for outbound RTP/SRTP packets
	 * @param outputSdp Output for the SDP lines
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void buildUpdatedSdpForAnnounce(
				boolean requireSrtp,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIpAddr serverIpOrName,
				@NonNull String clientUserAgent,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoAdSettingsStream inputAdStreamSett,
				@Nullable RtspProtoKmdsStream inputKmdsOutbound,
				@NonNull RtspProtoDataCntSdpRaw outputSdp
			) throws RtspProtoSdpException;

}
