package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdSubStreamNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoGlobalSessionDataStorage;

public final class RtspProtoGlobalSessionInfoSvc implements RtspProtoGlobalSessionInfoInterface {

	private final RtspProtoGlobalSessionDataStorage globalSessionInfo = new RtspProtoGlobalSessionDataStorage();

	public RtspProtoGlobalSessionInfoSvc() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoIdSubStream createSubStreamId(
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull RtspProtoIpAddr clientIpAddr
			) {
		return globalSessionInfo.createSubStreamId(
				clientIpAddr,
				idInputSource,
				idStreamSource
			);
	}

	@Override
	public @NonNull RtspProtoIdInputSource getInputSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
		return globalSessionInfo.getInputSourceIdBySubStreamId(idSubStream, clientIpAddr);
	}

	@Override
	public @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
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
