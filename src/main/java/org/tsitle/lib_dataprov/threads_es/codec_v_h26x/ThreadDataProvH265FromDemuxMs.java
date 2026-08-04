package org.tsitle.lib_dataprov.threads_es.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.MagicBytesH26xHelper;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvFromDemuxMsBase;

public final class ThreadDataProvH265FromDemuxMs extends ThreadDataProvFromDemuxMsBase<VideoH265Info> {

	private final @NonNull PacketParserH265 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvH265FromDemuxMs(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, true, false, false);

		this.packetParser = new PacketParserH265();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAvFromDemuxMs(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull VideoH265Info parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull VideoH265Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(debugStreamOffset, inputBv);
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferView inputBv) {
		return MagicBytesH26xHelper.findH26xNextNalUnit(inputBv);
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull VideoH265Info avInfo) {
		throw new RuntimeException(getClass().getSimpleName() + ".readFrameLenFromAvInfo(): not implemented");
	}

}
