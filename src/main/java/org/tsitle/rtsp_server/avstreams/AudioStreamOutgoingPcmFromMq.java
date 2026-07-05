package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class AudioStreamOutgoingPcmFromMq extends AvStreamOutgoingFromMqBase {

	private final boolean isBigEndian;

	private final int bytesPerSample;
	private final int bytesPerChannelAndSample;
	private final BufferExt cachedDataBuf2 = new BufferExt();

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param isBigEndian Is the input data big-endian?
	 */
	public AudioStreamOutgoingPcmFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming,
				int channels,
				int bitsPerSample,
				boolean isBigEndian
			) {
		super(logMsgInterface, avStreamIncoming);

		//
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException("Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException("Invalid audio bits per sample: " + bitsPerSample);
		}

		this.isBigEndian = isBigEndian;

		this.bytesPerSample = bitsPerSample / 8;
		this.bytesPerChannelAndSample = channels * bytesPerSample;

		this.cachedDataBuf2.increaseSize(1024);
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
		BufferExt readIntoPtr = (isBigEndian || bytesPerSample == 1 ? frameBuf : cachedDataBuf2);
		//
		avStreamIncoming.readFrame(readIntoPtr, stTimestamp);
		int tmpRead = readIntoPtr.getUsed();
		if (tmpRead > 0 && tmpRead % bytesPerChannelAndSample != 0) {
			// discard any partial samples
			tmpRead -= (tmpRead % bytesPerChannelAndSample);
		}
		if (tmpRead <= 0) {
			throw new InputStreamEosException();
		}
		readIntoPtr.setUsed(tmpRead);
		//
		if (isBigEndian || bytesPerSample == 1) {
			return;
		}

		// convert to big-endian
		for (int i = 0; i + 1 < tmpRead; i += bytesPerSample) {
			frameBuf.getBaPtr()[i] = readIntoPtr.get(i + 1);
			frameBuf.getBaPtr()[i + 1] = readIntoPtr.get(i);
		}
		frameBuf.setUsed(tmpRead);
	}

}
