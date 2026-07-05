package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;

import java.net.URI;

public final class AvStreamIncomingFactory {

	private AvStreamIncomingFactory() { }

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
