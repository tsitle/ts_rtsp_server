package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingBase;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;

final class BuilderThreadRtpSenderHelper {

	private BuilderThreadRtpSenderHelper() { }

	static Class<? extends AvStreamIncomingBase> getAvStreamIncomingType(@NonNull RtspProtoEsSourceType esSourceType) {
		return switch (esSourceType) {
				case ST_ES_FILE -> AvStreamIncomingFromEsFile.class;
				case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> AvStreamIncomingFromDemuxMs.class;
				default -> AvStreamIncomingFromEsMq.class;
			};
	}

}
