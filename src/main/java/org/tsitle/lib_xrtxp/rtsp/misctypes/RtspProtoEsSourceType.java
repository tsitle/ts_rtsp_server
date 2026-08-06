package org.tsitle.lib_xrtxp.rtsp.misctypes;

public enum RtspProtoEsSourceType {

	/** Elementary-Stream from a raw file */
	ST_ES_RAW_FILE,
	/** Elementary-Stream from a Message Queue */
	ST_ES_MQ,
	/** Demuxed Elementary-Stream from a file container */
	ST_DEMUX_MS_FILE,
	/** Demuxed Elementary-Stream from an RTSP stream */
	ST_DEMUX_MS_RTSP;

	public boolean isFromFile() {
		return (this == ST_ES_RAW_FILE || this == ST_DEMUX_MS_FILE);
	}

	public boolean isDemuxed() {
		return (this == ST_DEMUX_MS_FILE || this == ST_DEMUX_MS_RTSP);
	}

	@SuppressWarnings("unused")
	public boolean isMq() {
		return (this == ST_ES_MQ);
	}

}
