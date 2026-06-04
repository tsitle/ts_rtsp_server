package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtxpKmd;

import java.util.Optional;

public final class RtspKeymgmtKmdsOutbound {

	private @Nullable Boolean areKmdsForLegacySdes = null;
	private @Nullable SrtxpKmd kmdSubStream1 = null;
	private @NonNull String subStreamId1 = "";
	private @Nullable SrtxpKmd kmdSubStream2 = null;
	private @NonNull String subStreamId2 = "";

	public void setKmdForSubStream1(@NonNull SrtxpKmd kmd, @NonNull String subStreamId) {
		if (subStreamId.isBlank()) {
			throw new IllegalArgumentException("subStreamId cannot be blank");
		}
		if (subStreamId.equalsIgnoreCase(subStreamId2)) {
			throw new IllegalArgumentException("subStreamId already used by Sub-Stream 2");
		}
		if (areKmdsForLegacySdes != null && areKmdsForLegacySdes != kmd.isForLegacySdes()) {
			throw new IllegalArgumentException("Cannot mix legacy SDES and non-legacy SDES KMDs");
		}
		kmdSubStream1 = kmd.clone();
		subStreamId1 = subStreamId;
		areKmdsForLegacySdes = kmd.isForLegacySdes();
	}

	public void setKmdForSubStream2(@NonNull SrtxpKmd kmd, @NonNull String subStreamId) {
		if (subStreamId.isBlank()) {
			throw new IllegalArgumentException("subStreamId cannot be blank");
		}
		if (subStreamId.equalsIgnoreCase(subStreamId1)) {
			throw new IllegalArgumentException("subStreamId already used by Sub-Stream 1");
		}
		if (areKmdsForLegacySdes != null && areKmdsForLegacySdes != kmd.isForLegacySdes()) {
			throw new IllegalArgumentException("Cannot mix legacy SDES and non-legacy SDES KMDs");
		}
		kmdSubStream2 = kmd.clone();
		subStreamId2 = subStreamId;
		areKmdsForLegacySdes = kmd.isForLegacySdes();
	}

	public Optional<SrtxpKmd> getKmdForSubStream(@NonNull String subStreamId) {
		if (subStreamId.isBlank()) {
			return Optional.empty();
		}
		if (subStreamId.equalsIgnoreCase(subStreamId1)) {
			if (kmdSubStream1 == null) {  // only for the linter
				return Optional.empty();
			}
			return Optional.of(kmdSubStream1.clone());
		}
		if (subStreamId.equalsIgnoreCase(subStreamId2)) {
			if (kmdSubStream2 == null) {  // only for the linter
				return Optional.empty();
			}
			return Optional.of(kmdSubStream2.clone());
		}
		return Optional.empty();
	}

	public int getNumberOfSubStreams() {
		return (kmdSubStream1 != null ? 1 : 0) + (kmdSubStream2 != null ? 1 : 0);
	}

	public Optional<Boolean> getAreKmdsForLegacySdes() {
		return Optional.ofNullable(areKmdsForLegacySdes);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"areKmdsForLegacySdes=" + (areKmdsForLegacySdes == null ? "-" : (areKmdsForLegacySdes ? "T" : "F")) +
				", kmdSubStream1=" + kmdSubStream1 +
				", subStreamId1='" + subStreamId1 + "'" +
				", kmdSubStream2=" + kmdSubStream2 +
				", subStreamId2='" + subStreamId2 + "'" +
				"]";
	}

}
