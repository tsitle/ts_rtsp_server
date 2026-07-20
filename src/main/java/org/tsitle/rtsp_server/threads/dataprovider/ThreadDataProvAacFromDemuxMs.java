package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAacFromDemuxMs extends ThreadDataProvFromDemuxMsBase<AudioAacInfo> {

	private final @NonNull PacketParserAac packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvAacFromDemuxMs(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, false);

		this.packetParser = new PacketParserAac();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAvFromDemuxMs(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		AudioAacInfo curPktInfo = packetParser.parseAndConvertData(inputBuf);

		haveAllRequiredMetadataPackets = true;

		return curPktInfo;
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return -1;
	}

}
