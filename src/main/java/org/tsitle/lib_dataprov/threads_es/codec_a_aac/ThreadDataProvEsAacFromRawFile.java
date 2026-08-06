package org.tsitle.lib_dataprov.threads_es.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsRawFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromRawFileBase;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAac;

public final class ThreadDataProvEsAacFromRawFile extends ThreadDataProvEsFromRawFileBase<AudioAacInfo> {

	private final @NonNull PacketParserAac packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsAac Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvEsAacFromRawFile(
				@NonNull ParamsThreadDpCommon paramsCommon,
				@NonNull ParamsThreadDpAac paramsAac,
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
		paramsAac.validate();
		//
		this.packetParser = new PacketParserAac();
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
		this.frameGrabber = new FrameGrabberAudioAacFromEsRawFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioAacInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

}
