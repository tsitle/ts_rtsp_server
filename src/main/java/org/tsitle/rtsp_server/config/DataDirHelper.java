package org.tsitle.rtsp_server.config;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

final class DataDirHelper {

	private DataDirHelper() { }

	static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

}
