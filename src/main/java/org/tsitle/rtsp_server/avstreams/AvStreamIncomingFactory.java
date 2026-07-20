package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.rtsp_server.threads.dataprovider_demux.TdpDemuxReadNextAvPacketInterface;

import java.net.URI;

// @TODO delete class
public final class AvStreamIncomingFactory {

	private AvStreamIncomingFactory() { }

	public static <AVSTRIC extends AvStreamIncomingBase> AVSTRIC createAvStreamIncoming(
				Class<AVSTRIC> type,
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull URI inputUri,
				boolean isVideo,
				@Nullable TdpDemuxReadNextAvPacketInterface demuxReadNextAvPacketInterface
			) throws AvCannotOpenInputException {
		if (type == AvStreamIncomingFromEsFile.class) {
			return type.cast(new AvStreamIncomingFromEsFile(logMsgInterface, idEsSource, inputUri));
		}
		if (type == AvStreamIncomingFromEsMq.class) {
			return type.cast(new AvStreamIncomingFromEsMq(logMsgInterface, idEsSource, inputUri));
		}
		if (type == AvStreamIncomingFromDemuxMs.class) {
			if (demuxReadNextAvPacketInterface == null) {
				throw new IllegalArgumentException(AvStreamIncomingFactory.class.getSimpleName() + ": " +
						"demuxReadNextAvPacketInterface is null");
			}
			return type.cast(new AvStreamIncomingFromDemuxMs(logMsgInterface, idEsSource, isVideo, demuxReadNextAvPacketInterface));
		}
		throw new IllegalArgumentException(AvStreamIncomingFactory.class.getSimpleName() + ": Unsupported type: " + type.getName());
	}

}
