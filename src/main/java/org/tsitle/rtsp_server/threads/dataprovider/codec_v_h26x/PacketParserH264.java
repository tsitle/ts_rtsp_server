package org.tsitle.rtsp_server.threads.dataprovider.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.lib_xrtxp.avdata.VideoH264Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PictureBoundaryInfo;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264SpsContext;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

import java.util.HashMap;
import java.util.Map;

final class PacketParserH264 {

	private final @NonNull VideoH264Parser pktParser;
	private @Nullable H264PictureBoundaryInfo cachePictBoundInfoPrev = null;

	PacketParserH264() {
		Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
		Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
		this.pktParser = new VideoH264Parser(mapSpsContext, mapPpsContext);
	}

	@NonNull VideoH264Info parseData(long debugStreamOffset, @NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		int magicBytesLength = MagicBytesH26xHelper.findH26xMagicBytesLength(inputBv);
		//
		VideoH264Info curFrameH264Info = pktParser.parseH264Data(
				debugStreamOffset,
				magicBytesLength,
				inputBv,
				cachePictBoundInfoPrev
			);
		if (curFrameH264Info.isVclNalUnit) {
			cachePictBoundInfoPrev = curFrameH264Info.pictBoundInfo.clone();
		}

		return curFrameH264Info;
	}

}
