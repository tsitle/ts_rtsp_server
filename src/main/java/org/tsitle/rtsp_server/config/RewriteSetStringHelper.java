package org.tsitle.rtsp_server.config;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

final class RewriteSetStringHelper {

	private RewriteSetStringHelper() { }

	static @NonNull Set<@NonNull String> removeNullAndBlank(
				@Nullable Set<String> input,
				@SuppressWarnings("SameParameterValue") boolean convToLowercase
			) {
		if (input == null) {
			return new HashSet<>();
		}
		Set<String> resSet = new HashSet<>();
		for (String entry : input) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			resSet.add(convToLowercase ? entry.toLowerCase() : entry);
		}
		return resSet;
	}

}
