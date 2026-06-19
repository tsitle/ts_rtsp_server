package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

import java.util.Optional;

/**
 * Container for SRTxP-KMD for a Sub-Stream.
 */
public final class RtspProtoKmdForSubStream implements Cloneable {

	private boolean writeProtected = false;

	private boolean isKmdForLegacySdes = false;
	private @Nullable SrtxpKmd kmd = null;
	private @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isKmdSet() {
		return (kmd != null);
	}
	public void setKmd(@NonNull SrtxpKmd kmd, @NonNull RtspProtoIdSubStream idSubStream) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		if (idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		this.kmd = kmd.clone();
		this.idSubStream.copyFrom(idSubStream);
		isKmdForLegacySdes = kmd.getMetaIsForLegacySdes();
	}
	public Optional<SrtxpKmd> getKmd() {
		if (kmd == null) {
			return Optional.empty();
		}
		return Optional.of(kmd.clone());
	}

	public Optional<RtspProtoIdSubStream> getSubStreamId() {
		if (kmd == null) {
			return Optional.empty();
		}
		return Optional.of(idSubStream.clone());
	}

	@SuppressWarnings("unused")
	public Optional<Boolean> getIsKmdForLegacySdes() {
		if (kmd == null) {
			return Optional.empty();
		}
		return Optional.of(isKmdForLegacySdes);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		writeProtected = true;

		idSubStream.writeProtect();
	}

	public void clear() {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		isKmdForLegacySdes = false;
		kmd = null;
		idSubStream.clear();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoKmdForSubStream clone() {
		try {
			RtspProtoKmdForSubStream cloned = (RtspProtoKmdForSubStream)super.clone();
			if (kmd != null) {
				cloned.kmd = kmd.clone();
			}
			cloned.idSubStream = idSubStream.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
