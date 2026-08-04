package org.tsitle.lib_dataprov.threads_es.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvFromDemuxMsBase;

public final class ThreadDataProvAc3FromDemuxMs extends ThreadDataProvFromDemuxMsBase<AudioAc3Info> {

	private final @NonNull PacketParserAc3 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvAc3FromDemuxMs(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, false, false, true);

		this.packetParser = new PacketParserAc3();
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
	protected @NonNull AudioAc3Info parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioAc3Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioAc3Info avInfo) {
		return avInfo.frameLength;
	}

}
