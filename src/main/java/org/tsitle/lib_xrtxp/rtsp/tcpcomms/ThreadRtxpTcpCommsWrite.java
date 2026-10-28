package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.helpers.SleepHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

final class ThreadRtxpTcpCommsWrite extends ThreadRtxpTcpCommsBase {

	private final @NonNull OutputStream socketOs;

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
				line = line.strip() + CRLF;
				lastEntryPtr.get().text = line;
				textDataRingBuf.advanceWritePointer();
			}
		} finally {
			blockedState.unblock(BlockedState.Flag.BUFFER_WRITE);
		}

		// wait until send buffer is empty
		int waitCnt = 0;
		while (! (hasBeenRequestedToStop() || textDataRingBuf.isEmpty())) {
			if (++waitCnt > 1000) {
				throw new RuntimeException(FNC_NAME + ": Timeout waiting for send buffer to empty");
			}
			if (! SleepHelper.basicSleep(1L)) {
				break;
			}
		}
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

		// wait until send buffer is empty
		int waitCnt = 0;
		while (! (hasBeenRequestedToStop() || rbPtr.isEmpty())) {
			if (++waitCnt > 1000) {
				throw new RuntimeException(FNC_NAME + ": Timeout waiting for send buffer to empty");
			}
			if (! SleepHelper.basicSleep(1L)) {
				break;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean mainLoop() throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSocketTcp.isClosed() && ! hasSocketBeenImplicitlyClosed.get()) {
			throw new TcpSocketClosedException();
		}

		try {
			blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.SOCKET_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
			return false;  // stop the thread
		}
		try {
			tcpActivityInterface.checkTcpActivityTimeout();

			boolean haveWrittenSomething = false;

			try {
				blockedState.waitForUnblockedAndThenBlock(BlockedState.Flag.BUFFER_WRITE);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				return false;  // stop the thread
			}
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
			}
		} finally {
			blockedState.unblock(BlockedState.Flag.SOCKET_WRITE);
		}
		return true;  // all good - continue the thread
	}

	// -----------------------------------------------------------------------------------------------------------------
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
