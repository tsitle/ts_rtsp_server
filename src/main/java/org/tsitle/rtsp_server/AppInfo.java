package org.tsitle.rtsp_server;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

final class AppInfo {

	private static final @Nullable String APP_VERSION = System.getProperty("appVersion");

	static Optional<String> getAppVersion() {
		return Optional.ofNullable(APP_VERSION);
	}

}
