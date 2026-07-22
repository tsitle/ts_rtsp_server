package org.tsitle.rtsp_server.avstreams.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromEsFileBase;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberAudioPcmFromEsFile extends FrameGrabberAvFromEsFileBase {

	private final int channels;
	private final int bitsPerSample;
	private final int rtpSamplesPerFrame;
	private final boolean isBigEndian;

	private final @NonNull BufferExt cachedDataFromAsi = new BufferExt();
	private @Nullable PcmFrameHandler pcmFrameHandler = null;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param rtpSamplesPerFrame Samples per frame as required for RTP
	 * @param isBigEndian Is the input data big-endian?
	 */
	public FrameGrabberAudioPcmFromEsFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming,
				int channels,
				int bitsPerSample,
				int rtpSamplesPerFrame,
				boolean isBigEndian
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				new byte[0],
				0
			);

		//
		final String errMsgPrefix = getClass().getSimpleName() + ".ctor(): ";
		if (channels < 1 || channels > 10) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 32) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid audio bits per sample: " + bitsPerSample);
		}
		if (rtpSamplesPerFrame < 1) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid audio samples per frame: " + rtpSamplesPerFrame);
		}

		this.channels = channels;
		this.bitsPerSample = bitsPerSample;
		this.rtpSamplesPerFrame = rtpSamplesPerFrame;
		this.isBigEndian = isBigEndian;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	@Override
	public boolean haveEos() {
		return avStreamIncoming.haveEos();
	}

	/**
	 * Reads the next audio samples from the stream.
	 * @param frameBuf Output buffer to store the samples in
	 * @param stTimestamp Output for sample-time timestamp
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampEpochNs stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		frameBuf.clear();
		stTimestamp.clear();

		if (pcmFrameHandler == null) {
			pcmFrameHandler = new PcmFrameHandler(channels, bitsPerSample, rtpSamplesPerFrame, isBigEndian);
		}
		cachedDataFromAsi.increaseSize(pcmFrameHandler.getFrameSizeBytes());

		//
		int tmpRead = avStreamIncoming.readBytes(cachedDataFromAsi.getBaPtr(), pcmFrameHandler.getFrameSizeBytes());
		cachedDataFromAsi.setUsed(tmpRead);

		//
		pcmFrameHandler.convertToBigEndian(cachedDataFromAsi, frameBuf);
	}

}
