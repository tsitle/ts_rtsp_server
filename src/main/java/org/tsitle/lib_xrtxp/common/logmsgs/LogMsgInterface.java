package org.tsitle.lib_xrtxp.common.logmsgs;

import org.jspecify.annotations.NonNull;

public interface LogMsgInterface {

	void addMsgForLogThread(
			@NonNull RtxpLogLevel logLevel,
			@NonNull String threadId,
			@NonNull String msg
		);

}
