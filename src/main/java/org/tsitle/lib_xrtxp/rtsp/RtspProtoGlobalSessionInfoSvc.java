package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdSubStreamNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoGlobalSessionDataStorage;

public final class RtspProtoGlobalSessionInfoSvc implements RtspProtoGlobalSessionInfoInterface {

	private final RtspProtoGlobalSessionDataStorage globalSessionInfo = new RtspProtoGlobalSessionDataStorage();

	public RtspProtoGlobalSessionInfoSvc() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoIdSubStream createSubStreamId(
				@NonNull String cfgSubStreamIdPrefix,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull RtspProtoIpAddr clientIpAddr
			) {
		return globalSessionInfo.createSubStreamId(
				cfgSubStreamIdPrefix,
				clientIpAddr,
				idInputSource,
				idStreamSource
			);
	}

	@Override
	public @NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException {
		return globalSessionInfo.getInputSourceIdBySubStreamId(idSubStream, clientIpAddr);
	}

	@Override
	public @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspProtoIdSubStreamNotFoundException {
		return globalSessionInfo.getStreamSourceIdBySubStreamId(idSubStream, clientIpAddr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr) {
		return globalSessionInfo.createAuthServerNonce(clientIpAddr);
	}

	@Override
	public boolean existsAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull String nonce) {
		return globalSessionInfo.existsAuthServerNonce(clientIpAddr, nonce);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		return globalSessionInfo.incrementUnauthorized(clientIpAddr, idInputSource);
	}

	@Override
	public void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		globalSessionInfo.resetUnauthorized(clientIpAddr, idInputSource);
	}

	@Override
	public int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		return globalSessionInfo.getUnauthorized(clientIpAddr, idInputSource);
	}

}
