package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromMq;

public final class ThreadDataProvAc3FromMq extends ThreadDataProvFromMqBase<AudioAc3Info> {

	private final @NonNull AudioAc3Parser ac3Parser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvAc3FromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, false);

		//
		this.frameGrabber = new FrameGrabberAudioAc3FromMq(logMsgInterface, avStreamIncoming);
		this.ac3Parser = new AudioAc3Parser();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull AudioAc3Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		AudioAc3Info curFrameAacInfo = ac3Parser.parseAc3Data(inputBuf);
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameAacInfo;
	}

}
