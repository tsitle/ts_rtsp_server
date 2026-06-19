package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

public final class RtspProtoSdpDataConnectionSession extends RtspProtoSdpDataConnectionMsBase {

	public RtspProtoSdpDataConnectionSession(
				@NonNull String nwType,
				@NonNull String addrType,
				@NonNull String addrVal
			) {
		super(nwType, addrType, addrVal);
	}

	public RtspProtoSdpDataConnectionSession(@NonNull RtspProtoSdpDataConnectionSession other) {
		this(other.nwType(), other.addrType(), other.addrVal());
	}

}
