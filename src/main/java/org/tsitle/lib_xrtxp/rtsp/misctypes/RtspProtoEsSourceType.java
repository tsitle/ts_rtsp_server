package org.tsitle.lib_xrtxp.rtsp.misctypes;

public enum RtspProtoEsSourceType {

	/** Elementary-Stream from a raw file */
	ST_ES_RAW_FILE,
	/** Elementary-Stream from an external Message Queue */
	ST_ES_MQ,
	/** Demuxed Elementary-Stream from a file container */
	ST_DEMUX_MS_FILE,
	/** Demuxed Elementary-Stream from an RTSP stream */
	ST_DEMUX_MS_RTSP,
	/** Elementary-Stream from a demuxed file container */
	ST_DMX_VIRTUAL_ES_FC,
	/** Elementary-Stream from a demuxed RTSP stream using a Message Queue for internal transport  */
	ST_DMX_VIRTUAL_ES_MQ;

	public boolean isFromFile() {
		return (this == ST_ES_RAW_FILE || this == ST_DEMUX_MS_FILE || this == ST_DMX_VIRTUAL_ES_FC);
	}

	@SuppressWarnings("unused")
	public boolean isDemuxed() {
		return (this == ST_DEMUX_MS_FILE || this == ST_DMX_VIRTUAL_ES_FC || this == ST_DEMUX_MS_RTSP || this == ST_DMX_VIRTUAL_ES_MQ);
	}

	@SuppressWarnings("unused")
	public boolean isExternalMq() {
		return (this == ST_ES_MQ);
	}

	@SuppressWarnings("unused")
	public boolean isAnyMq() {
		return (this == ST_ES_MQ || this == ST_DMX_VIRTUAL_ES_MQ);
	}

}
