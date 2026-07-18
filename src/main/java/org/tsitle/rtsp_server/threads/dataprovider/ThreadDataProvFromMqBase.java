package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromMqBase;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public abstract class ThreadDataProvFromMqBase<I extends CodecInfoInterface<I>> extends ThreadDataProvBase<I, FrameGrabberAvFromMqBase> {

	private final boolean needMagicBytes;

	protected @Nullable AvStreamIncomingFromMq avStreamIncoming = null;

	protected boolean haveAllRequiredMetadataPackets = false;
	private final BufferExt remainingInputBuf = new BufferExt();
	private final TimestampEpochNs stTimestampCurFrame = TimestampEpochNs.ofEmpty();
	protected int magicBytesLength = -1;
	protected byte[] magicBytesArrPtr = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param needMagicBytes Do we need 'Magic Bytes'?
	 */
	protected ThreadDataProvFromMqBase(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				boolean needMagicBytes
			) {
		super(paramsCommon);

		//
		this.needMagicBytes = needMagicBytes;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		try {
			createAvStreamIncoming();
			createFrameGrabber();
		} catch (AvCannotOpenInputException e) {
			logError(FNC_NAME, "cannot open input: " + e.getMessage());
			return;
		}

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			while (! doStop.get()) {
				//noinspection BusyWait
				Thread.sleep(50);
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
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
		return (frameGrabber == null || frameGrabber.haveEos());
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
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		if (haveEos()) {
			throw new InputStreamEosException();
		}
		do {
			if (frameGrabber == null) {
				throw new InputStreamEosException();
			}
			BufferExt readIntoBufPtr = (needMagicBytes ? remainingInputBuf : buf);
			if (! needMagicBytes || remainingInputBuf.isEmpty()) {
				try {
					frameGrabber.getNextFrame(readIntoBufPtr, stTimestampCurFrame);
				} catch (InputStreamIoException e) {
					throw new InputStreamEosException();
				}
			}
			//
			try {
				final I tmpInfoObj = parseAndConvertData(readIntoBufPtr);
				infoObj.copyOf(tmpInfoObj);
			} catch (Exception e) {
				logError(FNC_NAME, "caught: " + e);
				throw new InputStreamEosException();
			}

			stTimestamp.copyFrom(stTimestampCurFrame);

			/*
			 * IP cameras tend to send, for instance, 'SPS', 'PPS' and a VCL NAL Unit in a single packet.
			 * So we need to find the next magic bytes to split the buffer into multiple NAL Units.
			 */
			if (needMagicBytes) {
				int nextOffset = findNextMagicBytes(remainingInputBuf);
				if (nextOffset < 1) {
					buf.copyOf(remainingInputBuf);
					remainingInputBuf.clear();
				} else {
					buf.copyOf(remainingInputBuf, 0, nextOffset);
					BufferExt tmpBuf = new BufferExt();
					tmpBuf.copyOf(remainingInputBuf, nextOffset, remainingInputBuf.getUsed() - nextOffset);
					remainingInputBuf.copyOf(tmpBuf);
					// parse the new buffer again
					try {
						final I tmpInfoObj = parseAndConvertData(buf);
						infoObj.copyOf(tmpInfoObj);
					} catch (Exception e) {
						logError(FNC_NAME, "caught: " + e);
						throw new InputStreamEosException();
					}
				}
			}
			//
			debugStreamOffset += buf.getUsed();
		} while (! haveAllRequiredMetadataPackets);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createAvStreamIncoming() throws AvCannotOpenInputException {
		this.avStreamIncoming = new AvStreamIncomingFromMq(
				logMsgInterface,
				paramsCommon.getIdEsSource(),
				paramsCommon.getAvStreamIncomingUri().orElseThrow()
			);
	}

	protected abstract @NonNull I parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return -1;
	}

	// @TODO move this to MagicBytesH26xHelper
	protected int findH26xMagicBytesLength(final @NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		// @TODO fix this
		int resI = 0;
		if (inputBuf.getUsed() >= FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length) {
			if (checkForH26xMagicBytes(FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4, inputBuf)) {
				resI = FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length;
			}
		}
		if (resI == 0 &&
				inputBuf.getUsed() >= FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3.length) {
			if (checkForH26xMagicBytes(FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3, inputBuf)) {
				resI = FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3.length;
			}
		}
		if (resI == 0) {
			throw new AvInvalidCodecDataException("could not determine Magic Bytes");
		}
		return resI;
	}

	// @TODO move this to MagicBytesH26xHelper
	protected int findH26xNextNalUnit(final @NonNull BufferExt inputBuf) {
		if (magicBytesArrPtr == null) {
			throw new IllegalStateException("magicBytesArrPtr is null");
		}
		int resI = -1;
		boolean found;
		// minimum starting offset is 1
		for (int ix1 = magicBytesArrPtr.length; ix1 < inputBuf.getUsed() - magicBytesArrPtr.length; ix1++) {
			found = true;
			for (int ix2 = 0; ix2 < magicBytesArrPtr.length; ix2++) {
				if (inputBuf.get(ix1 + ix2) != magicBytesArrPtr[ix2]) {
					found = false;
					break;
				}
			}
			if (found) {
				resI = ix1;
				break;
			}
		}
		return resI;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// @TODO move this to MagicBytesH26xHelper
	private boolean checkForH26xMagicBytes(final byte[] magicBytes, final @NonNull BufferExt inputBuf) {
		for (int i = 0; i < magicBytes.length; i++) {
			if (inputBuf.get(i) != magicBytes[i]) {
				return false;
			}
		}
		return true;
	}

}
