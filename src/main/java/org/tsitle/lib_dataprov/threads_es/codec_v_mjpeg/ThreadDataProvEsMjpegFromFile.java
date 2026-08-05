package org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg.VideoJpegInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_dataprov.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsFromFileBase;

public final class ThreadDataProvEsMjpegFromFile extends ThreadDataProvEsFromFileBase<VideoJpegInfo> {

	private final @NonNull PacketPacMjpeg packetPac;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvEsMjpegFromFile(
				@NonNull ParamsThreadDpCommon paramsCommon,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles,
				true
			);

		this.packetPac = new PacketPacMjpeg(
				paramsCommon.getLogMsgInterface().orElseThrow()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		if (congestionLevel < 0 || congestionLevel > 4) {
			throw new IllegalArgumentException("congestionLevel must be in range 0..4");
		}
		/*
		 * CL 0 --> CQ 100%
		 * CL 1 --> CQ  85%
		 * CL 2 --> CQ  70%
		 * CL 3 --> CQ  55%
		 * CL 4 --> CQ  40%
		 */
		packetPac.setCompressionQuality(1.0f - (0.15f * (float)congestionLevel));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberVideoMjpegFromEsFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull VideoJpegInfo parseAndConvertData(@NonNull BufferExt ioBuf) throws AvInvalidCodecDataException {
		return packetPac.parseAndConvertData(debugStreamOffset, ioBuf);
	}

	@Override
	protected @NonNull VideoJpegInfo parseData(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseData(): not implemented");
	}

}
