package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingFromMqBase;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingH26xFromFile;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

public abstract class ThreadDataProvFromMqBase<I extends CodecInfoInterface<I>> extends ThreadDataProvBase<I, AvStreamOutgoingFromMqBase> {

	protected boolean haveAllRequiredMetadataPackets = false;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 */
	protected ThreadDataProvFromMqBase(@NonNull LogMsgInterface logMsgInterface) {
		super(logMsgInterface);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);

		//
		try {
			while (! doStop.get()) {
				//noinspection BusyWait
				Thread.sleep(50);
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

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

	@Override
	public synchronized boolean haveEos() {
		return mediaOutgoingStream.haveEos();
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Override
	public synchronized boolean haveFullInputQueue() {
		return true;
	}

	@Override
	public synchronized int getInputQueueSize() {
		return 1;
	}

	@Override
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		if (haveEos()) {
			throw new InputStreamEosException();
		}
		do {
			try {
				mediaOutgoingStream.getNextFrame(buf);
			} catch (InputStreamIoException e) {
				throw new InputStreamEosException();
			}
			try {
				final I tmpInfoObj = parseAndConvertData(buf);
				infoObj.copyOf(tmpInfoObj);
			} catch (Exception e) {
				logError(FNC_NAME, "caught: " + e);
				throw new InputStreamEosException();
			}
			debugStreamOffset += buf.getUsed();
		} while (! haveAllRequiredMetadataPackets);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract @NonNull I parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	protected int findH26xMagicBytesLength(final BufferExt inputBuf) throws AvInvalidCodecDataException {
		int magicBytesLength = 0;
		if (inputBuf.getUsed() >= VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length) {
			if (findH26xMagicBytesLength_sub(VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_4, inputBuf)) {
				magicBytesLength = VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length;
			}
		}
		if (magicBytesLength == 0 &&
				inputBuf.getUsed() >= VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_3.length) {
			if (findH26xMagicBytesLength_sub(VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_3, inputBuf)) {
				magicBytesLength = VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_3.length;
			}
		}
		if (magicBytesLength == 0) {
			throw new AvInvalidCodecDataException("could not determine Magic Bytes");
		}
		return magicBytesLength;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean findH26xMagicBytesLength_sub(final byte[] magicBytes, final BufferExt inputBuf) {
		for (int i = 0; i < magicBytes.length; i++) {
			if (inputBuf.get(i) != magicBytes[i]) {
				return false;
			}
		}
		return true;
	}

}
