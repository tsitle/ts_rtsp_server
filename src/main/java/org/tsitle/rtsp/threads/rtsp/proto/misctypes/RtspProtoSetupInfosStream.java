package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class RtspProtoSetupInfosStream implements Cloneable {

	private final @NonNull RtspProtoIdSubStream idSubStream1 = new RtspProtoIdSubStream();
	private @Nullable RtspProtoSetupInfoForSubStream siSsPtr1 = null;  // store pointer since it contains UDP sockets
	private final @NonNull RtspProtoIdSubStream idSubStream2 = new RtspProtoIdSubStream();
	private @Nullable RtspProtoSetupInfoForSubStream siSsPtr2 = null;  // store pointer since it contains UDP sockets

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void createAndAddSetupSubStream(
				@NonNull RtspProtoRscUrl rscUrlSubStream,
				int ssrcId,
				@NonNull RtspProtoKmdForSubStream kmdOutbound
			) {
		if (rscUrlSubStream.getUrlStr().isEmpty()) {
			throw new IllegalArgumentException("Resource URL must be set");
		}
		if (rscUrlSubStream.idInputSource.isEmpty()) {
			throw new IllegalArgumentException("Input Source ID must be set");
		}
		if (rscUrlSubStream.idStreamSource.isEmpty()) {
			throw new IllegalArgumentException("Stream Source ID must be set");
		}
		if (rscUrlSubStream.idSubStream.isEmpty()) {
			throw new IllegalArgumentException("Sub-Stream ID must be set");
		}
		if (ssrcId == 0) {
			throw new IllegalArgumentException("ssrcId must not be 0");
		}

		SrtxpKmd tmpKmd = null;
		if (kmdOutbound.isKmdSet()) {
			tmpKmd = kmdOutbound.getKmd().orElseThrow();
		}
		if (tmpKmd != null && tmpKmd.ssrcId() == 0) {
			throw new IllegalArgumentException("kmdOutbound.ssrcId must not be 0");
		}
		if (tmpKmd != null && tmpKmd.ssrcId() != ssrcId) {
			throw new IllegalArgumentException("kmdOutbound.ssrcId must match ssrcId");
		}
		RtspProtoSetupInfoForSubStream resObj = new RtspProtoSetupInfoForSubStream(
				rscUrlSubStream,
				ssrcId,
				RandomHelper.getRandomUint16(),
				RandomHelper.getRandomUint32(true),
				System.nanoTime()
			);
		if (tmpKmd != null) {
			resObj.getKmdOutboundPtr().setKmd(tmpKmd, rscUrlSubStream.idSubStream);
		}
		//
		putInfoForSubStream(resObj);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean containsSiForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			return true;
		}
		return (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean haveSetupForSubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1) && siSsPtr1 != null) {
			return siSsPtr1.getHaveSetup();
		}
		if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2) && siSsPtr2 != null) {
			return siSsPtr2.getHaveSetup();
		}
		return false;
	}

	public Optional<RtspProtoSetupInfoForSubStream> getSiBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			return Optional.ofNullable(siSsPtr1);
		}
		if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			return Optional.ofNullable(siSsPtr2);
		}
		return Optional.empty();
	}

	public Optional<RtspProtoRscUrl> getResourceUrlBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		RtspProtoRscUrl resObj = null;
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			resObj = (siSsPtr1 == null ? null : siSsPtr1.getRscUrlSubStreamPtr());
		} else if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			resObj = (siSsPtr2 == null ? null : siSsPtr2.getRscUrlSubStreamPtr());
		}
		if (resObj == null) {
			return Optional.empty();
		}
		return Optional.of(resObj.clone());
	}

	public Optional<Integer> getSsrcBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		Integer resObj = null;
		if (! idSubStream1.isEmpty() && idSubStream.equals(idSubStream1)) {
			resObj = (siSsPtr1 == null ? null : siSsPtr1.rtspSsrcId);
		} else if (! idSubStream2.isEmpty() && idSubStream.equals(idSubStream2)) {
			resObj = (siSsPtr2 == null ? null : siSsPtr2.rtspSsrcId);
		}
		if (resObj == null) {
			return Optional.empty();
		}
		return Optional.of(resObj);
	}

	@SuppressWarnings("unused")
	public int getNumberOfSubStreams() {
		return (! idSubStream1.isEmpty() ? 1 : 0) + (! idSubStream2.isEmpty() ? 1 : 0);
	}

	@SuppressWarnings("unused")
	public @NonNull Set<@NonNull RtspProtoIdSubStream> getSubStreamIds() {
		Set<@NonNull RtspProtoIdSubStream> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty()) {
			resSet.add(idSubStream1);
		}
		if (! idSubStream2.isEmpty()) {
			resSet.add(idSubStream2);
		}
		return resSet;
	}

	public @NonNull Set<@NonNull RtspProtoIdStreamSource> getStreamSourceIds() {
		Set<@NonNull RtspProtoIdStreamSource> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty() && siSsPtr1 != null) {
			resSet.add(siSsPtr1.getRscUrlSubStreamPtr().idStreamSource.clone());
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null) {
			resSet.add(siSsPtr2.getRscUrlSubStreamPtr().idStreamSource.clone());
		}
		return resSet;
	}

	public @NonNull Set<RtspProtoRscUrl> getRscUrls() {
		Set<@NonNull RtspProtoRscUrl> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty() && siSsPtr1 != null) {
			resSet.add(siSsPtr1.getRscUrlSubStreamPtr().clone());
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null) {
			resSet.add(siSsPtr2.getRscUrlSubStreamPtr().clone());
		}
		return resSet;
	}

	public @NonNull Set<@NonNull RtspProtoSetupInfoForSubStream> getSis() {
		Set<@NonNull RtspProtoSetupInfoForSubStream> resSet = new HashSet<>();
		if (! idSubStream1.isEmpty() && siSsPtr1 != null) {
			resSet.add(siSsPtr1);
		}
		if (! idSubStream2.isEmpty() && siSsPtr2 != null) {
			resSet.add(siSsPtr2);
		}
		return resSet;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoSetupInfosStream other) {
		idSubStream1.copyFrom(other.idSubStream1);
		siSsPtr1 = other.siSsPtr1;  // copy pointer since it contains UDP sockets
		idSubStream2.copyFrom(other.idSubStream2);
		siSsPtr2 = other.siSsPtr2;  // copy pointer since it contains UDP sockets
	}

	public void clear() {
		idSubStream1.clear();
		siSsPtr1 = null;
		idSubStream2.clear();
		siSsPtr2 = null;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		boolean have1 = ! idSubStream1.isEmpty();
		boolean have2 = ! idSubStream2.isEmpty();

		return getClass().getSimpleName() + " [" +
				"siSubStream1=" + (have1 ? siSsPtr1 : "-") +
				", subStreamId1=" + (have1 ? "'" + idSubStream1.getIdStr() + "'" : "-") +
				", siSubStream2=" + (have2 ? siSsPtr2 : "-") +
				", subStreamId2=" + (have2 ? "'" + idSubStream2.getIdStr() + "'" : "-") +
				"]";
	}

	@Override
	public RtspProtoSetupInfosStream clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void putInfoForSubStream(@NonNull RtspProtoSetupInfoForSubStream siForSs) {
		if (siForSs.getRscUrlSubStreamPtr().idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		boolean isTrg1;
		if (! idSubStream1.isEmpty() && idSubStream1.equals(siForSs.getRscUrlSubStreamPtr().idSubStream)) {
			isTrg1 = true;
		} else if (! idSubStream2.isEmpty() && idSubStream2.equals(siForSs.getRscUrlSubStreamPtr().idSubStream)) {
			isTrg1 = false;
		} else {
			isTrg1 = idSubStream1.isEmpty();
		}
		if (isTrg1) {
			siSsPtr1 = siForSs;
			idSubStream1.copyFrom(siForSs.getRscUrlSubStreamPtr().idSubStream);
		} else {
			siSsPtr2 = siForSs;
			idSubStream2.copyFrom(siForSs.getRscUrlSubStreamPtr().idSubStream);
		}
	}

}
