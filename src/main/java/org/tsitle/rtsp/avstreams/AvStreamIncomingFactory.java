package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdStreamSource;

import java.net.URI;

public class AvStreamIncomingFactory {

	public static <AVSTRIC extends AvStreamIncomingBase> AVSTRIC createAvStreamIncoming(
				Class<AVSTRIC> type,
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdStreamSource idStreamSource,
				@NonNull URI inputUri
			) throws AvCannotOpenInputException {
		if (type == AvStreamIncomingFromFile.class) {
			return type.cast(new AvStreamIncomingFromFile(logMsgInterface, idStreamSource, inputUri));
		}
		if (type == AvStreamIncomingFromMq.class) {
			return type.cast(new AvStreamIncomingFromMq(logMsgInterface, idStreamSource, inputUri));
		}
		throw new IllegalArgumentException("Unsupported type: " + type.getName());
	}

}
