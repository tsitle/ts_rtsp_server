package org.tsitle.lib.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;

/**
 * Sub-Stream settings as announced in the SDP (either in ANNOUNCE request or DESCRIBE response).
 */
public final class RtspProtoAdSettingsForSubStream implements Cloneable {

	private boolean isWriteProtected = false;

	public @NonNull RtspProtoIdStreamSource idStreamSource = RtspProtoIdStreamSource.ofEmpty();
	public @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();
	public @NonNull RtspProtoIdXsrc ssrcId = RtspProtoIdXsrc.ofEmpty();
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

	public void copyFrom(@NonNull RtspProtoAdSettingsForSubStream other) {
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
	public RtspProtoAdSettingsForSubStream clone() {
		try {
			RtspProtoAdSettingsForSubStream cloned = (RtspProtoAdSettingsForSubStream)super.clone();
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
