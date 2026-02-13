package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.rtsp.packets.rtp.RtpPacketType;

import java.util.Map;
import java.util.TreeMap;

class RtspPrivateConstants {

	/** Maximum length for RTSP Resource URLs */
	static final int RTSP_MAX_RESOURCE_URL_LENGTH = 256;

	/** Prefix for Stream IDs as publicized over SDP */
	static final String STREAM_ID_PREFIX = "streamid";

	// RTSP Requests/Responses
	///
	static final String RTSP_RR_CMD_PROTOCOL_VERSION_1 = "RTSP/1.0";
	static final String RTSP_RR_CMD_PROTOCOL_VERSION_2 = "RTSP/2.0";
	///
	static final String RTSP_RR_HEADER_TOKEN_DES_ACCEPT = "Accept:";
	static final String RTSP_RR_HEADER_TOKEN_DES_CONTBASE = "Content-Base:";
	static final String RTSP_RR_HEADER_TOKEN_DES_CONTTYPE = "Content-Type:";
	static final String RTSP_RR_HEADER_TOKEN_SET_TRANSPORT = "Transport:";
	static final String RTSP_RR_HEADER_TOKEN_PLA_RANGE = "Range:";
	static final String RTSP_RR_HEADER_TOKEN_PLA_RTPINFO = "RTP-Info:";
	static final String RTSP_RR_HEADER_TOKEN_OPT_PUBLIC = "Public:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_CSEQ = "CSeq:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_SESSION = "Session:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_USERAGENT = "User-Agent:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_SERVER = "Server:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_CONTLEN = "Content-Length:";
	static final String RTSP_RR_HEADER_TOKEN_XXX_DATE = "Date:";
	///
	static final String RTSP_RR_HEADER_VALUE_DES_ACCEPT = "application/sdp";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPUDP = "RTP/AVP";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPTCP = "RTP/AVP/TCP";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_UNICAST = "unicast";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_CLIENTPORT = "client_port=";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_SERVERPORT = "server_port=";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_DESTIP = "destination=";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_SOURCEIP = "source=";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_SSRC = "ssrc=";
	static final String RTSP_RR_HEADER_VALUE_SET_TP_INTERLEAVED = "interleaved=";
	static final String RTSP_RR_HEADER_VALUE_SET_TIMEOUT = "timeout=";
	static final String RTSP_RR_HEADER_VALUE_PLA_RI_URL = "url=";
	static final String RTSP_RR_HEADER_VALUE_PLA_RI_SEQ = "seq=";
	static final String RTSP_RR_HEADER_VALUE_PLA_RI_RTPTIME = "rtptime=";
	///
	static final Map<RtpPacketType, String> RTSP_SDP_TAG_A_CODEC_MAPPING = new TreeMap<>() {{
			put(RtpPacketType.A_PCMU_8KHZ_MONO, "PCMU");
			put(RtpPacketType.A_PCMU_VAR, "PCMU");
			put(RtpPacketType.A_LINEAR_PCM_U08_VAR, "L8");
			put(RtpPacketType.A_LINEAR_PCM_S16_441K_MONO, "L16");
			put(RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO, "L16");
			put(RtpPacketType.A_LINEAR_PCM_S16_VAR, "L16");
			put(RtpPacketType.V_JPEG, "JPEG");
			put(RtpPacketType.V_H265, "H265");
		}};
	///
	static final Map<ServerResponseStatusCode, String> RTSP_RR_SC_MAP_TO_STR = new TreeMap<>() {{
			put(ServerResponseStatusCode.OK, "OK");
			put(ServerResponseStatusCode.BAD_REQUEST, "Bad Request");
			put(ServerResponseStatusCode.UNAUTHORIZED, "Unauthorized");
			put(ServerResponseStatusCode.NOT_FOUND, "Not Found");
			put(ServerResponseStatusCode.SESSION_NOT_FOUND, "Session Not Found");
			put(ServerResponseStatusCode.UNSUPPORTED_TRANSPORT, "Unsupported Transport");
		}};

}
