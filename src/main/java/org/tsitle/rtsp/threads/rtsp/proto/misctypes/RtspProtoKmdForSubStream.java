package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;

import java.util.Optional;

public final class RtspProtoKmdForSubStream implements Cloneable {

	private boolean isKmdForLegacySdes = false;
	private @Nullable SrtxpKmd kmd = null;
	private final @NonNull RtspProtoIdSubStream idSubStream = new RtspProtoIdSubStream();

	public boolean isKmdSet() {
		return (kmd != null);
	}
	public void setKmd(@NonNull SrtxpKmd kmd, @NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		this.kmd = kmd.clone();
		this.idSubStream.copyFrom(idSubStream);
		isKmdForLegacySdes = kmd.isForLegacySdes();
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

	public void clear() {
		isKmdForLegacySdes = false;
		kmd = null;
		idSubStream.clear();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoKmdForSubStream clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

}
