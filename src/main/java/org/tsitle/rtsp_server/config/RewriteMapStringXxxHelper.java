package org.tsitle.rtsp_server.config;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class RewriteMapStringXxxHelper {

	private RewriteMapStringXxxHelper() { }

	static <T> @NonNull Map<@NonNull String, @NonNull T> removeNullAndBlank(
				@Nullable Map<String, T> input,
				@SuppressWarnings("SameParameterValue") boolean convIdToLowercase
			) {
		if (input == null) {
			return new HashMap<>();
		}
		Map<@NonNull String, @NonNull T> resMap = new HashMap<>();
		for (Map.Entry<String, T> entry : input.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
				continue;
			}
			resMap.put(
					(convIdToLowercase ? entry.getKey().toLowerCase() : entry.getKey()).strip(),
					entry.getValue()
				);
		}
		return resMap;
	}

}
