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
			put(RtpPacketType.A_AAC, "mpeg4-generic");
			put(RtpPacketType.A_PCMU_8KHZ_MONO, "PCMU");
			put(RtpPacketType.A_PCMU_VAR, "PCMU");
			put(RtpPacketType.A_LINEAR_PCM_U08_VAR, "L8");
			put(RtpPacketType.A_LINEAR_PCM_S16_441K_MONO, "L16");
			put(RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO, "L16");
			put(RtpPacketType.A_LINEAR_PCM_S16_VAR, "L16");
			put(RtpPacketType.V_JPEG, "JPEG");
			put(RtpPacketType.V_H264, "H264");
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

	/** Stream types according to ISO/IEC 14496-1 */
	enum IsoIec14496_1_StreamType {
		/** Forbidden */
		FORBIDDEN(0x00),
		/** ObjectDescriptorStream (see ISO/IEC 14496-1 Section 7.2.5) */
		OBJECTDESCRIPTORSTREAM(0x01),
		/** ClockReferenceStream (see ISO/IEC 14496-1 Section 7.3.2.5) */
		CLOCKREFERENCESTREAM(0x02),
		/** SceneDescriptionStream (see ISO/IEC 14496-11) */
		SCENEDESCRIPTIONSTREAM(0x03),
		/** VisualStream */
		VISUALSTREAM(0x04),
		/** AudioStream */
		AUDIOSTREAM(0x05),
		/** MPEG7Stream */
		MPEG7STREAM(0x06),
		/** IPMPStream (see ISO/IEC 14496-1 Section 7.2.3.2) */
		IPMPSTREAM(0x07),
		/** ObjectContentInfoStream (see ISO/IEC 14496-1 Section 7.2.4.2) */
		OBJECTCONTENTINFOSTREAM(0x08),
		/** MPEGJStream */
		MPEGJSTREAM(0x09),
		/** Interaction Stream */
		INTERACTIONSTREAM(0x0A),
		/** IPMPToolStream (see ISO/IEC 14496-13) */
		IPMPTOOLSTREAM(0x0B);

		public final int value;
		IsoIec14496_1_StreamType(int value) {
			this.value = value;
		}
	}

	/**
	 * Audio profiles and levels.<br />
	 * According to ISO/IEC 14496-3:2009 Table 1.14 audioProfileLevelIndication values
	 */
	enum IsoIec14496_3_AudioProfilesAndLevels {
		/*
			0x05 Scalable Audio Profile; L1
			0x06 Scalable Audio Profile; L2
			0x07 Scalable Audio Profile; L3
			0x08 Scalable Audio Profile; L4

			0x09 Speech Audio Profile; L1
			0x0A Speech Audio Profile; L2

			0x0B Synthetic Audio Profile; L1
			0x0C Synthetic Audio Profile; L2
			0x0D Synthetic Audio Profile; L3

			0x16 Low Delay Audio Profile; L1
			0x17 Low Delay Audio Profile; L2
			0x18 Low Delay Audio Profile; L3
			0x19 Low Delay Audio Profile; L4
			0x1A Low Delay Audio Profile; L5
			0x1B Low Delay Audio Profile; L6
			0x1C Low Delay Audio Profile; L7
			0x1D Low Delay Audio Profile; L8

			0x1E Natural Audio Profile; L1
			0x1F Natural Audio Profile; L2
			0x20 Natural Audio Profile; L3
			0x21 Natural Audio Profile; L4

			0x22 Mobile Audio Internetworking Profile; L1
			0x23 Mobile Audio Internetworking Profile; L2
			0x24 Mobile Audio Internetworking Profile; L3
			0x25 Mobile Audio Internetworking Profile; L4
			0x26 Mobile Audio Internetworking Profile; L5
			0x27 Mobile Audio Internetworking Profile; L6

			0x34 Low Delay AAC Profile; L1

			0x35 Baseline MPEG Surround Profile (see ISO/IEC23003-1); L1
			0x36 Baseline MPEG Surround Profile (see ISO/IEC23003-1); L2
			0x37 Baseline MPEG Surround Profile (see ISO/IEC23003-1); L3
			0x38 Baseline MPEG Surround Profile (see ISO/IEC23003-1); L4
			0c39 Baseline MPEG Surround Profile (see ISO/IEC23003-1); L5
			0x3A Baseline MPEG Surround Profile (see ISO/IEC23003-1); L6

			0x3B - 0x7F reserved for ISO use
			0x80 - 0xFD user private
			0xFE no audio profile specified
			0xFF no audio capability required
		*/

		/** Main Audio Profile, Level 1 */
		MAIN_LEV1(0x01),
		/** Main Audio Profile, Level 2 */
		MAIN_LEV2(0x02),
		/** Main Audio Profile, Level 3 */
		MAIN_LEV3(0x03),
		/** Main Audio Profile, Level 4 */
		MAIN_LEV4(0x04),

		/** High Quality Audio Profile, Level 1; up to 2 ch, ≤ 22.05 kHz */
		HQ_LEV1(0x0E),
		/** High Quality Audio Profile, Level 2; up to 2 ch, ≤ 48 kHz */
		HQ_LEV2(0x0F),
		/** High Quality Audio Profile, Level 3; up to 5.1 ch, ≤ 48 kHz */
		HQ_LEV3(0x10),
		/** High Quality Audio Profile, Level 4; up to 5.1 ch, ≤ 48 kHz */
		HQ_LEV4(0x11),
		/** High Quality Audio Profile, Level 5; up to 2 ch, ≤ 22.05 kHz */
		HQ_LEV5(0x12),
		/** High Quality Audio Profile, Level 6; up to 2 ch, ≤ 48 kHz */
		HQ_LEV6(0x13),
		/** High Quality Audio Profile, Level 7; up to 5.1 ch, ≤ 48 kHz */
		HQ_LEV7(0x14),
		/** High Quality Audio Profile, Level 8; up to 5.1 ch, ≤ 48 kHz */
		HQ_LEV8(0x15),

		/** AAC, AAC Profile, Level 1; up to 2 ch, ≤ 24 kHz */
		AAC_LEV1(0x28),
		/** AAC, AAC Profile, Level 2; up to 2 ch, ≤ 48 kHz */
		AAC_LEV2(0x29),
		/** AAC, AAC Profile, Level 4; up to 5.1 ch, ≤ 48 kHz */
		AAC_LEV4(0x2A),
		/** AAC, AAC Profile, Level 5; up to 5.1 ch, ≤ 96 kHz */
		AAC_LEV5(0x2B),

		/** HE-AAC, High Efficiency AAC Profile, Level 2 */
		HE_AAC_V1_LEV2(0x2C),
		/** HE-AAC, High Efficiency AAC Profile, Level 3 */
		HE_AAC_V1_LEV3(0x2D),
		/** HE-AAC, High Efficiency AAC Profile, Level 4 */
		HE_AAC_V1_LEV4(0x2E),
		/** HE-AAC, High Efficiency AAC Profile, Level 5 */
		HE_AAC_V1_LEV5(0x2F),

		/** HE-AAC v2, High Efficiency AAC v2 Profile, Level 2 */
		HE_AAC_V2_LEV2(0x2C),
		/** HE-AAC v2, High Efficiency AAC v2 Profile, Level 3 */
		HE_AAC_V2_LEV3(0x2D),
		/** HE-AAC v2, High Efficiency AAC v2 Profile, Level 4 */
		HE_AAC_V2_LEV4(0x2E),
		/** HE-AAC v2, High Efficiency AAC v2 Profile, Level 5 */
		HE_AAC_V2_LEV5(0x2F),

		UNKNOWN(0xFF);

		public final int value;
		IsoIec14496_3_AudioProfilesAndLevels(int value) {
			this.value = value;
		}
	}

	public enum H26xPacketizationMode {
		/** One NAL unit per RTP packet. Simple but inefficient for large frames */
		SINGLE_NALU(0),
		/** Allows fragmentation (FU-A) and aggregation (STAP-A). Most common for streaming */
		NON_INTERLEAVED(1),
		/** Allows re-ordering across packets. Rare; mostly for low-latency broadcast */
		INTERLEAVED(2);

		public final int value;
		H26xPacketizationMode(int value) {
			this.value = value;
		}
	}

}
