package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.helpers.SleepHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.TimestampEpoch;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpCommsInterface;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TCP Socket wrapper that reads/writes RTxP messages over a single TCP socket.<br />
 * If the transport mode for the RTSP session that owns this TCP socket is 'TCP with interleaved RTP/RTCP',
 * then this class can handle RTSP/RTP/RTCP messages in the same TCP connection.<br />
 * If the transport mode for the RTSP session that owns this TCP socket is 'UDP',
 * then this class can handle RTSP messages in the TCP connection.<br />
 * This class is thread-safe and can be used by multiple threads to read/write RTxP messages from/to the TCP socket.<br />
 * <br />
 * See <a href="https://datatracker.ietf.org/doc/html//rfc2326.html#section-10.12">RFC-2326 Section 10.12</a> for more information.
 */
public final class RtxpTcpCommsImpl implements RtxpTcpCommsInterface, AutoCloseable {

	private static class TcpActivityTimeoutChecker implements TcpActivityInterface {
		/** VLC sometimes takes quite a while to complete an RTSP setup right after being started */
		private static final long TCP_ACTIVITY_TIMEOUT_SECS_DEF = 10L;
		private static final long TCP_ACTIVITY_TIMEOUT_SECS_RTSP_ONLY = 60L;

		private final ReentrantLock lockOpsTatc = new ReentrantLock();
		private final AtomicInteger tcpActivityTimeoutMs = new AtomicInteger((int)(TCP_ACTIVITY_TIMEOUT_SECS_DEF * 1000L));
		private final TimestampEpoch lastActivityTime = TimestampEpoch.ofEmpty();

		void setTcpActivityTimeoutForRtspOnly() {
			lockOpsTatc.lock();
			try {
				tcpActivityTimeoutMs.set((int) (TCP_ACTIVITY_TIMEOUT_SECS_RTSP_ONLY * 1000L));
			} finally {
				lockOpsTatc.unlock();
			}
		}

		void setTcpActivityTimeoutForRtxp() {
			lockOpsTatc.lock();
			try {
				tcpActivityTimeoutMs.set((int)(TCP_ACTIVITY_TIMEOUT_SECS_DEF * 1000L));
			} finally {
				lockOpsTatc.unlock();
			}
		}

		@Override
		public void checkTcpActivityTimeout() throws TcpSocketActivityTimeoutException {
			lockOpsTatc.lock();
			try {
				if (lastActivityTime.isElapsedMs(tcpActivityTimeoutMs.get())) {
					throw new TcpSocketActivityTimeoutException();
				}
			} finally {
				lockOpsTatc.unlock();
			}
		}

		@Override
		public void resetTcpActivityTimeoutTimer() {
			lockOpsTatc.lock();
			try {
				lastActivityTime.setToNow();
			} finally {
				lockOpsTatc.unlock();
			}
		}
	}

	private final @NonNull Socket rtspSocketTcp;

	private final TcpActivityTimeoutChecker tcpActivityTimeoutChecker = new TcpActivityTimeoutChecker();

	private final AtomicBoolean doStop = new AtomicBoolean(false);

	private final ReentrantLock lockOpsGen = new ReentrantLock();
	private final ReentrantLock lockOpsRead = new ReentrantLock();
	private final ReentrantLock lockOpsWrite = new ReentrantLock();

	private final @NonNull ThreadRtxpTcpCommsRead threadRead;
	private final @NonNull ThreadRtxpTcpCommsWrite threadWrite;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param timeoutSocketReadMs Timeout for TCP socket read operations in milliseconds
	 */
	public RtxpTcpCommsImpl(
				@NonNull LogMsgInterface logMsgInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				int timeoutSocketReadMs
			) {
		this.rtspSocketTcp = rtspSocketTcp;

		//
		this.threadRead = new ThreadRtxpTcpCommsRead(
				logMsgInterface,
				tcpActivityTimeoutChecker,
				clientConnectionNr,
				rtspSocketTcp,
				timeoutSocketReadMs
			);
		this.threadRead.setDaemon(false);
		this.threadRead.start();
		this.threadWrite = new ThreadRtxpTcpCommsWrite(
				logMsgInterface,
				tcpActivityTimeoutChecker,
				clientConnectionNr,
				rtspSocketTcp
			);
		this.threadWrite.setDaemon(false);
		this.threadWrite.start();
		int waitCnt = 0;
		while (! (threadRead.isRunning() && threadWrite.isRunning())) {
			if (++waitCnt > 100 * 5) {  // ^= 5 secs
				break;
			}
			if (! SleepHelper.sleep(10L)) {
				break;
			}
		}
		//
		this.tcpActivityTimeoutChecker.resetTcpActivityTimeoutTimer();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean isSocketClosed() {
		lockOpsGen.lock();
		try {
			if (doStop.get()) {
				return true;
			}
			return rtspSocketTcp.isClosed();
		} finally {
			lockOpsGen.unlock();
		}
	}

	@Override
	public void closeSocket() {
		lockOpsGen.lock();
		try {
			if (doStop.get()) {
				return;
			}
			doStop.set(true);
			//
			threadRead.stopThread();
			threadWrite.stopThread();
			int waitCnt = 0;
			while (threadRead.isRunning() || threadWrite.isRunning()) {
				if (++waitCnt > 100 * 5) {  // ^= 5 secs
					break;
				}
				if (! SleepHelper.sleep(10L)) {
					break;
				}
			}
		} finally {
			try { rtspSocketTcp.close(); } catch (IOException e) { /* ignore */ }
			threadRead.closeIoStream();
			threadWrite.closeIoStream();
			//
			lockOpsGen.unlock();
		}
	}

	@Override
	public void close() {
		closeSocket();
	}

	@Override
	@SuppressWarnings("unused")
	public @NonNull RtspProtoSocketPortNr getSocketLocalPort() {
		lockOpsGen.lock();
		try {
			if (doStop.get()) {
				return RtspProtoSocketPortNr.ofEmpty();
			}
			try {
				return RtspProtoSocketPortNr.of(rtspSocketTcp.getLocalPort());
			} catch (RtspProtoNumberRangeException e) {
				// this will never happen
				return RtspProtoSocketPortNr.ofEmpty();
			}
		} finally {
			lockOpsGen.unlock();
		}
	}

	@Override
	public @NonNull RtspProtoSocketPortNr getSocketRemotePort() {
		lockOpsGen.lock();
		try {
			if (doStop.get()) {
				return RtspProtoSocketPortNr.ofEmpty();
			}
			try {
				return RtspProtoSocketPortNr.of(rtspSocketTcp.getPort());
			} catch (RtspProtoNumberRangeException e) {
				// this will never happen
				return RtspProtoSocketPortNr.ofEmpty();
			}
		} finally {
			lockOpsGen.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean getIsRtpRtcpAllowed() {
		lockOpsGen.lock();
		try {
			return threadRead.getIsRtpRtcpAllowed();
		} finally {
			lockOpsGen.unlock();
		}
	}

	@Override
	public void setIsRtpRtcpAllowed(boolean value) {
		lockOpsGen.lock();
		try {
			if (value) {
				setTcpActivityTimeoutForRtxp();
			} else {
				setTcpActivityTimeoutForRtspOnly();
			}
			threadRead.setIsRtpRtcpAllowed(value);
			threadWrite.setIsRtpRtcpAllowed(value);
		} finally {
			lockOpsGen.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void setTcpActivityTimeoutForRtspOnly() {
		tcpActivityTimeoutChecker.setTcpActivityTimeoutForRtspOnly();
	}

	@Override
	public void setTcpActivityTimeoutForRtxp() {
		tcpActivityTimeoutChecker.setTcpActivityTimeoutForRtxp();
	}

	@Override
	public void resetTcpActivityTimeoutTimer() {
		tcpActivityTimeoutChecker.resetTcpActivityTimeoutTimer();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean canReadRtsp()
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsRead.lock();
		try {
			return threadRead.canReadRtsp();
		} finally {
			lockOpsRead.unlock();
		}
	}

	@Override
	public Optional<String> readRtspLine()
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsRead.lock();
		try {
			return threadRead.readRtspLine();
		} finally {
			lockOpsRead.unlock();
		}
	}

	@Override
	public void writeRtspLines(@NonNull List<@NonNull String> lines)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsWrite.lock();
		try {
			threadWrite.writeRtspLines(lines);
		} finally {
			lockOpsWrite.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean canReadRtpOrRtcp(@NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsRead.lock();
		try {
			return threadRead.canReadRtpOrRtcp(channNr);
		} finally {
			lockOpsRead.unlock();
		}
	}

	@Override
	public boolean readRtpOrRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsRead.lock();
		try {
			return threadRead.readRtpOrRtcpBinary(buf, channNr);
		} finally {
			lockOpsRead.unlock();
		}
	}

	@Override
	public void writeRtpOrRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		lockOpsWrite.lock();
		try {
			threadWrite.writeRtpOrRtcpBinary(bufView, channNr);
		} finally {
			lockOpsWrite.unlock();
		}
	}

}
