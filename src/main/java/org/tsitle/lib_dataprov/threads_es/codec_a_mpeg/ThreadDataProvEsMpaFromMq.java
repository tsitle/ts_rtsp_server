package org.tsitle.lib_dataprov.threads_es.codec_a_mpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.codec_a_mpeg.FrameGrabberAudioMpegFromEsMq;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromMqBase;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class ThreadDataProvEsMpaFromMq extends ThreadDataProvEsFromMqBase<AudioMpegInfo> {

	private final @NonNull PacketParserMpa packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvEsMpaFromMq(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, false, false, true);

		this.packetParser = new PacketParserMpa();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioMpegFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioMpegInfo parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioMpegInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioMpegInfo avInfo) {
		return avInfo.frameLength;
	}

}
