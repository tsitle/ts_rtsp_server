package org.tsitle.rtsp_server.threads.dataprovider_es;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromEsMqBase;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public abstract class ThreadDataProvFromMqBase<I extends CodecInfoInterface<I>> extends ThreadDataProvBase<I, FrameGrabberAvFromEsMqBase> {

	private @Nullable PacketSplitter<I, FrameGrabberAvFromEsMqBase> packetSplitter = null;
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
	protected ThreadDataProvFromMqBase(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
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
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, InputStreamThreadEndedException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		if (haveEos()) {
			throw new InputStreamEosException();
		}

		if (packetSplitter == null) {
			if (frameGrabber == null) {
				throw new InputStreamEosException();
			}
			packetSplitter = new PacketSplitter<>(
					(@NonNull String cbErrorMsg) -> logError(FNC_NAME, cbErrorMsg),
					frameGrabber,
					needMagicBytes,
					needConvertData,
					needConvertData ? this::parseAndConvertData : null,
					needConvertData ? null : this::parseData,
					this::findNextMagicBytes,
					canReadFrameLenFromAvInfo ? this::readFrameLenFromAvInfo : null
				);
		}
		packetSplitter.getNextSplitPacket(buf, stTimestamp, infoObj);
		debugStreamOffset = packetSplitter.getDebugStreamOffset();
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

}
