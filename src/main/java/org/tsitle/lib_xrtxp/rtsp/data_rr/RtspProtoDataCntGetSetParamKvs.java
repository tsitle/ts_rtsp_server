package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;

import java.util.*;

public final class RtspProtoDataCntGetSetParamKvs {

	private boolean isWriteProtected = false;

	/** Content language(s) (e.g. 'de') */
	private @NonNull String contentLang = "";

	/** Parameter key-value-pairs */
	private final @NonNull Map<@NonNull String, @NonNull String> paramKvs = new HashMap<>();

	private final @NonNull RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	private final @NonNull RtspProtoIdSubStream idSubStream = RtspProtoIdSubStream.ofEmpty();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getContentLang() {
		return contentLang;
	}
	public void setContentLang(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.contentLang = value;
	}

	public boolean isParamKvsEmpty() {
		return paramKvs.isEmpty();
	}
	public Optional<String> getParamKvsValue(@NonNull String key) {
		if (! paramKvs.containsKey(key)) {
			return Optional.empty();
		}
		return Optional.of(paramKvs.get(key));
	}
	public @NonNull Set<@NonNull String> getParamKvsKeySet() {
		return new HashSet<@NonNull String>(paramKvs.keySet());
	}
	public @NonNull Set<Map.@NonNull Entry<@NonNull String, @NonNull String>> getParamKvsEntrySet() {
		return new HashSet<>(paramKvs.entrySet());
	}
	public void putParamKvsEntry(@NonNull String key, @NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		paramKvs.put(key, value);
	}

	public @NonNull RtspProtoIdInputSource getIdInputSource() {
		return idInputSource.clone();
	}
	public void setIdInputSource(@NonNull RtspProtoIdInputSource idInputSource) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.idInputSource.copyFrom(idInputSource);
	}

	public @NonNull RtspProtoIdSubStream getIdSubStream() {
		return idSubStream.clone();
	}
	public void setIdSubStream(@NonNull RtspProtoIdSubStream idSubStream) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.idSubStream.copyFrom(idSubStream);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		contentLang = "";
		paramKvs.clear();
		idInputSource.clear();
		idSubStream.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntGetSetParamKvs other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		contentLang = other.contentLang;
		paramKvs.clear();
		paramKvs.putAll(other.paramKvs);
		idInputSource.copyFrom(other.idInputSource);
		idSubStream.copyFrom(other.idSubStream);
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	private static @NonNull String mapToString(@NonNull Map<@NonNull String, @NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (Map.Entry<@NonNull String, @NonNull String> tmpEntry : input.entrySet()) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(tmpEntry.getKey()).append("'=");
			/*if (tmpEntry.getValue() == null) {
				sb.append("unset");
			} else {*/
				sb.append("'").append(tmpEntry.getValue()).append("'");
			/*}*/
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"contentLang='" + contentLang + "'" +
				", paramKvs=" + mapToString(paramKvs) +
				", idInputSource=" + idInputSource +
				", idSubStream=" + idSubStream +
				"]";
	}

}
