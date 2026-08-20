package org.tsitle.lib_xrtxp.rtsp.lowlevel.msg;

import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;

public final class RtspProtoLowMsgConstants {

	private RtspProtoLowMsgConstants() { }

	public static final RtspProtocolVersion DEFAULT_RTSP_PROTO_VERSION = RtspProtocolVersion.RTSP_V1;

	public static final String CRLF = "\r\n";

	/** Maximum length for RTSP Resource URLs */
	public static final int RTSP_MAX_RESOURCE_URL_LENGTH = 512;

	// RTSP Requests/Responses
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT = "client_port=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT = "server_port=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP = "destination=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP = "source=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC = "ssrc=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED = "interleaved=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_MODE = "mode=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT = "timeout=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL = "url=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SSRC = "ssrc=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ = "seq=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME = "rtptime=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER = "username=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM = "realm=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE = "nonce=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI = "uri=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP = "response=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO = "algorithm=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT = "prot=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI = "uri=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA = "data=";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP1 = "RTP/AVP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2 = "RTP/AVP/UDP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP1 = "RTP/SAVP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2 = "RTP/SAVP/UDP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP = "RTP/AVP/TCP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP = "RTP/SAVP/TCP";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST = "unicast";
	public static final String RTSP_RR_HEADER_PARAM_VAL_SET_TP_MULTICAST = "multicast";
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX = "Digest ";

}
