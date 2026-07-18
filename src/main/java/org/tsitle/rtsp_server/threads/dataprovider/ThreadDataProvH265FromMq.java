package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvH265FromMq extends ThreadDataProvFromMqBase<VideoH265Info> {

	private @Nullable VideoH265Parser h265Parser = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvH265FromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberVideoH26xFromMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
		this.h265Parser = new VideoH265Parser();
	}

	@Override
	protected @NonNull VideoH265Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (h265Parser == null) {
			throw new IllegalStateException("h265Parser is null");
		}

		int magicBytesLength = MagicBytesH26xHelper.findH26xMagicBytesLength(inputBuf);
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
	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return MagicBytesH26xHelper.findH26xNextNalUnit(inputBuf);
	}

}
