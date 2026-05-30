package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

public class RtspProtoLowConstants {

	/** RTSP URL Protocol */
	public static final String RTSP_URL_PROTOCOL = "rtsp";
	/** RTSPS URL Protocol */
	public static final String RTSPS_URL_PROTOCOL = "rtsps";

	/** Maximum length for RTSP Resource URLs */
	public static final int RTSP_MAX_RESOURCE_URL_LENGTH = 512;

	// RTSP Requests/Responses
	///
	public static final String RTSP_RR_CMD_PROTOCOL_VERSION_1 = "RTSP/1.0";
	public static final String RTSP_RR_CMD_PROTOCOL_VERSION_2 = "RTSP/2.0";
	///
	public static final String RTSP_RR_HEADER_TOKEN_DES_ACCEPT = "Accept";
	public static final String RTSP_RR_HEADER_TOKEN_DES_CONTBASE = "Content-Base";
	public static final String RTSP_RR_HEADER_TOKEN_SET_TRANSPORT = "Transport";
	public static final String RTSP_RR_HEADER_TOKEN_PLA_RANGE = "Range";
	public static final String RTSP_RR_HEADER_TOKEN_PLA_RTPINFO = "RTP-Info";
	public static final String RTSP_RR_HEADER_TOKEN_OPT_PUBLIC = "Public";
	public static final String RTSP_RR_HEADER_TOKEN_OPT_REQUIRE = "Require";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_CSEQ = "CSeq";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_SESSION = "Session";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_USERAGENT = "User-Agent";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_SERVER = "Server";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_CONTTYPE = "Content-Type";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_CONTLEN = "Content-Length";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_DATE = "Date";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_AUTH = "Authorization";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_WWWAUTH = "WWW-Authenticate";
	public static final String RTSP_RR_HEADER_TOKEN_XXX_KEYMGMT = "KeyMgmt";
	///
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT = "client_port=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT = "server_port=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP = "destination=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP = "source=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC = "ssrc=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED = "interleaved=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT = "timeout=";
	public static final String RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL = "url=";
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
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP = "application/sdp";
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_CT_MIKEY = "application/x-rtsp-mikey";
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX = "Digest ";
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5 = "MD5";
	public static final String RTSP_RR_HEADER_PARAM_VAL_XXX_KM_MIKEY = "mikey";

}
