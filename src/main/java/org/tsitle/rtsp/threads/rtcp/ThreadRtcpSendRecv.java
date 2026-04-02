package org.tsitle.rtsp.threads.rtcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.packets.rtcp.*;
import org.tsitle.rtsp.security.SrtcpContextInbound;
import org.tsitle.rtsp.security.SrtcpContextOutbound;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtcp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadRtcpSendRecv extends ThreadPausableBase {

	private final ParamsThreadRtcp params;
	private final @Nullable DatagramSocket parRtcpSocketUdp;
	private final @Nullable RtxpTcpReadWrite parRtcpRwIfTcp;

	private final AtomicInteger targetCongestionLevel = new AtomicInteger(0);
	private final DatagramPacket cacheDpRecv;
	private final BufferExt cacheRecvBuf1 = new BufferExt();
	private final BufferExt cacheRecvBuf2 = new BufferExt();
	private final BufferExt cacheRecvBuf3 = new BufferExt();
	@SuppressWarnings("FieldCanBeLocal")
	private Instant lastRtcpPacketReceived = null;

	private final Queue<BufferExt> queueSend = new ConcurrentLinkedQueue<>();

	private final @Nullable SrtcpContextInbound srtcpCtxInbound;
	private final @Nullable SrtcpContextOutbound srtcpCtxOutbound;

	/**
	 * Constructor.
	 * @param params Thread parameters
	 */
	public ThreadRtcpSendRecv(
				@NonNull ParamsThreadRtcp params
			) {
		super(params.getLogMsgInterface().orElseThrow());

		this.params = params.clone();
		this.parRtcpSocketUdp = params.getTpSocketUdp().orElse(null);
		this.parRtcpRwIfTcp = params.getTpClientDestTcpIf().orElse(null);

		//
		if (params.getCryptoIsRtxpEncryptionEnabled()) {
			try {
				this.srtcpCtxInbound = new SrtcpContextInbound(params.getCryptoKmdInbound().orElseThrow());
				this.srtcpCtxOutbound = new SrtcpContextOutbound(params.getCryptoKmdOutbound().orElseThrow());
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
						"SrtxpSecurityException caught: " + e.getMessage());
			}
		} else {
			this.srtcpCtxInbound = null;
			this.srtcpCtxOutbound = null;
		}

		//
		byte[] rtcpBuf = new byte[1024];
		this.cacheDpRecv = new DatagramPacket(rtcpBuf, rtcpBuf.length);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized int getTargetCongestionLevel() {
		return targetCongestionLevel.get();
	}

	public synchronized void appendToSendQueue(BufferExt rtcpPacketsBuf) {
		BufferExt tmpBuf = new BufferExt();
		tmpBuf.copyOf(rtcpPacketsBuf);
		queueSend.add(tmpBuf);
	}

	public synchronized void appendByePacketToSendQueue() {
		final String FNC_NAME = getClass().getSimpleName() + ".appendByePacketToSendQueue()";

		logDebug(FNC_NAME, String.format(
				"Sending BYE packet (ss=%d, SSRC=0x%08X)", params.getStreamSourceId(), params.getRtspSsrcId()));
		BufferExt packetCompoundBuf = new BufferExt();
		sendBye_buildRtcpCompound(packetCompoundBuf);
		//
		queueSend.add(packetCompoundBuf);
	}

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		try {
			while (! doStop.get()) {
				if ((parRtcpSocketUdp != null && parRtcpSocketUdp.isClosed()) ||
						(parRtcpRwIfTcp != null && parRtcpRwIfTcp.isSocketClosed())) {
					break;
				}
				if (! mainLoop()) {
					break;
				}
			}
			// keep running for another 5s
			if (! doStop.get()) {
				logInfo(FNC_NAME, "Receiving RTCP packets stopped, waiting for up to 5s for more packets");
				Instant tmpStart = Instant.now();
				while (Duration.between(tmpStart, Instant.now()).toMillis() < 5000) {
					if ((parRtcpSocketUdp != null && parRtcpSocketUdp.isClosed()) ||
							(parRtcpRwIfTcp != null && parRtcpRwIfTcp.isSocketClosed())) {
						break;
					}
					if (! mainLoop()) {
						break;
					}
				}
			}
		} catch (UdpSocketIoException e) {
			logError(FNC_NAME, e.toString());
		} catch (TcpSocketIoException e) {
			// fail silently
		} finally {
			if (parRtcpSocketUdp != null) {
				parRtcpSocketUdp.close();
			}
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean mainLoop() throws UdpSocketIoException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		//
		try {
			if (! isPaused.get() && ! queueSend.isEmpty()) {
				sendFromQueue();
			}
			//
			if (parRtcpSocketUdp != null) {
				parRtcpSocketUdp.receive(cacheDpRecv);  // blocks for setSoTimeout() value
				/*logDebug(FNC_NAME, "Received RTCP packet on port " +
						parRtcpSocketUdp.getLocalPort() + " from port " + cacheDpRecv.getPort());*/
				cacheRecvBuf1.copyOf(cacheDpRecv.getData(), cacheDpRecv.getOffset(), cacheDpRecv.getLength());
			} else if (parRtcpRwIfTcp != null) {
				if (! parRtcpRwIfTcp.canReadRtcp(params.getTpClientDestTcpChann())) {
					return true;
				}
				boolean tmResB = parRtcpRwIfTcp.readRtcpBinary(cacheRecvBuf1, params.getTpClientDestTcpChann());
				if (! tmResB) {
					return false;
				}
			}
			lastRtcpPacketReceived = Instant.now();
		} catch (SocketTimeoutException ex1) {
			return true;
		} catch (IOException e) {
			if (doStop.get()) {
				return false;
			}
			throw new UdpSocketIoException(FNC_NAME + ": receive failed: " + e.getMessage());
		} catch (TcpSocketIoException e) {
			// fail silently
			return false;
		}

		//
		if (isPaused.get()) {
			return true;
		}

		//
		if (lastRtcpPacketReceived == null) {
			throw new IllegalStateException("lastRtcpPacketReceived cannot be null");
		}
		handleReceived();

		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendFromQueue() throws TcpSocketIoException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendFromQueue()";

		BufferExt plainPktBuf = queueSend.poll();
		if (plainPktBuf == null) {
			return;
		}
		BufferExt encrPktBuf = new BufferExt();
		BufferExt outpPacketPtr = plainPktBuf;
		if (params.getCryptoIsRtxpEncryptionEnabled() && srtcpCtxOutbound != null) {
			try {
				if (srtcpCtxOutbound.getSsrcId() != params.getRtspSsrcId()) {
					throw new SrtxpSecurityException(FNC_NAME + ": SSRC mismatch");
				}
				srtcpCtxOutbound.protectRtcpSrCompound(
						plainPktBuf,
						params.getRtspSsrcId(),
						encrPktBuf
					);
			} catch (SrtxpSecurityException e) {
				logError(FNC_NAME, "SrtxpSecurityException caught: " + e.getMessage());
				return;
			}
			outpPacketPtr = encrPktBuf;
		}

		// send the compound packet as a DatagramPacket over the UDP socket
		if (parRtcpSocketUdp != null) {
			DatagramPacket sendDp = new DatagramPacket(
					outpPacketPtr.getBaPtr(),
					outpPacketPtr.getUsed(),
					params.getTpClientIpAddr().orElseThrow(),
					params.getTpClientDestUdpPort()
				);
			try {
				parRtcpSocketUdp.send(sendDp);
			} catch (IOException e) {
				throw new UdpSocketIoException(FNC_NAME + ": IOException caught: " + e.getMessage());
			}
		} else if (parRtcpRwIfTcp != null) {
			BufferView tmpBv = new BufferView(outpPacketPtr);
			parRtcpRwIfTcp.writeRtcpBinary(tmpBv, params.getTpClientDestTcpChann());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendBye_buildEmptyRtcpSr(BufferExt packetSrBuf) {
		RtcpInnerSenderInfoBlock siBlock = new RtcpInnerSenderInfoBlock(0L, 0, 0, 0);
		RtcpPacketSR packetSrObj = new RtcpPacketSR(params.getRtspSsrcId(), siBlock, List.of());
		packetSrObj.copyRawPacketDataInto(packetSrBuf);
	}

	private void sendBye_buildRtcpCompound(BufferExt packetCompoundBuf) {
		/*
		 * We need to send a compound RTCP packet that contains two RTCP packets:
		 *   1. Sender Report (SR) packet
		 *   2. BYE packet
		 * See https://datatracker.ietf.org/doc/html/rfc3550#section-6.1
		 */

		packetCompoundBuf.clear();
		// SR packet
		sendBye_buildEmptyRtcpSr(packetCompoundBuf);
		// BYE packet
		RtcpPacketBYE packetByeObj = new RtcpPacketBYE(List.of(params.getRtspSsrcId()), null);
		BufferExt packetByeBuf = new BufferExt();
		packetByeObj.copyRawPacketDataInto(packetByeBuf);
		// Compound packet
		packetCompoundBuf.append(packetByeBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleReceived() {
		final String FNC_NAME = getClass().getSimpleName() + ".handleReceived()";

		boolean wasDecr = false;
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
				logWarn(FNC_NAME, "have invalid RTCP packet, discarding it (sz=" + tmpPktSz +
						", exp= le " + cacheRecvBuf1.getUsed() + ")");
				logWarn(FNC_NAME, "Discarded packet: 0x" + cacheRecvBuf1.toHexString());
				logWarn(FNC_NAME, "Discarded packet: " + rtcpPktHd);
				cacheRecvBuf1.clear();
				break;
			}

			boolean tmpWasDecr = false;
			if (! wasDecr && params.getCryptoIsRtxpEncryptionEnabled() && srtcpCtxInbound != null) {
				try {
					srtcpCtxInbound.unprotectSrtcpCompound(cacheRecvBuf1, cacheRecvBuf2);
					cacheRecvBuf3.copyOf(cacheRecvBuf2, 0, tmpPktSz);  // contains the current packet
					cacheRecvBuf1.copyOf(cacheRecvBuf2, tmpPktSz, cacheRecvBuf2.getUsed() - tmpPktSz);
					cacheRecvBuf2.clear();
					//
					wasDecr = true;
					tmpWasDecr = true;
				} catch (SrtxpSecurityException e) {
					logError(FNC_NAME, "SrtxpSecurityException caught: " + e.getMessage());
					cacheRecvBuf1.clear();
					return;
				}
			}

			if (! tmpWasDecr) {
				if (tmpPktSz < cacheRecvBuf1.getUsed()) {
					cacheRecvBuf3.copyOf(cacheRecvBuf1, 0, tmpPktSz);  // contains the current packet
					cacheRecvBuf2.copyOf(cacheRecvBuf1, tmpPktSz, cacheRecvBuf1.getUsed() - tmpPktSz);
					cacheRecvBuf1.copyOf(cacheRecvBuf2);
				} else {
					cacheRecvBuf3.copyOf(cacheRecvBuf1);  // contains the current packet
					cacheRecvBuf1.clear();
				}
			}

			// handle the payload
			//logDebug(FNC_NAME, "Handling RTCP packet: " + rtcpPktHd);
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
					logWarn(FNC_NAME, "have unsupported RTCP packet type: " + rtcpPktHd.getPayloadType());
			}
		}
	}

	private void handleRtcpPacketRR(RtcpPacketHeader rtcpPktHd) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketRR()";

		if (rtcpPktHd.getItemsCount() == 0) {
			logDebug(FNC_NAME, String.format(
					"RTCP packet without items (ss=%d, SSRC=0x%08X)", params.getStreamSourceId(), params.getRtspSsrcId()));
			return;
		}
		// read and validate the packet
		RtcpPacketRR rtcpPktInner = new RtcpPacketRR(rtcpPktHd, cacheRecvBuf3);
		//
		for (int itemNr = 1; itemNr <= rtcpPktHd.getItemsCount(); itemNr++) {
			RtcpInnerRecpReportBlock innerRb = rtcpPktInner.getRecpReportBlock(itemNr).orElseThrow();
			/*logDebug(FNC_NAME, String.format("RTT: %dms, FLost: %.3f%%",
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
		//
		params.getCbNotifyRrPacketReceived().orElseThrow().accept(Instant.now());
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
			logDebug(FNC_NAME, String.format(
					"RTCP packet without items (ss=%d, SSRC=0x%08X)", params.getStreamSourceId(), params.getRtspSsrcId()));
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
		logDebug(FNC_NAME, String.format(
				"received BYE (ss=%d, SSRC=0x%08X)", params.getStreamSourceId(), params.getRtspSsrcId()));
	}

}
