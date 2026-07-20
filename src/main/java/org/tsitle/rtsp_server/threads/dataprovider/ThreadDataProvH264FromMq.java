package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromEsMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvH264FromMq extends ThreadDataProvFromMqBase<VideoH264Info> {

	private final @NonNull PacketParserH264 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvH264FromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, true);

		this.packetParser = new PacketParserH264();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberVideoH26xFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull VideoH264Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return packetParser.parseAndConvertData(debugStreamOffset, inputBuf);
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return MagicBytesH26xHelper.findH26xNextNalUnit(inputBuf);
	}

}
