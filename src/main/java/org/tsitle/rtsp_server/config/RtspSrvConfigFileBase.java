package org.tsitle.rtsp_server.config;

import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

public abstract sealed class RtspSrvConfigFileBase permits RtspSrvConfigMain, RtspSrvConfigFileStreams {

	/**
	 * Post-process the configuration.
	 */
	abstract void postProcess() throws ConfigInvalidException;

}
