package org.tsitle.lib_dataprov.threads_es.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.codec_a_mp3.FrameGrabberAudioMp3FromEsRawFile;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMp3;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromRawFileBase;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class ThreadDataProvEsMp3FromRawFile extends ThreadDataProvEsFromRawFileBase<AudioMp3Info> {

	private final @NonNull PacketParserMp3 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsMp3 Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvEsMp3FromRawFile(
				@NonNull ParamsThreadDpCommon paramsCommon,
				@NonNull ParamsThreadDpMp3 paramsMp3,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles,
				false
			);

		//
		paramsMp3.validate();
		//
		this.packetParser = new PacketParserMp3();
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
		this.frameGrabber = new FrameGrabberAudioMp3FromEsRawFile(
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

}
