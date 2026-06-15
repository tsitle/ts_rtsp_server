package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdSubStreamNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoStaticSessionDataInterface;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;

public final class RtspStaticSessionDataSvc implements RtspProtoStaticSessionDataInterface {

	public RtspStaticSessionDataSvc() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoIdSubStream createSubStreamId(
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull RtspProtoIpAddr clientIpAddr
			) {
		return RtspStaticSessionInfo.createSubStreamId(
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
		return RtspStaticSessionInfo.getInputSourceIdBySubStreamId(idSubStream, clientIpAddr);
	}

	@Override
	public @NonNull RtspProtoIdStreamSource getStreamSourceIdBySubStreamId(
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws RtspIdSubStreamNotFoundException {
		return RtspStaticSessionInfo.getStreamSourceIdBySubStreamId(idSubStream, clientIpAddr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String createAuthServerNonce(@NonNull RtspProtoIpAddr clientIpAddr) {
		return RtspStaticSessionInfo.createAuthServerNonce(clientIpAddr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int incrementUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		return RtspStaticSessionInfo.incrementUnauthorized(clientIpAddr, idInputSource);
	}

	@Override
	public void resetUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		RtspStaticSessionInfo.resetUnauthorized(clientIpAddr, idInputSource);
	}

	@Override
	public int getUnauthorized(@NonNull RtspProtoIpAddr clientIpAddr, @NonNull RtspProtoIdInputSource idInputSource) {
		return RtspStaticSessionInfo.getUnauthorized(clientIpAddr, idInputSource);
	}

}
