package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class RtspProtoLowMsgRaw {

	public boolean readSuccess = false;

	public @NonNull String mainLine = "";
	public @NonNull List<@NonNull String> headerLines = new ArrayList<>();
	public @NonNull String body = "";

	@Override
	public @NonNull String toString() {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String line : headerLines) {
			if (! isFirst) {
				sb.append(", ");
			}
			line = line.replace("'", "\\'");
			sb.append("'").append(line).append("'");
			isFirst = false;
		}

		return getClass().getSimpleName() + " [" +
				"readSuccess=" + (readSuccess ? "T" : "F") +
				", mainLine='" + mainLine.replace("'", "\\'") + "'" +
				", headerLines={" + sb + "}" +
				", body='" + body
						.replace("'", "\\'")
						.replace("\r\n", "<CRLF>")
						.replace("\r", "<CR>")
						.replace("\n", "<LF>")
						.replace("\t", "<TAB>") + "'" +
				"]";
	}

}
