package org.tsitle.lib_dataprov.avstreams.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsMqBase;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberAudioPcmFromEsMq extends FrameGrabberAvFromEsMqBase {

	private final int channels;
	private final int bitsPerSample;
	private int rtpSamplesPerFrame;
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
	public FrameGrabberAudioPcmFromEsMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming,
				int channels,
				int bitsPerSample,
				int rtpSamplesPerFrame,
				boolean isBigEndian
			) {
		super(logMsgInterface, avStreamIncoming);

		//
		final String errMsgPrefix = getClass().getSimpleName() + ".ctor(): ";
		if (channels < 1 || channels > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 32) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid audio bits per sample: " + bitsPerSample);
		}

		this.channels = channels;
		this.bitsPerSample = bitsPerSample;
		this.rtpSamplesPerFrame = rtpSamplesPerFrame;
		this.isBigEndian = isBigEndian;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next audio samples from the stream.
	 * @param frameBuf Output buffer to store the samples in
	 * @param stTimestamp Output for sample-time timestamp
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		frameBuf.clear();
		stTimestamp.clear();

		if (pcmFrameHandler == null) {
			pcmFrameHandler = new PcmFrameHandler(channels, bitsPerSample, rtpSamplesPerFrame, isBigEndian);
		}
		if (rtpSamplesPerFrame > 0) {
			cachedDataFromAsi.increaseSize(pcmFrameHandler.getFrameSizeBytes());
		}

		//
		avStreamIncoming.readFrame(cachedDataFromAsi, stTimestamp);
		if (rtpSamplesPerFrame < 1) {
			pcmFrameHandler.updateParamFrameSize(cachedDataFromAsi.getUsed());
			rtpSamplesPerFrame = pcmFrameHandler.getSamplesPerFrame();
		}

		//
		pcmFrameHandler.convertToBigEndian(cachedDataFromAsi, frameBuf);
	}

}
