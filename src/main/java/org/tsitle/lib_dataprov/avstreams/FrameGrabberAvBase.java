package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;

import java.util.Optional;

public abstract class FrameGrabberAvBase {

	private final @Nullable LogMsgInterface logMsgInterface;
	private final @NonNull AvStreamIncomingBase avStreamIncomingBase;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncomingBase Incoming A/V stream
	 */
	protected FrameGrabberAvBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingBase avStreamIncomingBase
			) {
		this.logMsgInterface = logMsgInterface;
		this.avStreamIncomingBase = avStreamIncomingBase;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Gets the minimum length of the Magic Bytes for frame start detection.
	 * @return Length of the Magic Bytes array in bits
	 */
	public abstract int getMinimumMagicBytesLengthBits();

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	public abstract boolean haveEos();

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 * @param stTimestamp Output for sample-time timestamp
	 */
	public abstract void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException, AvInvalidCodecDataException, InputStreamThreadEndedException;

	/**
	 * Returns the video frames per second if available
	 * @return Video frames per second
	 */
	public Optional<Double> getVideoFps() {
		return avStreamIncomingBase.getVideoFps();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void logDebug(@NonNull String fncName, @NonNull String msg) {
		if (logMsgInterface == null) {
			return;
		}
		logMsgInterface.addMsgForLogThread(RtxpLogLevel.DEBUG, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
