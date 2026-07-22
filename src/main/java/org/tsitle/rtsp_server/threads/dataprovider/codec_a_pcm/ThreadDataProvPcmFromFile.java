package org.tsitle.rtsp_server.threads.dataprovider.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvFromFileBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;

public final class ThreadDataProvPcmFromFile extends ThreadDataProvFromFileBase<AudioPcmInfo> {

	private final @NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final @NonNull ParamsThreadRtpSenderPcm paramsPcm;

	private final @NonNull PacketParserPcm packetParser;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvPcmFromFile(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				paramsCommon,
				queueSize,
				debugRewindMediaFiles,
				false
			);

		//
		paramsAudioCommon.validate();
		this.paramsAudioCommon = paramsAudioCommon.clone();
		paramsPcm.validate();
		this.paramsPcm = paramsPcm.clone();
		//
		this.packetParser = new PacketParserPcm(
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample(),
				paramsAudioCommon.getAudioSamplerate()
			);
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
		this.frameGrabber = new FrameGrabberAudioPcmFromEsFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming,
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample(),
				paramsAudioCommon.getRtpAudioSpf(),
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

}
