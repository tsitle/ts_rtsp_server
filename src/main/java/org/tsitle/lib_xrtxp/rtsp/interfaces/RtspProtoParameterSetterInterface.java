package org.tsitle.lib_xrtxp.rtsp.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;

public interface RtspProtoParameterSetterInterface {

	/**
	 * Set a parameter.
	 * @param dryRunOnly If true, do not actually set the parameter
	 * @param idSession Session ID
	 * @param contentLanguage Content language (can be empty)
	 * @param key The parameter key
	 * @param value The parameter's value
	 * @throws RtspProtoRtspParamUnknownException If the parameter is unknown
	 * @throws RtspProtoRtspParamInvalidValueException If the parameter's value is invalid
	 */
	void setRtspParameter(
			boolean dryRunOnly,
			@NonNull RtspProtoIdSession idSession,
			@NonNull String contentLanguage,
			@NonNull String key,
			@NonNull String value
		) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException;

}
