package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Stream settings as announced in the SDP (either in ANNOUNCE request or DESCRIBE response).
 */
public final class RtspProtoAdSettingsStream {

	private boolean isWriteProtected = false;

	private final @NonNull RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	private final @NonNull RtspProtoAdSettingsForSubStream ss1 = new RtspProtoAdSettingsForSubStream();
	private final @NonNull RtspProtoAdSettingsForSubStream ss2 = new RtspProtoAdSettingsForSubStream();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setIdInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.idInputSource.copyFrom(idInputSource);
	}

	public void putSettingsForSubStream(@NonNull RtspProtoAdSettingsForSubStream settings) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (settings.idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		RtspProtoAdSettingsForSubStream trg;
		if (! ss1.idSubStream.isEmpty() && settings.idSubStream.equals(ss1.idSubStream)) {
			trg = ss1;
		} else if (! ss2.idSubStream.isEmpty() && settings.idSubStream.equals(ss2.idSubStream)) {
			trg = ss2;
		} else if (ss1.idSubStream.isEmpty()) {
			trg = ss1;
		} else {
			trg = ss2;
		}
		trg.copyFrom(settings);
	}

	public Optional<RtspProtoAdSettingsForSubStream> getSettingsByStreamSourceId(@NonNull RtspProtoIdStreamSource idStreamSource) {
		if (idStreamSource.isEmpty()) {
			return Optional.empty();
		}
		RtspProtoAdSettingsForSubStream resObj;
		if (! ss1.idStreamSource.isEmpty() && idStreamSource.equals(ss1.idStreamSource)) {
			resObj = ss1;
		} else if (! ss2.idStreamSource.isEmpty() && idStreamSource.equals(ss2.idStreamSource)) {
			resObj = ss2;
		} else {
			return Optional.empty();
		}

		return Optional.of(resObj.clone());
	}

	public Optional<RtspProtoAdSettingsForSubStream> getSettingsBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		RtspProtoAdSettingsForSubStream resObj;
		if (! ss1.idSubStream.isEmpty() && idSubStream.equals(ss1.idSubStream)) {
			resObj = ss1;
		} else if (! ss2.idSubStream.isEmpty() && idSubStream.equals(ss2.idSubStream)) {
			resObj = ss2;
		} else {
			return Optional.empty();
		}

		return Optional.of(resObj.clone());
	}

	@SuppressWarnings("unused")
	public int getNumberOfSubStreams() {
		return (ss1.idSubStream.isEmpty() ? 0 : 1) + (ss2.idSubStream.isEmpty() ? 0 : 1);
	}

	public @NonNull Set<@NonNull RtspProtoIdSubStream> getSubStreamIds() {
		Set<@NonNull RtspProtoIdSubStream> resSet = new HashSet<>();
		if (! ss1.idSubStream.isEmpty()) {
			resSet.add(ss1.idSubStream);
		}
		if (! ss2.idSubStream.isEmpty()) {
			resSet.add(ss2.idSubStream);
		}
		return resSet;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		idInputSource.clear();
		ss1.clear();
		ss2.clear();
	}

	public void writeProtect() {
		isWriteProtected = true;

		idInputSource.writeProtect();
		ss1.writeProtect();
		ss2.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"idInputSource=" + (idInputSource.isEmpty() ? "-" : "'" + idInputSource.getIdStr() + "'") +
				", subStream1=" + (ss1.idSubStream.isEmpty() ? "-" : ss1) +
				", subStream2=" + (ss2.idSubStream.isEmpty() ? "-" : ss2) +
				"]";
	}

}
