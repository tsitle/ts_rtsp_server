package org.tsitle.rtsp.threads;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;

public interface LogMsgInterface {

	void addMsgForLogThread(
			@NonNull RtxpLogLevel logLevel,
			@NonNull String threadId,
			@NonNull String msg
		);

}
