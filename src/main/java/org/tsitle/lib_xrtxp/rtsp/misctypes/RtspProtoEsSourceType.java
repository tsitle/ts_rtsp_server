package org.tsitle.lib_xrtxp.rtsp.misctypes;

public enum RtspProtoEsSourceType {

	/** Elementary-Stream from a raw file */
	ST_ES_RAW_FILE,
	/** Elementary-Stream from an external Message Queue */
	ST_ES_MQ,
	/** Demuxed Elementary-Stream from a file container */
	ST_DEMUX_FC,
	/** Demuxed Elementary-Stream from an RTSP stream */
	ST_DEMUX_RTSP,
	/** Demuxed Elementary-Stream from an 'Audio Folder' stream */
	ST_DEMUX_AF,
	/** Elementary-Stream from a demuxed file container */
	ST_DMX_VIRTUAL_ES_FC,
	/** Elementary-Stream from a demuxed RTSP stream using a Message Queue for internal transport  */
	ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP,
	/** Elementary-Stream from an 'Audio Folder' stream using a Message Queue for internal transport  */
	ST_DMX_VIRTUAL_ES_MQ_FROM_AF;

	public boolean isFromFile() {
		return (this == ST_ES_RAW_FILE || this == ST_DEMUX_FC || this == ST_DEMUX_AF || this == ST_DMX_VIRTUAL_ES_FC);
	}

}
