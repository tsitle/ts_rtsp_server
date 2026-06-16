package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Stream settings as announced in the SDP (either in ANNOUNCE request or DESCRIBE response).
 */
public final class RtspProtoDataCntAdStreamSett {

	public static class SubStream implements Cloneable {
		private boolean isWriteProtected = false;

		public @NonNull RtspProtoIdStreamSource idStreamSource = new RtspProtoIdStreamSource();
		public @NonNull RtspProtoIdSubStream idSubStream = new RtspProtoIdSubStream();
		public @NonNull RtspProtoIdXsrc ssrcId = new RtspProtoIdXsrc();
		private @NonNull String urlSubPathForSubStream = "";

		public @NonNull String getUrlSubPathForSubStream() {
			return urlSubPathForSubStream;
		}
		public void setUrlSubPathForSubStream(@NonNull String urlSubPathForSubStream) {
			if (isWriteProtected) {
				throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
			}
			this.urlSubPathForSubStream = urlSubPathForSubStream;
		}

		public void clear() {
			if (isWriteProtected) {
				throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
			}
			idStreamSource.clear();
			idSubStream.clear();
			ssrcId.clear();
			urlSubPathForSubStream = "";
		}

		public void writeProtect() {
			isWriteProtected = true;

			idStreamSource.writeProtect();
			idSubStream.writeProtect();
			ssrcId.writeProtect();
		}

		public void copyFrom(@NonNull SubStream other) {
			if (isWriteProtected) {
				throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
			}
			if (other == this) {
				return;
			}
			idStreamSource.copyFrom(other.idStreamSource);
			idSubStream.copyFrom(other.idSubStream);
			ssrcId.copyFrom(other.ssrcId);
			urlSubPathForSubStream = other.urlSubPathForSubStream;
		}

		@Override
		public SubStream clone() {
			try {
				SubStream cloned = (SubStream)super.clone();
				cloned.idStreamSource = idStreamSource.clone();
				cloned.idSubStream = idSubStream.clone();
				cloned.ssrcId = ssrcId.clone();
				return cloned;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

		@Override
		public @NonNull String toString() {
			return "[" +
					"idStreamSource=" + (idStreamSource.isEmpty() ? "-" : "'" + idStreamSource.getIdStr() + "'") +
					", idSubStream=" + (idSubStream.isEmpty() ? "-" : "'" + idSubStream.getIdStr() + "'") +
					", ssrcId=" + (ssrcId.isEmpty() ? "-" : ssrcId.toHexString(true)) +
					", urlSubPathForSubStream='" + urlSubPathForSubStream + "'" +
					"]";
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean isWriteProtected = false;

	private final @NonNull RtspProtoIdInputSource idInputSource = new RtspProtoIdInputSource();
	private final @NonNull SubStream ss1 = new SubStream();
	private final @NonNull SubStream ss2 = new SubStream();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setIdInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.idInputSource.copyFrom(idInputSource);
	}

	public void putSettingsForSubStream(@NonNull SubStream settings) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (settings.idSubStream.isEmpty()) {
			throw new IllegalArgumentException("idSubStream cannot be empty");
		}
		SubStream trg;
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

	public Optional<SubStream> getSettingsByStreamSourceId(@NonNull RtspProtoIdStreamSource idStreamSource) {
		if (idStreamSource.isEmpty()) {
			return Optional.empty();
		}
		SubStream resObj;
		if (! ss1.idStreamSource.isEmpty() && idStreamSource.equals(ss1.idStreamSource)) {
			resObj = ss1;
		} else if (! ss2.idStreamSource.isEmpty() && idStreamSource.equals(ss2.idStreamSource)) {
			resObj = ss2;
		} else {
			return Optional.empty();
		}

		return Optional.of(resObj.clone());
	}

	@SuppressWarnings("unused")
	public Optional<SubStream> getSettingsBySubStreamId(@NonNull RtspProtoIdSubStream idSubStream) {
		if (idSubStream.isEmpty()) {
			return Optional.empty();
		}
		SubStream resObj;
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
