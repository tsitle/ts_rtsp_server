package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

/**
 * Interface for getting RTSP parameters.
 */
public interface RtspProtoParameterGetterInterface {

	/**
	 * Get all available parameters.
	 * @param idSession Session ID (required)
	 * @param idInputSource Input Source ID (required)
	 * @param idSubStream Sub-Stream ID (can be empty)
	 */
	@NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdSubStream idSubStream
			);

}
