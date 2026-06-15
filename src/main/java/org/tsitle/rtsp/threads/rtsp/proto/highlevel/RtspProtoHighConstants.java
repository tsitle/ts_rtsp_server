package org.tsitle.rtsp.threads.rtsp.proto.highlevel;

import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;

import java.util.Set;

public final class RtspProtoHighConstants {

	private RtspProtoHighConstants() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** URL Query Parameter for forcing the usage of SRTP/SRTCP (if the parameter value is '1') */
	public static final String URL_QUERY_PARAM_SRTP = "srtp";

	/** Prefix for Sub-Stream IDs as publicized over SDP */
	public static final String DEFAULT_SUBSTREAM_ID_PREFIX = "substreamid";

	/** RTSP Authorization Realm */
	public static final String DEFAULT_RTSP_AUTH_REALM = "Realm_A1B2C3D4E5F6_G7H8I9_J10K11";

	/** RTSP message types that the local host supports for incoming requests */
	public static final Set<RtspProtoMessageType> LH_SUPPORTED_MESSAGE_TYPES_INCOMING = Set.of(
			RtspProtoMessageType.DESCRIBE,
			RtspProtoMessageType.GET_PARAMETER,
			RtspProtoMessageType.OPTIONS,
			RtspProtoMessageType.PAUSE,
			RtspProtoMessageType.PLAY,
			RtspProtoMessageType.REDIRECT,
			RtspProtoMessageType.SET_PARAMETER,
			RtspProtoMessageType.SETUP,
			RtspProtoMessageType.TEARDOWN
		);

	/**
	 * RTSP Session Timeout in seconds -- this value will be announced to the client.<br />
	 * If TCP transport is being used, the timeout will be ignored. Instead, the {@link org.tsitle.rtsp.threads.RtxpTcpReadWrite}
	 * instance will check for a timeout.
	 */
	public static final int DEFAULT_RTSP_SESSION_TIMEOUT = 20;

}
