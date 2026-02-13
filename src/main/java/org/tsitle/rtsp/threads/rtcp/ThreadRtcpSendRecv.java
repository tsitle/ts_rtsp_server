package org.tsitle.rtsp.threads.rtcp;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtcp.*;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.threads.rtp.ParamsThreadRtcp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadRtcpSendRecv extends ThreadPausableBase {

	private final ParamsThreadRtcp params;
	private final DatagramSocket parRtcpSocketUdp;

	private final AtomicInteger targetCongestionLevel = new AtomicInteger(0);
	private final DatagramPacket cacheDpRecv;
	private final BufferExt cacheRecvBuf1 = new BufferExt();
	private final BufferExt cacheRecvBuf2 = new BufferExt();
	private final BufferExt cacheRecvBuf3 = new BufferExt();
	@SuppressWarnings("FieldCanBeLocal")
	private Instant lastRtcpPacketReceived = null;

	private final Queue<BufferExt> queueSend = new ConcurrentLinkedQueue<>();

	/**
	 * Constructor.
	 * @param params Thread parameters
	 */
	public ThreadRtcpSendRecv(ParamsThreadRtcp params) {
		if (params == null) {
			throw new IllegalArgumentException("Thread parameters cannot be null");
		}
		this.params = params.clone();
		this.parRtcpSocketUdp = params.getRtcpSocketUdp().orElseThrow();

		//
		byte[] rtcpBuf = new byte[1024];
		this.cacheDpRecv = new DatagramPacket(rtcpBuf, rtcpBuf.length);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized int getTargetCongestionLevel() {
		return targetCongestionLevel.get();
	}

	public synchronized void appendToSendQueque(BufferExt rtcpPacketsBuf) {
		BufferExt tmpBuf = new BufferExt();
		tmpBuf.copyOf(rtcpPacketsBuf);
		queueSend.add(tmpBuf);
	}

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		//
		isRunning.set(true);

		try {
			while (! (doStop.get() || parRtcpSocketUdp.isClosed())) {
				if (! mainLoop()) {
					break;
				}
			}
			// keep running for another 5s
			Instant tmpStart = Instant.now();
			while (! parRtcpSocketUdp.isClosed() && Duration.between(tmpStart, Instant.now()).toMillis() < 5000) {
				if (! mainLoop()) {
					break;
				}
			}
		} catch (UdpSocketIoException ex) {
			logError(FNC_NAME, ex.toString());
		} finally {
			parRtcpSocketUdp.close();
			isRunning.set(false);
			logInfo(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void stopThreadHook() {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean mainLoop() throws UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		//
		try {
			if (! isPaused.get() && ! queueSend.isEmpty()) {
				sendFromQueque();
			}
			//
			parRtcpSocketUdp.receive(cacheDpRecv);  // blocks for setSoTimeout() value
			lastRtcpPacketReceived = Instant.now();
		} catch (SocketTimeoutException ex1) {
			return true;
		} catch (IOException ex) {
			if (doStop.get()) {
				return false;
			}
			throw new UdpSocketIoException(FNC_NAME + ": receive failed: " + ex.getMessage());
		}

		//
		if (isPaused.get()) {
			return true;
		}

		//
		if (lastRtcpPacketReceived == null) {
			throw new IllegalStateException("lastRtcpPacketReceived cannot be null");
		}
		/*logInfo(FNC_NAME, "Received RTCP packet on port " +
				parRtcpSocketUdp.getLocalPort() + " from port " + cacheDpRecv.getPort());*/
		cacheRecvBuf1.copyOf(cacheDpRecv.getData(), cacheDpRecv.getOffset(), cacheDpRecv.getLength());
		handleReceived();

		return true;
	}

	private void sendFromQueque() throws IOException {
		BufferExt tmpBuf = queueSend.poll();
		if (tmpBuf == null) {
			return;
		}
		// send the compound packet as a DatagramPacket over the UDP socket
		DatagramPacket sendDp = new DatagramPacket(
				tmpBuf.getBuf(),
				tmpBuf.getUsed(),
				params.getClientIpAddr().orElseThrow(),
				params.getClientDestPortRtcp()
			);
		parRtcpSocketUdp.send(sendDp);
	}

	private void handleReceived() {
		final String FNC_NAME = getClass().getSimpleName() + ".handleReceived()";

		while (! cacheRecvBuf1.isEmpty()) {
			if (cacheRecvBuf1.getUsed() >= 4 &&
					(cacheRecvBuf1.get(0) == (byte)0xCE && cacheRecvBuf1.get(1) == (byte)0xFA &&
						cacheRecvBuf1.get(2) == (byte)0xED && cacheRecvBuf1.get(3) == (byte)0xFE)) {
				/*
				 * VLC dummy 4-byte packet to
				 *   - open NAT bindings
				 *   - punch holes through firewalls
				 *   - test whether the RTCP port is reachable
				 * This happens before or alongside real RTCP traffic.
				 * VLC does not expect the server to parse or respond to it
				 */
				cacheRecvBuf2.copyOf(cacheRecvBuf1, 4, cacheRecvBuf1.getUsed() - 4);
				cacheRecvBuf1.copyOf(cacheRecvBuf2);
				continue;
			}

			//
			final RtcpPacketHeader rtcpPktHd = new RtcpPacketHeader(cacheRecvBuf1);
			final int tmpPktSz = rtcpPktHd.getPacketSize();
			if (tmpPktSz < 0 || tmpPktSz > cacheRecvBuf1.getUsed()) {
				logError(FNC_NAME, "have invalid RTCP packet, discarding it");
				// output each byte of the packet to the console for debugging purposes
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < cacheRecvBuf1.getUsed(); i++) {
					sb.append(String.format("%02X ", cacheRecvBuf1.get(i)));
				}
				logInfo(FNC_NAME, "Discarded packet: 0x" + sb);
				break;
			}
			if (tmpPktSz < cacheRecvBuf1.getUsed()) {
				cacheRecvBuf3.copyOf(cacheRecvBuf1, 0, tmpPktSz);  // contains the current packet
				cacheRecvBuf2.copyOf(cacheRecvBuf1, tmpPktSz, cacheRecvBuf1.getUsed() - tmpPktSz);
				cacheRecvBuf1.copyOf(cacheRecvBuf2);
			} else {
				cacheRecvBuf3.copyOf(cacheRecvBuf1);  // contains the current packet
				cacheRecvBuf1.clear();
			}

			// handle the payload
			switch (rtcpPktHd.getPayloadType()) {
				case RR:
					handleRtcpPacketRR(rtcpPktHd);
					break;
				case SR:
					handleRtcpPacketSR(rtcpPktHd);
					break;
				case SDES:
					handleRtcpPacketSDES(rtcpPktHd);
					break;
				case BYE:
					handleRtcpPacketBYE(rtcpPktHd);
					break;
				default:
					logError(FNC_NAME, "have unsupported RTCP packet type: " + rtcpPktHd.getPayloadType());
			}
		}
	}

	private void handleRtcpPacketRR(RtcpPacketHeader rtcpPktHd) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketRR()";

		if (rtcpPktHd.getItemsCount() == 0) {
			logInfo(FNC_NAME, "RTCP packet without items");
			return;
		}
		// read and validate the packet
		RtcpPacketRR rtcpPktInner = new RtcpPacketRR(rtcpPktHd, cacheRecvBuf3);
		//
		for (int itemNr = 1; itemNr <= rtcpPktHd.getItemsCount(); itemNr++) {
			RtcpInnerRecpReportBlock innerRb = rtcpPktInner.getRecpReportBlock(itemNr).orElseThrow();
			/*logInfo(FNC_NAME, String.format("RTT: %dms, FLost: %.3f%%",
					innerRb.getRoundTripTimeMillis(lastRtcpPacketReceived).orElse(-1L),
					innerRb.getFractionLostPercent() * 100.0f));*/
			if (itemNr != 1) {
				continue;
			}
			// set congestion level
			final float fractionLost = innerRb.getFractionLostPercent();
			if (fractionLost >= 0 && fractionLost <= 0.01) {
				targetCongestionLevel.set(0);  // less than 0.01 assume negligible
			} else if (fractionLost > 0.01 && fractionLost <= 0.25) {
				targetCongestionLevel.set(1);
			} else if (fractionLost > 0.25 && fractionLost <= 0.5) {
				targetCongestionLevel.set(2);
			} else if (fractionLost > 0.5 && fractionLost <= 0.75) {
				targetCongestionLevel.set(3);
			} else {
				targetCongestionLevel.set(4);
			}
		}
	}

	private void handleRtcpPacketSR(RtcpPacketHeader rtcpPktHd) {
		// read and validate the packet
		RtcpPacketSR rtcpPktInner = new RtcpPacketSR(rtcpPktHd, cacheRecvBuf3);
		if (rtcpPktInner.getRawPacketSize() == 0) {
			throw new IllegalStateException();  // only for the linter
		}
	}

	private void handleRtcpPacketSDES(RtcpPacketHeader rtcpPktHd) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketSDES()";

		if (rtcpPktHd.getItemsCount() == 0) {
			logInfo(FNC_NAME, "RTCP packet without items");
			return;
		}
		// read and validate the packet
		RtcpPacketSDES rtcpPktInner = new RtcpPacketSDES(rtcpPktHd, cacheRecvBuf3);
		if (rtcpPktInner.getItemsCount() > 1000) {
			throw new IllegalStateException();  // only for the linter
		}
	}

	private void handleRtcpPacketBYE(RtcpPacketHeader rtcpPktHd) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketBYE()";

		// read and validate the packet
		RtcpPacketBYE rtcpPktInner = new RtcpPacketBYE(rtcpPktHd, cacheRecvBuf3);
		if (rtcpPktInner.getRawPacketSize() == 0) {
			return;  // only for the linter
		}
		logInfo(FNC_NAME, "received BYE");
	}

	/**
	 * @TODO
	 */
	private void logInfo(String fncName, String msg) {
		System.out.format("<ses=%s|str=%d|ssrc=%08X> %s: %s%n",
				params.getDebugSessionId().orElseThrow(), params.getDebugStreamId(), params.getRtspSsrcId(),
				fncName, msg);
	}

	private void logError(String fncName, String msg) {
		System.err.format("<ses=%s|str=%d|ssrc=%08X> %s: %s%n",
				params.getDebugSessionId().orElseThrow(), params.getDebugStreamId(), params.getRtspSsrcId(),
				fncName, msg);
	}

}
