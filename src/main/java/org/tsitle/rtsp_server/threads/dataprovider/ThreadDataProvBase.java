package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvBase;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;
import org.tsitle.rtsp_server.threads.ThreadBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ThreadDataProvBase<I extends CodecInfoInterface<I>, FGAV extends FrameGrabberAvBase<?>> extends ThreadBase {

	protected final @NonNull ParamsThreadRtpSenderCommon paramsCommon;

	protected long debugStreamOffset = 0;

	protected @Nullable FGAV frameGrabber;

	protected final ReentrantLock lock = new ReentrantLock();
	/** Condition to signal that a frame has been removed from the queue or the thread has been requested to stop */
	protected final Condition stateChanged = lock.newCondition();

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	protected ThreadDataProvBase(@NonNull ParamsThreadRtpSenderCommon paramsCommon) {
		super(paramsCommon.getLogMsgInterface().orElseThrow());

		//
		paramsCommon.validate();
		this.paramsCommon = paramsCommon.clone();

		//
		this.frameGrabber = null;  // needs to be set by the child class
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract boolean haveEos();

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public abstract boolean haveFullInputQueue();

	public abstract int getInputQueueSize();

	public abstract void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, InputStreamThreadEndedException, AvInvalidCodecDataException;

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	public abstract void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		lock.lock();
		try {
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void createAvStreamIncoming() throws AvCannotOpenInputException;

	protected abstract void createFrameGrabber();

	protected abstract @NonNull I parseAndConvertData(@NonNull BufferExt ioBuf) throws AvInvalidCodecDataException;

	protected abstract @NonNull I parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException;

}
