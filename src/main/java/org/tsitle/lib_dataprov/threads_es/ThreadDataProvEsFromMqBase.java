package org.tsitle.lib_dataprov.threads_es;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;

public abstract class ThreadDataProvEsFromMqBase<I extends CodecInfoInterface<I>>
		extends ThreadDataProvEsBase<I> {

	private @Nullable PacketSplitter<I> packetSplitter = null;
	private final boolean needMagicBytes;
	private final boolean needConvertData;
	private final boolean canReadFrameLenFromAvInfo;

	protected @Nullable AvStreamIncomingFromEsMq avStreamIncoming = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 * @param needMagicBytes Do we need 'Magic Bytes'?
	 * @param needConvertData Do we need the 'Parse and Convert' callback? (if false, then we need 'Parse Only' callback)
	 * @param canReadFrameLenFromAvInfo Can we read the frame length from the A/V info?
	 */
	protected ThreadDataProvEsFromMqBase(
				@NonNull ParamsThreadDpCommon paramsCommon,
				boolean needMagicBytes,
				boolean needConvertData,
				boolean canReadFrameLenFromAvInfo
			) {
		super(paramsCommon);

		//
		this.needMagicBytes = needMagicBytes;
		this.needConvertData = needConvertData;
		this.canReadFrameLenFromAvInfo = canReadFrameLenFromAvInfo;
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
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, InputStreamThreadEndedException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		if (haveEos()) {
			throw new InputStreamEosException();
		}

		if (packetSplitter == null) {
			createPacketSplitter(FNC_NAME);
		}
		//
		int errorCount = 0;
		while (! (doStop.get() || haveEos())) {
			packetSplitter.getNextSplitPacket(buf, stTimestamp, infoObj);
			debugStreamOffset = packetSplitter.getDebugStreamOffset();
			if (infoObj.isValid()) {
				break;
			}
			String tmpValErrMsg = infoObj.getValidationErrorMsg();
			if (++errorCount >= 60) {  // arbitrary limit
				throw new AvInvalidCodecDataException(tmpValErrMsg.isBlank() ? "unknown error" : tmpValErrMsg);
			}
			logDebug(FNC_NAME, "skipping frame with invalid codec data" +
					(tmpValErrMsg.isBlank() ? "" : " (" + tmpValErrMsg + ")"));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createAvStreamIncoming() throws AvCannotOpenInputException {
		this.avStreamIncoming = new AvStreamIncomingFromEsMq(
				logMsgInterface,
				paramsCommon.getIdEsSource(),
				paramsCommon.getAvStreamIncomingUri().orElseThrow()
			);
	}

	protected abstract int findNextMagicBytes(final @NonNull BufferView inputBv);

	protected abstract int readFrameLenFromAvInfo(final @NonNull I avInfo);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void createPacketSplitter(@NonNull String fncName) throws InputStreamEosException {
		if (packetSplitter != null) {
			return;
		}
		if (frameGrabber == null) {
			throw new InputStreamEosException();
		}
		packetSplitter = new PacketSplitter<>(
				(@NonNull String cbErrorMsg) -> logError(fncName, cbErrorMsg),
				frameGrabber,
				needMagicBytes,
				needConvertData,
				needConvertData ? this::parseAndConvertData : null,
				needConvertData ? null : this::parseData,
				this::findNextMagicBytes,
				canReadFrameLenFromAvInfo ? this::readFrameLenFromAvInfo : null
			);
	}

}
