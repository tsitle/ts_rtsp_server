package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromMq;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAc3FromMq extends ThreadDataProvFromMqBase<AudioAc3Info> {

	private @Nullable AudioAc3Parser ac3Parser = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvAc3FromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioAc3FromMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
		this.ac3Parser = new AudioAc3Parser();
	}

	@Override
	protected @NonNull AudioAc3Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (ac3Parser == null) {
			throw new IllegalStateException("ac3Parser is null");
		}
		AudioAc3Info curFrameAacInfo = ac3Parser.parseAc3Data(inputBuf);
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameAacInfo;
	}

}
