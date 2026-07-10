package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAacFromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAac;

public final class ThreadDataProvAacFromFile extends ThreadDataProvFromFileBase<AudioAacInfo> {

	private final @NonNull AudioAacParser aacParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAac Thread-specific parameters
	 * @param avStreamIncoming Incoming A/V stream
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvAacFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAac paramsAac,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		paramsAudioCommon.validate();
		paramsAac.validate();

		//
		this.frameGrabber = new FrameGrabberAudioAacFromFile(logMsgInterface, avStreamIncoming);
		this.aacParser = new AudioAacParser();
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
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return aacParser.parseAacData(inputBuf);
	}

}
