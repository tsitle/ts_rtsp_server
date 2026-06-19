package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public class RtspProtoSdpDataConnectionMsBase {

	private final @NonNull String nwType;
	private final @NonNull String addrType;
	private final @NonNull String addrVal;

	protected RtspProtoSdpDataConnectionMsBase(
				@NonNull String nwType,
				@NonNull String addrType,
				@NonNull String addrVal
			) {
		this.nwType = nwType;
		this.addrType = addrType;
		this.addrVal = addrVal;
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

	public @NonNull String nwType() {
		return nwType;
	}

	public @NonNull String addrType() {
		return addrType;
	}

	public @NonNull String addrVal() {
		return addrVal;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (RtspProtoSdpDataConnectionMsBase) obj;
		return Objects.equals(this.nwType, that.nwType) &&
				Objects.equals(this.addrType, that.addrType) &&
				Objects.equals(this.addrVal, that.addrVal);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nwType, addrType, addrVal);
	}

}
