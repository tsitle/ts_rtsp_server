package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.sdp.ArgsSdpForDescribeFromServer;
import org.tsitle.lib_xrtxp.rtsp.sdp.ArgsSrtxpSdpForAnnounceFromClient;
import org.tsitle.lib_xrtxp.rtsp.sdp.ArgsUpdatedSdpForAnnounceFromServer;

public interface RtspProtoSdpProducerInterface {

	/**
	 * Builds a Session Description (SDP) response from the server for a DESCRIBE request.
	 * @param args Arguments for building the SDP
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void buildSdpForDescribeFromServer(@NonNull ArgsSdpForDescribeFromServer args) throws RtspProtoSdpException;

	/**
	 * Builds an updated Session Description (SDP) for an ANNOUNCE request from the server.
	 * @param args Arguments for building the updated SDP
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void buildUpdatedSdpForAnnounceFromServer(@NonNull ArgsUpdatedSdpForAnnounceFromServer args)
			throws RtspProtoSdpException;

	/**
	 * Builds a Session Description (SDP) for an ANNOUNCE request from the client for the sole purpose of sending
	 * the client's (legacy SDES) SRTxP KMDs to the server.
	 * @param args Arguments for building the SDP
	 * @throws RtspProtoSdpException If an error occurs during SDP generation
	 */
	void buildSrtxpSdpForAnnounceFromClient(@NonNull ArgsSrtxpSdpForAnnounceFromClient args)
			throws RtspProtoSdpException;

}
