package org.tsitle.lib_dataprov.threads_es.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_a_opus.FrameGrabberAudioOpusFromEsRawFile;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromRawFileBase;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpOpus;

public final class ThreadDataProvEsOpusFromRawFile extends ThreadDataProvEsFromRawFileBase<AudioOpusInfo> {

	private final @NonNull PacketParserOpus packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsOpus Thread-specific parameters
	 * @param queueSize Size of the input queue
	 */
	public ThreadDataProvEsOpusFromRawFile(
				@NonNull ParamsThreadDpCommon paramsCommon,
				@NonNull ParamsThreadDpOpus paramsOpus,
				int queueSize
			) {
		super(
				paramsCommon,
				queueSize,
				false
			);

		//
		paramsOpus.validate();
		//
		this.packetParser = new PacketParserOpus();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
	public synchronized void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioOpusFromEsRawFile(
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

}
