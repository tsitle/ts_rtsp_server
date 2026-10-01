package org.tsitle.rtsp_server;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

final class AppInfo {

	private static final @Nullable String APP_VERSION = System.getProperty("appVersion");
	private static final @Nullable String FFMPEG_VERSION = System.getProperty("ffmpegVersion");
	private static final @Nullable String GIT_COMMIT = System.getProperty("gitCommit");
	private static final @Nullable String IS_NATIVE_IMAGE = System.getProperty("customIsNativeImage");

	static Optional<String> getAppVersion() {
		return Optional.ofNullable(APP_VERSION);
	}

	static Optional<String> getFfmpegVersion() {
		return Optional.ofNullable(FFMPEG_VERSION);
	}

	static Optional<String> getGitCommit() {
		return Optional.ofNullable(GIT_COMMIT);
	}

	static boolean isNativeImage() {
		return (IS_NATIVE_IMAGE != null && IS_NATIVE_IMAGE.equals("true"));
	}

}
