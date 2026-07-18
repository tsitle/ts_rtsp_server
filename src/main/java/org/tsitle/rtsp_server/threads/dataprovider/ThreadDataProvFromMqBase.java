package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromMqBase;
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

}
