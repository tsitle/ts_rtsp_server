package org.tsitle.lib.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSubStream;

import java.util.Optional;

/**
 * Container for SRTxP-KMDs for a Stream (with up to two Sub-Streams).
 */
public final class RtspProtoKmdsStream implements Cloneable {

	private @Nullable Boolean areKmdsForLegacySdes = null;
	private final @NonNull RtspProtoKmdForSubStream kmdSs1 = new RtspProtoKmdForSubStream();
	private final @NonNull RtspProtoKmdForSubStream kmdSs2 = new RtspProtoKmdForSubStream();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void putKmdForSubStream(@NonNull SrtxpKmd kmd, @NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		if (areKmdsForLegacySdes != null && areKmdsForLegacySdes != kmd.isForLegacySdes()) {
			throw new IllegalArgumentException("Cannot mix legacy SDES and non-legacy SDES KMDs");
		}
		RtspProtoKmdForSubStream trg;
		if (kmdSs1.isKmdSet() && kmdSs1.getSubStreamId().isPresent() && kmdSs1.getSubStreamId().orElseThrow().equals(idSubStream)) {
			trg = kmdSs1;
		} else if (kmdSs2.isKmdSet() && kmdSs2.getSubStreamId().isPresent() && kmdSs2.getSubStreamId().orElseThrow().equals(idSubStream)) {
			trg = kmdSs2;
		} else if (! kmdSs1.isKmdSet()) {
			trg = kmdSs1;
		} else {
			trg = kmdSs2;
		}
		trg.setKmd(kmd, idSubStream);
		areKmdsForLegacySdes = kmd.isForLegacySdes();
	}

	public boolean containsKmdForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (kmdSs1.getSubStreamId().isPresent() && idSubStream.equals(kmdSs1.getSubStreamId().get())) {
			return true;
		}
		return (kmdSs2.getSubStreamId().isPresent() && idSubStream.equals(kmdSs2.getSubStreamId().get()));
	}

	public Optional<SrtxpKmd> getKmdBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		if (kmdSs1.getSubStreamId().isPresent() && idSubStream.equals(kmdSs1.getSubStreamId().get())) {
			return kmdSs1.getKmd();
		}
		if (kmdSs2.getSubStreamId().isPresent() && idSubStream.equals(kmdSs2.getSubStreamId().get())) {
			return kmdSs2.getKmd();
		}
		return Optional.empty();
	}

	public int getNumberOfSubStreams() {
		return (kmdSs1.getSubStreamId().isPresent() ? 1 : 0) + (kmdSs2.getSubStreamId().isPresent() ? 1 : 0);
	}

	public Optional<Boolean> getAreKmdsForLegacySdes() {
		return Optional.ofNullable(areKmdsForLegacySdes);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		boolean have1 = kmdSs1.getSubStreamId().isPresent();
		boolean have2 = kmdSs2.getSubStreamId().isPresent();

		return getClass().getSimpleName() + " [" +
				"areKmdsForLegacySdes=" + (areKmdsForLegacySdes == null ? "-" : (areKmdsForLegacySdes ? "T" : "F")) +
				", kmdSubStream1=" + (have1 ? kmdSs1.getKmd().orElseThrow() : "-") +
				", subStreamId1=" + (have1 ? "'" + kmdSs1.getSubStreamId().orElseThrow().getIdStr() + "'" : "-") +
				", kmdSubStream2=" + (have2 ? kmdSs2.getKmd().orElseThrow() : "-") +
				", subStreamId2=" + (have2 ? "'" + kmdSs2.getSubStreamId().orElseThrow().getIdStr() + "'" : "-") +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoKmdsStream clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

}
