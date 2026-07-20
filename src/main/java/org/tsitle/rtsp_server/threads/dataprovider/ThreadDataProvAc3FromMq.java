package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromEsMq;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAc3FromMq extends ThreadDataProvFromMqBase<AudioAc3Info> {

	private final @NonNull PacketParserAc3 packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvAc3FromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, false);

		this.packetParser = new PacketParserAc3();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioAc3FromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAc3Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return packetParser.parseAndConvertData(inputBuf);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferExt inputBuf) {
		return -1;
	}

}
