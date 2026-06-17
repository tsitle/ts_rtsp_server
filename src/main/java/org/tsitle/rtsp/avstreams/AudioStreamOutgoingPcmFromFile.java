package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public class AudioStreamOutgoingPcmFromFile extends AvStreamOutgoingFromFileBase {

	private final boolean isBigEndian;

	private final int bytesPerSample;
	private final int bytesPerChannelAndSample;
	private final int rtpFrameSizeBytes;
	private final BufferExt cachedDataBuf2 = new BufferExt();

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param rtpSamplesPerFrame Samples per frame as required for RTP
	 * @param isBigEndian Is the input data big-endian?
	 */
	public AudioStreamOutgoingPcmFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
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
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException("Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException("Invalid audio bits per sample: " + bitsPerSample);
		}
		if (rtpSamplesPerFrame < 1) {
			throw new IllegalArgumentException("Invalid audio samples per frame: " + rtpSamplesPerFrame);
		}

		this.isBigEndian = isBigEndian;

		this.bytesPerSample = bitsPerSample / 8;
		this.bytesPerChannelAndSample = channels * bytesPerSample;
		this.rtpFrameSizeBytes = channels * rtpSamplesPerFrame * this.bytesPerSample;

		this.cachedDataBuf2.increaseSize(this.rtpFrameSizeBytes);
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
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEosException {
		BufferExt readIntoPtr = (isBigEndian || bytesPerSample == 1 ? frameBuf : cachedDataBuf2);
		//
		frameBuf.clear();
		frameBuf.increaseSize(rtpFrameSizeBytes);
		//
		int tmpRead = avStreamIncoming.readBytes(readIntoPtr.getBaPtr(), rtpFrameSizeBytes);
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
