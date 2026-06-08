package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

public final class RtspProtoDataCntGetSetParamKvs {

	/** Content language(s) (e.g. 'de') */
	public @NonNull String contentLang = "";

	/** Parameter key-value-pairs */
	public final @NonNull Map<@NonNull String, @NonNull String> paramKvs = new HashMap<>();

	public void clear() {
		contentLang = "";
		paramKvs.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntGetSetParamKvs other) {
		if (other == this) {
			return;
		}
		contentLang = other.contentLang;
		paramKvs.clear();
		paramKvs.putAll(other.paramKvs);
	}

}
