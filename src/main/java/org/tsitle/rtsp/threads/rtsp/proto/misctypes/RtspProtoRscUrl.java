package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;

public final class RtspProtoRscUrl implements Cloneable {

	private boolean isWriteProtected = false;

	public @NonNull RtspProtoIdInputSource idInputSource = new RtspProtoIdInputSource();
	public @NonNull RtspProtoIdStreamSource idStreamSource = new RtspProtoIdStreamSource();
	public @NonNull RtspProtoIdSubStream idSubStream = new RtspProtoIdSubStream();
	private @NonNull String urlStr = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUrlStr() {
		return urlStr;
	}
	public void setUrlStr(@NonNull String urlStr) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (urlStr.isBlank()) {
			throw new IllegalArgumentException("urlStr cannot be blank");
		}
		this.urlStr = urlStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		idInputSource.clear();
		idStreamSource.clear();
		idSubStream.clear();
		urlStr = "";
	}

	public boolean isEmpty() {
		return idInputSource.isEmpty() && idStreamSource.isEmpty() && idSubStream.isEmpty() && urlStr.isEmpty();
	}

	public void copyFrom(@NonNull RtspProtoRscUrl other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		idInputSource.copyFrom(other.idInputSource);
		idStreamSource.copyFrom(other.idStreamSource);
		idSubStream.copyFrom(other.idSubStream);
		urlStr = other.urlStr;
	}

	public void writeProtect() {
		isWriteProtected = true;

		idInputSource.writeProtect();
		idStreamSource.writeProtect();
		idSubStream.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoRscUrl clone() {
		try {
			RtspProtoRscUrl cloned = (RtspProtoRscUrl)super.clone();
			cloned.idInputSource = idInputSource.clone();
			cloned.idStreamSource = idStreamSource.clone();
			cloned.idSubStream = idSubStream.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

}
