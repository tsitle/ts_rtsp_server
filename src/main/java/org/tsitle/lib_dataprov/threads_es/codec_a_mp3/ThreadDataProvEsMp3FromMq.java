package org.tsitle.lib_dataprov.threads_es.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.codec_a_mp3.FrameGrabberAudioMp3FromEsMq;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromMqBase;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class ThreadDataProvEsMp3FromMq extends ThreadDataProvEsFromMqBase<AudioMp3Info> {

	private final @NonNull PacketParserMp3 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvEsMp3FromMq(
				@NonNull ParamsThreadDpCommon paramsCommon
			) {
		super(paramsCommon, false, false, true);

		this.packetParser = new PacketParserMp3();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioMp3FromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioMp3Info parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioMp3Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioMp3Info avInfo) {
		return avInfo.frameLength;
	}

}
