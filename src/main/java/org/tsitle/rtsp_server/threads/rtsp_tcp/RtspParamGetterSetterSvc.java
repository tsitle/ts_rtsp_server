package org.tsitle.rtsp_server.threads.rtsp_tcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterGetterInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;

/**
 * Service for getting and setting RTSP parameters.
 */
public final class RtspParamGetterSetterSvc implements RtspProtoParameterGetterInterface, RtspProtoParameterSetterInterface {

	public final static String CONTENT_LANGUAGE = "en";

	private final RtspProtoIdSession currentSessionId = RtspProtoIdSession.ofEmpty();

	public RtspParamGetterSetterSvc() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void updateSessionId(@NonNull RtspProtoIdSession idSession) {
		currentSessionId.copyFrom(idSession);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void setRtspParameter(
				boolean dryRunOnly,
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull String contentLanguage,
				@NonNull String key,
				@NonNull String value
			) throws RtspProtoRtspParamUnknownException, RtspProtoRtspParamInvalidValueException {
		if (! key.isBlank()) {
			throw new RtspProtoRtspParamUnknownException(key);
		}
		if (! value.isBlank()) {
			throw new RtspProtoRtspParamInvalidValueException(key);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoDataCntGetSetParamKvs getAllRtspParameters(
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdSubStream idSubStream
			) {
		RtspProtoDataCntGetSetParamKvs resObj = new RtspProtoDataCntGetSetParamKvs();
		resObj.setContentLang(CONTENT_LANGUAGE);
		return resObj;
	}

}
