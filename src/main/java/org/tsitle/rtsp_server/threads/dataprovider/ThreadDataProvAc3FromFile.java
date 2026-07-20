package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromEsFile;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAc3FromFile extends ThreadDataProvFromFileBase<AudioAc3Info> {

	private final @NonNull PacketParserAc3 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvAc3FromFile(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles
			);

		this.packetParser = new PacketParserAc3();
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
		this.frameGrabber = new FrameGrabberAudioAc3FromEsFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAc3Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		AudioAc3Info curPktInfo = packetParser.parseAndConvertData(inputBuf);

		//haveAllRequiredMetadataPackets = true;

		return curPktInfo;
	}

}
