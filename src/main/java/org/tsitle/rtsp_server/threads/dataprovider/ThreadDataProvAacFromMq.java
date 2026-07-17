package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAacFromMq;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public final class ThreadDataProvAacFromMq extends ThreadDataProvFromMqBase<AudioAacInfo> {

	private final @NonNull AudioAacParser aacParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvAacFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, false);

		//
		this.frameGrabber = new FrameGrabberAudioAacFromMq(logMsgInterface, avStreamIncoming);
		this.aacParser = new AudioAacParser();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		AudioAacInfo curFrameAacInfo = aacParser.parseAacData(inputBuf);
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameAacInfo;
	}

}
