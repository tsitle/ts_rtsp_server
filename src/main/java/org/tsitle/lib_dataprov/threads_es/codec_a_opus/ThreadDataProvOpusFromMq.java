package org.tsitle.lib_dataprov.threads_es.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_a_opus.FrameGrabberAudioOpusFromEsMq;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvFromMqBase;

public final class ThreadDataProvOpusFromMq extends ThreadDataProvFromMqBase<AudioOpusInfo> {

	private final @NonNull PacketParserOpus packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvOpusFromMq(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, false, false, true);

		this.packetParser = new PacketParserOpus();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioOpusFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioOpusInfo parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioOpusInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioOpusInfo avInfo) {
		return avInfo.frameLength;
	}

}
