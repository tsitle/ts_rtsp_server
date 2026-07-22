package org.tsitle.lib_xrtxp.rtsp.misctypes;

public enum RtspProtoEsSourceType {

	/** Elementary-Stream from a file */
	ST_ES_FILE,
	/** Elementary-Stream from a Message Queue */
	ST_ES_MQ,
	/** Demuxed Elementary-Stream from a file */
	ST_DEMUX_MS_FILE;

	public boolean isFromFile() {
		return (this == ST_ES_FILE || this == ST_DEMUX_MS_FILE);
	}

}
