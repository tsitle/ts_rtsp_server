package org.tsitle.rtsp.threads;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class RtxpTcpReadWrite {

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

	/** TCP socket used to send/receive RTxP messages */
	private final Socket socketTcp;

	private final InputStream socketIs;
	private final OutputStream socketOs;

	private final AtomicBoolean doStop = new AtomicBoolean(false);

	private final Queue<String> queueRtspLinesRcvd = new ConcurrentLinkedDeque<>();
	private final Map<@NonNull Integer, @NonNull Queue<@NonNull BufferExt>> mapQueueRtpRtcpDataRcvd = new ConcurrentHashMap<>();

	private final BlockedState blockedState = new BlockedState();

	public RtxpTcpReadWrite(@NonNull Socket socketTcp) {
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

	public boolean canReadRtcp(int channId) throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTP_RTCP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (mapQueueRtpRtcpDataRcvd.containsKey(channId) && ! mapQueueRtpRtcpDataRcvd.get(channId).isEmpty()) {
				return true;
			}
			if (doStop.get() || socketTcp.isClosed()) {
				return false;
			}
			internalReadSocket();
			if (! mapQueueRtpRtcpDataRcvd.containsKey(channId)) {
				return false;
			}
			return (! (doStop.get() || mapQueueRtpRtcpDataRcvd.get(channId).isEmpty()));
		} finally {
			blockedState.unblock(Flag.QUEUE_RTP_RTCP_RCVD);
		}
	}

	public boolean readRtcpBinary(@NonNull BufferExt buf, int channId) throws TcpSocketIoException {
		return internalReadRtpRtcpBinary(buf, channId);
	}

	public void writeRtcpBinary(@NonNull BufferView bufView, int channId) throws TcpSocketIoException {
		internalWriteRtpRtcpBinary(bufView, channId);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeRtpBinary(@NonNull BufferView bufView, int channId) throws TcpSocketIoException {
		internalWriteRtpRtcpBinary(bufView, channId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalReadSocket() throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.SOCKET_READ);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (socketTcp.isClosed()) {
				return;
			}
			while (! doStop.get()) {
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
					if (tmpInt == '$') {
						internalReadSocket_binary();
					} else {
						internalReadSocket_string((char)tmpInt);
					}
					haveSomething = true;
				}
				if (! haveSomething) {
					break;
				}
			}
		} catch (IOException e) {
			throw new TcpSocketIoException(e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_READ);
		}
	}

	private void internalReadSocket_binary() throws IOException {
		int channId = -1;
		int packetLen = 4;  // we need at least 3 more bytes
		int packetRd = 1;
		BufferExt payloadBe = new BufferExt();
		int payloadLen = 0;
		int payloadOffset = 0;
		while (! doStop.get() && packetRd < packetLen) {
			if (packetRd == 1) {
				channId = (socketIs.available() > 0 ? socketIs.read() : -1);
				if (channId == -1) {
					try {
						Thread.sleep(Duration.ofNanos(100_000L));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();  // restore flag
						break;
					}
					continue;
				}
				if (! mapQueueRtpRtcpDataRcvd.containsKey(channId)) {
					mapQueueRtpRtcpDataRcvd.put(channId, new ConcurrentLinkedDeque<>());
				}
			} else if (packetRd == 2 || packetRd == 3) {
				int tmpVal = (socketIs.available() > 0 ? socketIs.read() : -1);
				if (tmpVal == -1) {
					try {
						Thread.sleep(Duration.ofNanos(100_000L));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();  // restore flag
						break;
					}
					continue;
				}
				if (packetRd == 2) {
					payloadLen = ((tmpVal << 8) & 0xFF00);
				} else {
					payloadLen |= (tmpVal & 0x00FF);
					packetLen += payloadLen;
				}
			} else if (socketIs.available() > 0) {
				payloadBe.setUsed(packetLen - packetRd);
				int tmpDidRead = socketIs.read(payloadBe.getBaPtr(), payloadOffset, packetLen - packetRd);
				if (tmpDidRead == -1) {
					throw new IOException("internalReadSocket_binary(): " + "Could not read from socket");
				}
				if (tmpDidRead > 0) {
					payloadOffset += tmpDidRead;
					packetRd += (tmpDidRead - 1);
				}
			}
			++packetRd;
		}
		mapQueueRtpRtcpDataRcvd.get(channId).add(payloadBe);
	}

	private static String listToString(ArrayList<Character> list) {
		StringBuilder builder = new StringBuilder(list.size());
		for (Character c : list) {
			builder.append(c);
		}
		return builder.toString();
	}

	private void internalReadSocket_string(char firstChar) throws IOException {
		ArrayList<Character> tmpList = new ArrayList<>();
		tmpList.add(firstChar);

		boolean haveCr = (firstChar == CRLF.charAt(0));
		while (! doStop.get()) {
			int tmpInt = (socketIs.available() > 0 ? socketIs.read() : -1);
			if (tmpInt == -1) {
				try {
					Thread.sleep(Duration.ofNanos(1_000_000L));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
				continue;
			}
			tmpList.add((char)tmpInt);
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

	private boolean internalReadRtpRtcpBinary(@NonNull BufferExt buf, int channId) throws TcpSocketIoException {
		try {
			blockedState.waitForUnblockedAndThenBlock(Flag.QUEUE_RTP_RTCP_RCVD);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
		try {
			if (doStop.get() || socketTcp.isClosed()) {
				return false;
			}
			if (! mapQueueRtpRtcpDataRcvd.containsKey(channId) || mapQueueRtpRtcpDataRcvd.get(channId).isEmpty()) {
				internalReadSocket();
				if (! mapQueueRtpRtcpDataRcvd.containsKey(channId) || mapQueueRtpRtcpDataRcvd.get(channId).isEmpty()) {
					return false;
				}
			}
			BufferExt tmpBuf = mapQueueRtpRtcpDataRcvd.get(channId).poll();
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
		} catch (IOException e) {
			throw new TcpSocketIoException(FNC_NAME + ": " + e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_WRITE);
		}
	}

	private void internalWriteRtpRtcpBinary(@NonNull BufferView bufView, int channId) throws TcpSocketIoException {
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
			byte[] tmpBa = new byte[4];
			tmpBa[0] = (byte)'$';
			tmpBa[1] = (byte)channId;
			tmpBa[2] = (byte)((bufView.getLength() >> 8) & 0xFF);
			tmpBa[3] = (byte)(bufView.getLength() & 0xFF);
			socketOs.write(tmpBa, 0, 4);
			socketOs.write(bufView.getInternalBaPtr(), bufView.getOffset(), bufView.getLength());
		} catch (IOException e) {
			throw new TcpSocketIoException(FNC_NAME + ": " + e.getMessage());
		} finally {
			blockedState.unblock(Flag.SOCKET_WRITE);
		}
	}

}
