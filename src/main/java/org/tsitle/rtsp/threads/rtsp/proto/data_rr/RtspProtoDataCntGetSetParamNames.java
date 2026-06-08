package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public final class RtspProtoDataCntGetSetParamNames {

	/** Parameter names */
	public final @NonNull Set<@NonNull String> paramNames = new HashSet<>();

	public void clear() {
		paramNames.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntGetSetParamNames other) {
		if (other == this) {
			return;
		}
		paramNames.clear();
		paramNames.addAll(other.paramNames);
	}

}
