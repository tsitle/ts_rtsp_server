package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.VideoStreamOutgoingH26xFromFile;
import org.tsitle.rtsp_server.avstreams.VideoStreamOutgoingH26xFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public class ThreadDataProvH265FromMq extends ThreadDataProvFromMqBase<VideoH265Info> {

	private final VideoH265Parser h265Parser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvH265FromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, true);

		//
		paramsVideoCommon.validate();

		//
		this.mediaOutgoingStream = new VideoStreamOutgoingH26xFromMq(logMsgInterface, avStreamIncoming);
		this.h265Parser = new VideoH265Parser();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull VideoH265Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (magicBytesLength == -1) {
			magicBytesLength = findH26xMagicBytesLength(inputBuf);
			magicBytesArrPtr = (magicBytesLength == 3 ?
					VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_3 :
					VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_4);
		}
		//
		VideoH265Info curFrameH265Info = h265Parser.parseH265Data(
				debugStreamOffset,
				magicBytesLength,
				inputBuf
			);
		//
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameH265Info;
	}

	@Override
	protected int findNextMagicBytes(final BufferExt inputBuf) {
		return findH26xNextNalUnit(inputBuf);
	}

}
