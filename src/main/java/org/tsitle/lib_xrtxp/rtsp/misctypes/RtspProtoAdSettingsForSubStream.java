package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

/**
 * Sub-Stream settings as announced in the SDP (either in ANNOUNCE request or DESCRIBE response).
 */
public final class RtspProtoAdSettingsForSubStream implements Cloneable {

	private boolean isWriteProtected = false;

	public @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();
	public @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();
	public @NonNull RtspProtoIdXsrc ssrcInbound = RtspProtoIdXsrc.ofEmpty();
	public @NonNull RtspProtoIdXsrc ssrcOutbound = RtspProtoIdXsrc.ofEmpty();
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
		idEsSource.clear();
		idSubStream.clear();
		ssrcInbound.clear();
		ssrcOutbound.clear();
		urlSubPathForSubStream = "";
	}

	public void writeProtect() {
		isWriteProtected = true;

		idEsSource.writeProtect();
		idSubStream.writeProtect();
		ssrcInbound.writeProtect();
		ssrcOutbound.writeProtect();
	}

	public void copyFrom(@NonNull RtspProtoAdSettingsForSubStream other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		idEsSource.copyFrom(other.idEsSource);
		idSubStream.copyFrom(other.idSubStream);
		ssrcInbound.copyFrom(other.ssrcInbound);
		ssrcOutbound.copyFrom(other.ssrcOutbound);
		urlSubPathForSubStream = other.urlSubPathForSubStream;
	}

	@Override
	public @NonNull RtspProtoAdSettingsForSubStream clone() {
		try {
			RtspProtoAdSettingsForSubStream cloned = (RtspProtoAdSettingsForSubStream)super.clone();
			cloned.idEsSource = idEsSource.clone();
			cloned.idSubStream = idSubStream.clone();
			cloned.ssrcInbound = ssrcInbound.clone();
			cloned.ssrcOutbound = ssrcOutbound.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				"idEsSource=" + (idEsSource.isEmpty() ? "-" : "'" + idEsSource.getIdStr().orElseThrow() + "'") +
				", idSubStream=" + (idSubStream.isEmpty() ? "-" : "'" + idSubStream.getIdStr().orElseThrow() + "'") +
				", ssrcInbound=" + (ssrcInbound.isEmpty() ? "-" : ssrcInbound.toHexString(true)) +
				", ssrcOutbound=" + (ssrcOutbound.isEmpty() ? "-" : ssrcOutbound.toHexString(true)) +
				", urlSubPathForSubStream='" + urlSubPathForSubStream + "'" +
				"]";
	}

}
