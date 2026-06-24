package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;

public interface RtspProtoDescribeRespSrtxpTypeDeciderInterface {

	/**
	 * Decide whether to use legacy SDES for SRTxP KMDs.<br />
	 * This method is used to determine if the client's User Agent is not compatible with the
	 * modern MIKEY SRTxP Key Management system.<br />
	 * It will be called when the server builds a DESCRIBE response.
	 * @param clientUserAgent The client's User Agent (e.g. 'LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)')
	 * @return True if the client's User Agent is not compatible with the modern MIKEY SRTxP Key Management system.
	 */
	boolean useLegacySdesForSrtxpKmds(@NonNull String clientUserAgent);

}
