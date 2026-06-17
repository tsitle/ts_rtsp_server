package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdp;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;

public interface RtspProtoSdpConsumerInterface {

	/**
	 * Parses a Session Description (SDP) from a DESCRIBE response.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param inputSdp Input for the SDP lines
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void parseSdpFromDescribe(
			@NonNull RtspProtoDataCntSdp inputSdp
		) throws RtspProtoSdpException;

	/**
	 * Parses an updated Session Description (SDP) from an ANNOUNCE request.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param inputSdp Input for the SDP lines
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void parseUpdatedSdpFromAnnounce(
			@NonNull RtspProtoDataCntSdp inputSdp
		) throws RtspProtoSdpException;

}
