package org.tsitle.rtsp_server.threads.dataprovider.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.codec_v_h26x.FrameGrabberVideoH26xFromEsMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvFromMqBase;
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
		super(paramsCommon, true, false, false);

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
	protected @NonNull VideoH264Info parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull VideoH264Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(debugStreamOffset, inputBv);
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferView inputBv) {
		return MagicBytesH26xHelper.findH26xNextNalUnit(inputBv);
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull VideoH264Info avInfo) {
		throw new RuntimeException(getClass().getSimpleName() + ".readFrameLenFromAvInfo(): not implemented");
	}

}
