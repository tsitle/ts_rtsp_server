package org.tsitle.rtsp.threads.logging;

import org.jspecify.annotations.NonNull;

public enum RtxpLogLevel {

	DEBUG, INFO, WARN, ERROR;

	public static boolean isValid(@NonNull String level) {
		return (DEBUG.name().equalsIgnoreCase(level) || INFO.name().equalsIgnoreCase(level) ||
				WARN.name().equalsIgnoreCase(level) || ERROR.name().equalsIgnoreCase(level));
	}

	public static @NonNull RtxpLogLevel of(@NonNull String level) {
		return switch (level.toUpperCase()) {
				case "DEBUG" -> DEBUG;
				case "INFO" -> INFO;
				case "WARN" -> WARN;
				case "ERROR" -> ERROR;
				default -> throw new IllegalArgumentException("Invalid log level: '" + level + "'");
			};
	}

}
