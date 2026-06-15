package org.tsitle.rtsp.threads.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntSdp;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;

public interface RtspProtoSdpConsumerInterface {

	/**
	 * Parses a Session Description (SDP) from a DESCRIBE response.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param inputSdp Input for the SDP lines
	 * @throws RtspSdpException If an error occurs during SDP generation
	 */
	void parseSdpFromDescribe(
			@NonNull RtspProtoDataCntSdp inputSdp
		) throws RtspSdpException;

	/**
	 * Parses an updated Session Description (SDP) from an ANNOUNCE request.<br />
	 * (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC-2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC-4317: SDP Offer/Answer Examples</a>)
	 * @param inputSdp Input for the SDP lines
	 * @throws RtspSdpException If an error occurs during SDP generation
	 */
	void parseUpdatedSdpFromAnnounce(
			@NonNull RtspProtoDataCntSdp inputSdp
		) throws RtspSdpException;

}
