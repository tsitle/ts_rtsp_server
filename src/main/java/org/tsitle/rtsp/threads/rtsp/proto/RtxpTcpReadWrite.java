package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TCP Socket wrapper for reading/writing RTxP messages from/to a single TCP socket.<br />
 * If the transport mode for the RTSP session that owns this TCP socket is 'TCP with interleaved RTP/RTCP',
 * then this class can handle RTSP/RTP/RTCP messages in the same TCP connection.<br />
 * If the transport mode for the RTSP session that owns this TCP socket is 'UDP',
 * then this class can handle RTSP messages in the TCP connection.<br />
 * This class is thread-safe and can be used by multiple threads to read/write RTxP messages from/to the TCP socket.<br />
 * <br />
 * See <a href="https://datatracker.ietf.org/doc/html//rfc2326.html#section-10.12">RFC-2326 Section 10.12</a> for more information.
 */
public final class RtxpTcpReadWrite {

	private enum Flag { SOCKET_READ, SOCKET_WRITE, QUEUE_RTSP_RCVD, QUEUE_RTP_RTCP_RCVD}

	private static class BlockedState {
		private final ReentrantLock lock = new ReentrantLock();
		private final Condition stateChanged = lock.newCondition();

		private boolean socketReadBlocked;
		private boolean socketWriteBlocked;
		private boolean queueRtspRcvdBlocked;
		private boolean queueRtpRtcpRcvdBlocked;
		private boolean doStop;

		public void waitForUnblockedAndThenBlock(Flag flag) throws InterruptedException {
			lock.lock();
			try {
				while (! doStop && isBlocked(flag)) {
					stateChanged.await();
				}
				if (doStop) {
					return;
				}
				setBlocked(flag, true);
			} finally {
				lock.unlock();
			}
		}

		public void unblock(Flag flag) {
			setBlocked(flag, false);
		}

		public void stop() {
			lock.lock();
			try {
				doStop = true;
				stateChanged.signalAll();
			} finally {
				lock.unlock();
			}
		}

		private void setBlocked(Flag flag, boolean value) {
			lock.lock();
			try {
				setBlockedInternal(flag, value);
				stateChanged.signalAll();
			} finally {
				lock.unlock();
			}
		}

		private boolean isBlocked(Flag flag) {
			return switch (flag) {
				case SOCKET_READ -> socketReadBlocked;
				case SOCKET_WRITE -> socketWriteBlocked;
				case QUEUE_RTSP_RCVD -> queueRtspRcvdBlocked;
				case QUEUE_RTP_RTCP_RCVD -> queueRtpRtcpRcvdBlocked;
			};
		}

		private void setBlockedInternal(Flag flag, boolean value) {
			switch (flag) {
				case SOCKET_READ -> socketReadBlocked = value;
				case SOCKET_WRITE -> socketWriteBlocked = value;
				case QUEUE_RTSP_RCVD -> queueRtspRcvdBlocked = value;
				case QUEUE_RTP_RTCP_RCVD -> queueRtpRtcpRcvdBlocked = value;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final String CRLF = "\r\n";
	private static final int RTSP_INPUT_LINE_MAX_LENGTH = 1024 * 4;
	private static final int QUEUES_MAX_SIZE = 50;
	private static final int READ_MAX_RETRIES = 2;

	private static final long TCP_ACTIVITY_TIMEOUT_SECS_DEF = 10L;  // VLC sometimes takes quite a while to complete an RTSP setup right after being started
	private static final long TCP_ACTIVITY_TIMEOUT_SECS_RTSP_ONLY = 60L;

	/** TCP socket used to send/receive RTxP messages */
	private final Socket socketTcp;

	private final InputStream socketIs;
	private final OutputStream socketOs;

	private final AtomicBoolean doStop = new AtomicBoolean(false);

	private final AtomicBoolean isRtpRtcpAllowed = new AtomicBoolean(false);
	private final AtomicInteger tcpActivityTimeout = new AtomicInteger((int)TCP_ACTIVITY_TIMEOUT_SECS_DEF);

	private final Queue<String> queueRtspLinesRcvd = new ConcurrentLinkedDeque<>();
	private final Map<@NonNull RtspProtoTcpChannelNr, @NonNull Queue<@NonNull BufferExt>> mapQueueRtpRtcpDataRcvd = new ConcurrentHashMap<>();

	private final BlockedState blockedState = new BlockedState();

	private Instant lastActivityTime = Instant.now();

	/**
	 * Constructor.
	 * @param socketTcp TCP socket used to send/receive RTxP messages
	 */
	public RtxpTcpReadWrite(@NonNull Socket socketTcp) {
		try {
			if (socketTcp.getSoTimeout() <= 0) {
				throw new IllegalArgumentException("socketTcp.getSoTimeout() must be positive");
			}
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}

		this.socketTcp = socketTcp;

		try {
			this.socketIs = socketTcp.getInputStream();
			this.socketOs = socketTcp.getOutputStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized boolean isSocketClosed() {
		if (doStop.get()) {
			return true;
		}
		return socketTcp.isClosed();
	}

	public synchronized void closeSocket() {
		if (doStop.get()) {
			return;
		}
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTSP_RCVD);
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTP_RTCP_RCVD);
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_READ);
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			doStop.set(true);
			socketTcp.close();
		} catch (IOException e) {
			// ignore
		} finally {
			blockedState.unblock(Flag.QUEUE_RTSP_RCVD);
			blockedState.unblock(Flag.QUEUE_RTP_RTCP_RCVD);
			blockedState.unblock(Flag.SOCKET_READ);
			blockedState.unblock(Flag.SOCKET_WRITE);
			blockedState.stop();
		}
	}

	@SuppressWarnings("unused")
	public synchronized int getSocketLocalPort() {
		if (doStop.get()) {
			return -1;
		}
		return socketTcp.getLocalPort();
	}

	public synchronized int getSocketRemotePort() {
		if (doStop.get()) {
			return -1;
		}
		return socketTcp.getPort();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void setIsRtpRtcpAllowed(boolean value) {
		isRtpRtcpAllowed.set(value);
	}

	public synchronized void setTcpActivityTimeoutForRtspOnly() {
		tcpActivityTimeout.set((int)TCP_ACTIVITY_TIMEOUT_SECS_RTSP_ONLY);
	}

	public synchronized void setTcpActivityTimeoutForRtxp() {
		tcpActivityTimeout.set((int)TCP_ACTIVITY_TIMEOUT_SECS_DEF);
	}

	public synchronized void resetTcpActivityTimeoutTimer() {
		lastActivityTime = Instant.now();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean canReadRtsp() throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTSP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (! queueRtspLinesRcvd.isEmpty()) {
				return true;
			}
			if (doStop.get() || socketTcp.isClosed()) {
				return false;
			}
			internalReadSocket();
			return (! (doStop.get() || queueRtspLinesRcvd.isEmpty()));
		} finally {
			blockedState.unblock(Flag.QUEUE_RTSP_RCVD);
		}
	}

	public Optional<String> readRtspLine() {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTSP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (queueRtspLinesRcvd.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(queueRtspLinesRcvd.poll());
		} finally {
			blockedState.unblock(Flag.QUEUE_RTSP_RCVD);
		}
	}

	public void writeRtspLines(@NonNull List<@NonNull String> lines) throws TcpSocketIoException {
		internalWriteRtspLines(lines);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean canReadRtcp(@NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTP_RTCP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (channNr.isEmpty()) {
				return false;
			}
			if (mapQueueRtpRtcpDataRcvd.containsKey(channNr) && ! mapQueueRtpRtcpDataRcvd.get(channNr).isEmpty()) {
				return true;
			}
			if (doStop.get() || socketTcp.isClosed()) {
				return false;
			}
			internalReadSocket();
			if (! mapQueueRtpRtcpDataRcvd.containsKey(channNr)) {
				return false;
			}
			return (! (doStop.get() || mapQueueRtpRtcpDataRcvd.get(channNr).isEmpty()));
		} finally {
			blockedState.unblock(Flag.QUEUE_RTP_RTCP_RCVD);
		}
	}

	public boolean readRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		return internalReadRtpRtcpBinary(buf, channNr);
	}

	public void writeRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		internalWriteRtpRtcpBinary(bufView, channNr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeRtpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		internalWriteRtpRtcpBinary(bufView, channNr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalReadSocket() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadSocket()";

		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (socketTcp.isClosed()) {
				return;
			}
			boolean haveAnythingAtAll = false;
			int tmpQueueSize = 0;
			boolean localIsRtpRtcpAllowed = isRtpRtcpAllowed.get();
			while (! doStop.get()) {
				checkTcpActivityTimeout(FNC_NAME);
				boolean haveSomething = false;
				while (! doStop.get()) {
					int tmpInt;
					try {
						tmpInt = socketIs.read();  // blocks for setSoTimeout() value
					} catch (SocketTimeoutException e) {
						break;
					}
					if (tmpInt == -1) {
						break;
					}
					haveAnythingAtAll = true;
					if (localIsRtpRtcpAllowed && tmpInt == '$') {
						tmpQueueSize = internalReadSocket_binary();
					} else {
						internalReadSocket_string((char)tmpInt);
						tmpQueueSize = queueRtspLinesRcvd.size();
					}
					haveSomething = true;
				}
				if (! haveSomething || tmpQueueSize >= QUEUES_MAX_SIZE) {
					break;
				}
			}
			//
			if (haveAnythingAtAll) {
				resetTcpActivityTimeoutTimer();
			}
		} catch (IOException e) {
			throw new TcpSocketIoException(FNC_NAME + ": " + e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_READ);
		}
	}

	private int internalReadSocket_binary() throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadSocket_binary()";

		int channIdInt = -1;
		RtspProtoTcpChannelNr channIdObj = new RtspProtoTcpChannelNr();
		int packetLen = 4;  // we need at least 3 more bytes
		int packetRd = 1;
		BufferExt payloadBe = new BufferExt();
		int payloadLen = 0;
		int payloadOffset = 0;
		int readTimeoutCnt = 0;
		while (! doStop.get() && packetRd < packetLen) {
			if (packetRd == 1) {
				try {
					channIdInt = socketIs.read();  // blocks for setSoTimeout() value
					if (channIdInt == -1) {
						throw new IOException(FNC_NAME + ": Could not read from socket");
					}
					readTimeoutCnt = 0;
				} catch (SocketTimeoutException e) {
					if (++readTimeoutCnt >= READ_MAX_RETRIES) {
						break;
					}
					continue;
				}
				try {
					channIdObj.setChannel8bit(channIdInt);
				} catch (RtspProtoNumberRangeException e) {
					// this will never happen
				}
				if (! mapQueueRtpRtcpDataRcvd.containsKey(channIdObj)) {
					mapQueueRtpRtcpDataRcvd.put(channIdObj, new ConcurrentLinkedDeque<>());
				}
			} else if (packetRd == 2 || packetRd == 3) {
				int tmpVal;
				try {
					tmpVal = socketIs.read();  // blocks for setSoTimeout() value
					if (tmpVal == -1) {
						throw new IOException(FNC_NAME + ": Could not read from socket");
					}
					readTimeoutCnt = 0;
				} catch (SocketTimeoutException e) {
					if (++readTimeoutCnt >= READ_MAX_RETRIES) {
						break;
					}
					continue;
				}
				if (packetRd == 2) {
					payloadLen = ((tmpVal << 8) & 0xFF00);
				} else {
					payloadLen |= (tmpVal & 0x00FF);
					packetLen += payloadLen;
					payloadBe.increaseSize(payloadLen);
				}
			} else {
				int tmpDidRead;
				try {
					tmpDidRead = socketIs.read(payloadBe.getBaPtr(), payloadOffset, packetLen - packetRd);  // blocks for setSoTimeout() value
					if (tmpDidRead == -1) {
						throw new IOException(FNC_NAME + ": " + "Could not read from socket");
					}
					readTimeoutCnt = 0;
				} catch (SocketTimeoutException e) {
					if (++readTimeoutCnt >= READ_MAX_RETRIES) {
						break;
					}
					continue;
				}
				if (tmpDidRead > 0) {
					payloadOffset += tmpDidRead;
					packetRd += (tmpDidRead - 1);
				}
			}
			++packetRd;
		}
		payloadBe.setUsed(payloadLen);
		if (channIdObj.isEmpty()) {
			return 0;
		}
		mapQueueRtpRtcpDataRcvd.get(channIdObj).add(payloadBe);
		return mapQueueRtpRtcpDataRcvd.get(channIdObj).size();
	}

	private static String listToString(ArrayList<Character> list) {
		StringBuilder builder = new StringBuilder(list.size());
		for (Character c : list) {
			builder.append(c);
		}
		return builder.toString();
	}

	private void internalReadSocket_string(char firstChar) throws IOException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadSocket_string()";

		ArrayList<Character> tmpList = new ArrayList<>();
		tmpList.add(firstChar);

		boolean haveCr = (firstChar == CRLF.charAt(0));
		int tmpInt;
		int readTimeoutCnt = 0;
		while (! doStop.get()) {
			try {
				tmpInt = socketIs.read();  // blocks for setSoTimeout() value
				if (tmpInt == -1) {
					throw new IOException(FNC_NAME + ": Could not read from socket");
				}
				readTimeoutCnt = 0;
			} catch (SocketTimeoutException e) {
				if (++readTimeoutCnt >= READ_MAX_RETRIES) {
					break;
				}
				continue;
			}
			tmpList.add((char)tmpInt);
			if (tmpList.size() >= RTSP_INPUT_LINE_MAX_LENGTH) {
				break;
			}
			if (tmpInt == CRLF.charAt(0)) {
				haveCr = true;
			} else if (tmpInt == CRLF.charAt(1)) {
				if (haveCr) {
					break;
				}
			}
		}

		String tmpStr = listToString(tmpList);
		queueRtspLinesRcvd.add(tmpStr);
	}

	private boolean internalReadRtpRtcpBinary(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTP_RTCP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (doStop.get() || socketTcp.isClosed()) {
				return false;
			}
			if (channNr.isEmpty()) {
				return false;
			}
			if (! mapQueueRtpRtcpDataRcvd.containsKey(channNr) || mapQueueRtpRtcpDataRcvd.get(channNr).isEmpty()) {
				internalReadSocket();
				if (! mapQueueRtpRtcpDataRcvd.containsKey(channNr) || mapQueueRtpRtcpDataRcvd.get(channNr).isEmpty()) {
					return false;
				}
			}
			BufferExt tmpBuf = mapQueueRtpRtcpDataRcvd.get(channNr).poll();
			if (tmpBuf == null) {
				return false;
			}
			buf.copyOf(tmpBuf);
			return true;
		} finally {
			blockedState.unblock(Flag.QUEUE_RTP_RTCP_RCVD);
		}
	}

	private void internalWriteRtspLines(@NonNull List<@NonNull String> lines) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalWriteRtspLines()";

		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			for (String line : lines) {
				if (doStop.get() || socketTcp.isClosed()) {
					return;
				}
				socketOs.write(line.getBytes(StandardCharsets.UTF_8));
			}
			//
			resetTcpActivityTimeoutTimer();
		} catch (IOException e) {
			throw new TcpSocketIoException(FNC_NAME + ": " + e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_WRITE);
		}
	}

	private void internalWriteRtpRtcpBinary(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalWriteRtpRtcpBinary()";

		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_WRITE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (doStop.get() || socketTcp.isClosed()) {
				return;
			}
			if (channNr.isEmpty()) {
				return;
			}
			final int tmpChannInt = channNr.getChannel8bit().orElseThrow();
			byte[] tmpBa = new byte[4];
			tmpBa[0] = (byte)'$';
			tmpBa[1] = (byte)tmpChannInt;
			tmpBa[2] = (byte)((bufView.getLength() >> 8) & 0xFF);
			tmpBa[3] = (byte)(bufView.getLength() & 0xFF);
			socketOs.write(tmpBa, 0, 4);
			socketOs.write(bufView.getInternalBaPtr(), bufView.getOffset(), bufView.getLength());
			//
			resetTcpActivityTimeoutTimer();
		} catch (IOException e) {
			throw new TcpSocketIoException(FNC_NAME + ": " + e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_WRITE);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private synchronized void checkTcpActivityTimeout(@NonNull String fncName) throws TcpSocketIoException {
		if (Instant.now().minusSeconds(tcpActivityTimeout.get()).isAfter(lastActivityTime)) {
			throw new TcpSocketIoException(fncName + ": TCP activity timeout");
		}
	}

}
