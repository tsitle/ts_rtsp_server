package org.tsitle.rtsp.threads.rtsp.proto.interfaces;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoRtspParamUnknownException;

public interface RtspProtoParameterSetterInterface {

	/**
	 * Test if setting a parameter is possible.
	 * @param idSession Session ID
	 * @param contentLanguage Content language (can be empty)
	 * @param key The parameter key
	 * @param value The parameter's value
	 * @throws RtspProtoRtspParamUnknownException If the parameter is unknown
	 * @throws RtspProtoRtspParamInvalidValueException If the parameter's value is invalid
	 */
	void testSettingRtspParameter(
			@NonNull RtspProtoIdSession idSession,
			@NonNull String contentLanguage,
			@NonNull String key,
			@NonNull String value
		) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException;

	/**
	 * Set a parameter.
	 * @param idSession Session ID
	 * @param contentLanguage Content language (can be empty)
	 * @param key The parameter key
	 * @param value The parameter's value
	 * @throws RtspProtoRtspParamUnknownException If the parameter is unknown
	 * @throws RtspProtoRtspParamInvalidValueException If the parameter's value is invalid
	 */
	void setRtspParameter(
			@NonNull RtspProtoIdSession idSession,
			@NonNull String contentLanguage,
			@NonNull String key,
			@NonNull String value
		) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException;

}
