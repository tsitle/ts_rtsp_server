package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeContLang {

	public @NonNull String contentLangStr = "";

	@Override
	public @NonNull String toString() {
		return "[contentLangStr='" + contentLangStr + "']";
	}

}
