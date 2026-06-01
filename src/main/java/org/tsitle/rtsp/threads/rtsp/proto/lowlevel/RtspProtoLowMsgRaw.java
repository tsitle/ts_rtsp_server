package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class RtspProtoLowMsgRaw {

	public boolean readSuccess = false;

	public @NonNull String mainLine = "";
	public @NonNull List<@NonNull String> headerLines = new ArrayList<>();

}
