package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

public enum RtspProtoLowHeaderKey {

	NONE,
	//
	/** (only for requests) */
	ACCEPT,
	AUTH,
	CONTENT_BASE,
	CONTENT_TYPE,
	CSEQ,
	DATE,
	KEYMGMT,
	PUBLIC,
	RANGE,
	/** (only for requests) */
	REQUIRE,
	/** (only for responses) */
	RTPINFO,
	/** (only for responses) */
	SERVER,
	SESSION,
	TRANSPORT,
	/** (only for requests) */
	USERAGENT,

}
