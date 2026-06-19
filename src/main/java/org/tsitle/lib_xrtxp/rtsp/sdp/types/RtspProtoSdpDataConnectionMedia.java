package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

public final class RtspProtoSdpDataConnectionMedia extends RtspProtoSdpDataConnectionMsBase {

	public RtspProtoSdpDataConnectionMedia(
				@NonNull String nwType,
				@NonNull String addrType,
				@NonNull String addrVal
			) {
		super(nwType, addrType, addrVal);
	}

	public RtspProtoSdpDataConnectionMedia(@NonNull RtspProtoSdpDataConnectionMedia other) {
		this(other.nwType(), other.addrType(), other.addrVal());
	}

}
