package org.tsitle.lib_dataprov.threads_es.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_v_vpx.FrameGrabberVideoVp8FromEsFile;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvFromFileBase;

public final class ThreadDataProvVp8FromFile extends ThreadDataProvFromFileBase<VideoVp8Info> {

	private final @NonNull PacketParserVp8 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvVp8FromFile(
				@NonNull ParamsThreadDpCommon paramsCommon,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles,
				false
			);

		this.packetParser = new PacketParserVp8();
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
		this.frameGrabber = new FrameGrabberVideoVp8FromEsFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull VideoVp8Info parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull VideoVp8Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(debugStreamOffset, inputBv);
	}

}
