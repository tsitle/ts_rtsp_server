package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAacFromEsFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAac;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAacFromFile extends ThreadDataProvFromFileBase<AudioAacInfo> {

	private final @NonNull PacketParserAac packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsAac Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvAacFromFile(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAac paramsAac,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles
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
		this.frameGrabber = new FrameGrabberAudioAacFromEsFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		AudioAacInfo curPktInfo = packetParser.parseAndConvertData(inputBuf);

		//haveAllRequiredMetadataPackets = true;

		return curPktInfo;
	}

}
