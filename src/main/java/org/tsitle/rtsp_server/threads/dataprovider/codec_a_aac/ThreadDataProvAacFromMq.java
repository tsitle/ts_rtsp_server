package org.tsitle.rtsp_server.threads.dataprovider.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsMq;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvFromMqBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvAacFromMq extends ThreadDataProvFromMqBase<AudioAacInfo> {

	private final @NonNull PacketParserAac packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvAacFromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, false, true);

		this.packetParser = new PacketParserAac();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioAacFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
	}

	@Override
	protected @NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException("not implemented");
	}

	@Override
	protected @NonNull AudioAacInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		return -1;
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioAacInfo avInfo) {
		return avInfo.frameLength;
	}

}
