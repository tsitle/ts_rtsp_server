package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

import java.util.Objects;

/**
 * Container for Resource URLs and the respective Input Source ID and Sub-Stream ID.
 */
public final class RtspProtoRscUrl implements Cloneable {

	private boolean isWriteProtected = false;

	private @NonNull String urlStr = "";
	public @NonNull RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	public @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();

	private RtspProtoRscUrl() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspProtoRscUrl ofEmpty() {
		return new RtspProtoRscUrl();
	}

	public static @NonNull RtspProtoRscUrl of(@NonNull String urlStr) {
		RtspProtoRscUrl obj = new RtspProtoRscUrl();
		obj.setUrlStr(urlStr);
		return obj;
	}

	public static @NonNull RtspProtoRscUrl of(@NonNull String urlStr, @NonNull RtspProtoIdInputSource idInputSource) {
		RtspProtoRscUrl obj = RtspProtoRscUrl.of(urlStr);
		obj.idInputSource.copyFrom(idInputSource);
		return obj;
	}

	public static @NonNull RtspProtoRscUrl of(
				@NonNull String urlStr,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdSubStream idSubStream
			) {
		RtspProtoRscUrl obj = of(urlStr, idInputSource);
		obj.idSubStream.copyFrom(idSubStream);
		return obj;
	}

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
		idSubStream.clear();
		urlStr = "";
	}

	public boolean isEmpty() {
		return idInputSource.isEmpty() && idSubStream.isEmpty() && urlStr.isEmpty();
	}

	public void copyFrom(@NonNull RtspProtoRscUrl other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		idInputSource.copyFrom(other.idInputSource);
		idSubStream.copyFrom(other.idSubStream);
		urlStr = other.urlStr;
	}

	public void writeProtect() {
		isWriteProtected = true;

		idInputSource.writeProtect();
		idSubStream.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoRscUrl clone() {
		try {
			RtspProtoRscUrl cloned = (RtspProtoRscUrl)super.clone();
			cloned.idInputSource = idInputSource.clone();
			cloned.idSubStream = idSubStream.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"idInputSource=" + idInputSource +
				", idSubStream=" + idSubStream +
				", urlStr=" + urlStr +
				"]";
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoRscUrl that)) {
			return false;
		}
		return (Objects.equals(urlStr, that.urlStr) && Objects.equals(idInputSource, that.idInputSource) &&
				Objects.equals(idSubStream, that.idSubStream));
	}

	@Override
	public int hashCode() {
		return Objects.hash(urlStr, idInputSource, idSubStream);
	}

}
