package org.tsitle.rtsp_server.threads.dataprovider.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvFromMqBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;

public final class ThreadDataProvPcmFromMq extends ThreadDataProvFromMqBase<AudioPcmInfo> {

	private final @NonNull ParamsThreadRtpSenderPcm paramsPcm;

	private final @NonNull PacketParserPcm packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsPcm Thread-specific parameters
	 */
	public ThreadDataProvPcmFromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm
			) {
		super(paramsCommon, false, false, false);

		//
		paramsPcm.validate();
		this.paramsPcm = paramsPcm.clone();
		//
		this.packetParser = new PacketParserPcm(
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberAudioPcmFromEsMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming,
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample(),
				paramsPcm.getIsAudioInputBigEndian()
			);
	}

	@Override
	protected @NonNull AudioPcmInfo parseAndConvertData(@NonNull BufferExt ioBuf) {
		throw new RuntimeException(getClass().getSimpleName() + ".parseAndConvertData(): not implemented");
	}

	@Override
	protected @NonNull AudioPcmInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return packetParser.parseData(inputBv);
	}

	@Override
	protected int findNextMagicBytes(@NonNull BufferView inputBv) {
		throw new RuntimeException(getClass().getSimpleName() + ".findNextMagicBytes(): not implemented");
	}

	@Override
	protected int readFrameLenFromAvInfo(final @NonNull AudioPcmInfo avInfo) {
		throw new RuntimeException(getClass().getSimpleName() + ".readFrameLenFromAvInfo(): not implemented");
	}

}
