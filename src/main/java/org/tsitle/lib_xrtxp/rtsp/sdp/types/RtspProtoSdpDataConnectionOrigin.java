package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

public record RtspProtoSdpDataConnectionOrigin(
			@NonNull String nwType,
			@NonNull String addrType,
			@NonNull String addrVal
		) {

	public RtspProtoSdpDataConnectionOrigin(@NonNull RtspProtoSdpDataConnectionOrigin other) {
		this(other.nwType, other.addrType, other.addrVal);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (nwType.isBlank() && addrType.isBlank() && addrVal.isBlank());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"nwType='" + nwType + "'" +
				", addrType='" + addrType + "'" +
				", addrVal='" + addrVal + "'" +
				"]";
	}

}
