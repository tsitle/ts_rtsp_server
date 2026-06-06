package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public class RtspProtoHeaderTypeRequireBase {

	public @NonNull Set<@NonNull String> requiredFeatures = new HashSet<>();

	protected RtspProtoHeaderTypeRequireBase() { }

	@Override
	public @NonNull String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append("requiredFeatuers={");
		boolean isFirst = true;
		for (String tmpEntry : requiredFeatures) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry);
			isFirst = false;
		}
		sb.append("}]");
		return sb.toString();
	}

}
