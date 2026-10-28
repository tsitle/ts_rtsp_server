package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ThreadRtxpTcpCommsWrite extends ThreadRtxpTcpCommsBase {

	private static final long WAIT_UNTIL_WRITTEN_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(1L);

	private final @NonNull OutputStream socketOs;
	private final Object writeStateMonitor = new Object();
	private final AtomicBoolean hasThreadEnded = new AtomicBoolean(false);

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param tcpActivityInterface TCP activity checker interface
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 */
	ThreadRtxpTcpCommsWrite(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull TcpActivityInterface tcpActivityInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp
			) {
		super(
				logMsgInterface,
				tcpActivityInterface,
				clientConnectionNr,
				rtspSocketTcp,
				0,
				false
			);
		//
		try {
			this.socketOs = rtspSocketTcp.getOutputStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		try {
			super.run();
		} finally {
			hasThreadEnded.set(true);
			notifyWriteStateChanged();
		}
	}

	@Override
	public void stopThread() {
		super.stopThread();
		notifyWriteStateChanged();
	}

	@Override
	void closeIoStream() {
		closeIoStream(BlockedState.Flag.SOCKET_WRITE, null, socketOs);
	}

	// -----------------------------------------------------------------------------------------------------------------

	void writeRtspLines(@NonNull List<@NonNull String> lines)
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".writeRtspLines()";

		raiseForErrorState();
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return;
		}
		try {
			for (String line : lines) {
				if (textDataRingBuf.isFull()) {
					// discard oldest entry
					textDataRingBuf.poll();
				}
				Optional<TextRbEntry> lastEntryPtr = textDataRingBuf.getWritePointer();
				if (lastEntryPtr.isEmpty()) {
					break;
				}
				lastEntryPtr.get().text = line;
				textDataRingBuf.advanceWritePointer();
			}
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_WRITE);
		}

		notifyWriteStateChanged();
		waitUntilWritten(FNC_NAME, this::isTextDataRingBufEmpty);
	}

	// -----------------------------------------------------------------------------------------------------------------

	void writeRtpOrRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".writeRtpOrRtcpBinary()";

		raiseForErrorState();
		if (! isRtpRtcpAllowed.get() || channNr.isEmpty()) {
			return;
		}

		BinaryPktRingBuf rbPtr;

		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return;
		}
		try {
			if (! mapBinaryDataRbs.containsKey(channNr)) {
				mapBinaryDataRbs.put(
						channNr.clone(),
						new BinaryPktRingBuf(BINARY_PKT_RB_SIZE_INIT, BINARY_PKT_RB_SIZE_MAX)
					);
			}
			rbPtr = mapBinaryDataRbs.get(channNr);
			if (rbPtr.isFull()) {
				// discard oldest entry
				rbPtr.poll();
			}
			Optional<BufferExt> lastEntryPtr = rbPtr.getWritePointer();
			if (lastEntryPtr.isEmpty()) {
				return;  // this should never happen
			}

			BufferExt bePtr = lastEntryPtr.get();
			bufView.copyViewIntoBe(bePtr);
			rbPtr.advanceWritePointer();
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_WRITE);
		}

		notifyWriteStateChanged();
		waitUntilWritten(FNC_NAME, rbPtr::isEmpty);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean mainLoop()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSocketTcp.isClosed()) {
			if (! (hasBeenRequestedToStop() || hasSocketBeenImplicitlyClosed.get())) {
				throw new TcpSocketClosedException();
			}
			return false;  // stop the thread
		}

		blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.SOCKET_WRITE);
		try {
			tcpActivityInterface.checkTcpActivityTimeout();

			boolean haveWrittenSomething = false;

			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_WRITE);
			try {
				// RTSP lines
				if (! textDataRingBuf.isEmpty()) {
					writeTextToSocket();
					haveWrittenSomething = true;
					socketOs.flush();
				}
				// RTP/RTCP
				if (isRtpRtcpAllowed.get()) {
					for (RtspProtoTcpChannelNr channNr : mapBinaryDataRbs.keySet()) {
						BinaryPktRingBuf rbPtr = mapBinaryDataRbs.get(channNr);
						while (! rbPtr.isEmpty()) {
							writeBinaryToSocket(channNr, rbPtr.poll().orElseThrow());
							haveWrittenSomething = true;
							socketOs.flush();
						}
					}
				}
			} catch (IOException e) {
				throw new TcpSocketIoException(FNC_NAME + ": IOException caught: " + e.getMessage());
			} finally {
				blockedState.unblock(BlockedState.Flag.BUFFER_WRITE);
			}

			if (haveWrittenSomething) {
				tcpActivityInterface.resetTcpActivityTimeoutTimer();
				notifyWriteStateChanged();
			}
		} finally {
			blockedState.unblock(BlockedState.Flag.SOCKET_WRITE);
		}

		sleepForSomeTime();
		return true;  // all good - continue the thread
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * The write-thread is explicitly woken when data is queued, so polling sleeps are not needed here.
	 */
	private void sleepForSomeTime() throws InterruptedException {
		synchronized (writeStateMonitor) {
			while (! hasBeenRequestedToStop() && ! hasThreadEnded.get() && ! hasPendingWriteData()) {
				writeStateMonitor.wait();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@FunctionalInterface
	private interface BufferEmptyCheck {
		boolean isEmpty();
	}

	private void notifyWriteStateChanged() {
		synchronized (writeStateMonitor) {
			writeStateMonitor.notifyAll();
		}
	}

	private void waitUntilWritten(@NonNull String fncName, @NonNull BufferEmptyCheck bufferEmptyCheck)
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		long remainingNs = WAIT_UNTIL_WRITTEN_TIMEOUT_NS;
		final long deadlineNs = System.nanoTime() + remainingNs;

		while (! (hasBeenRequestedToStop() || hasThreadEnded.get() || bufferEmptyCheck.isEmpty())) {
			raiseForErrorState();
			if (remainingNs <= 0L) {
				throw new RuntimeException(fncName + ": Timeout waiting for send buffer to empty");
			}

			synchronized (writeStateMonitor) {
				if (! (hasBeenRequestedToStop() || hasThreadEnded.get() || bufferEmptyCheck.isEmpty())) {
					try {
						TimeUnit.NANOSECONDS.timedWait(writeStateMonitor, remainingNs);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();  // restore flag
						return;
					}
				}
			}
			remainingNs = deadlineNs - System.nanoTime();
		}

		raiseForErrorState();
	}

	private boolean isTextDataRingBufEmpty() {
		return textDataRingBuf.isEmpty();
	}

	private boolean hasPendingWriteData() {
		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return true;
		}
		try {
			if (! textDataRingBuf.isEmpty()) {
				return true;
			}
			if (isRtpRtcpAllowed.get()) {
				for (BinaryPktRingBuf rbPtr : mapBinaryDataRbs.values()) {
					if (! rbPtr.isEmpty()) {
						return true;
					}
				}
			}
			return false;
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_WRITE);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void writeTextToSocket() throws IOException {
		StringBuilder sb = new StringBuilder();
		while (! textDataRingBuf.isEmpty()) {
			TextRbEntry tmpText = textDataRingBuf.poll().orElseThrow();
			sb.append(tmpText.text);
		}
		socketOs.write(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void writeBinaryToSocket(@NonNull RtspProtoTcpChannelNr channNr, @NonNull BufferExt binBe) throws IOException {
		final int tmpChannInt = channNr.getChannel8bit().orElseThrow();
		final int bufSz = binBe.getUsed();
		byte[] tmpBa = new byte[4];
		tmpBa[0] = (byte)'$';
		tmpBa[1] = (byte)tmpChannInt;
		tmpBa[2] = (byte)((bufSz >> 8) & 0xFF);
		tmpBa[3] = (byte)(bufSz & 0xFF);
		socketOs.write(tmpBa, 0, 4);
		socketOs.write(binBe.getBaPtr(), 0, bufSz);
	}

}
