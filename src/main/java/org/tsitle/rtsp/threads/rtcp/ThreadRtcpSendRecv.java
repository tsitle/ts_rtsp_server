package org.tsitle.rtsp.threads.rtcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidMkiException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.common.helpers.NtpTimestamp;
import org.tsitle.lib_xrtxp.packets.rtcp.*;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextInbound;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextOutbound;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtcp;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadRtcpSendRecv extends ThreadPausableBase {

	private final @NonNull ParamsThreadRtcp params;
	private final @Nullable DatagramSocket parRtcpSocketUdp;
	private final @Nullable RtxpTcpReadWrite parRtcpRwIfTcp;

	private final AtomicInteger targetCongestionLevel = new AtomicInteger(0);
	private final @NonNull DatagramPacket cacheDpRecv;
	private final BufferExt cacheRecvBuf1 = new BufferExt();
	private final BufferExt cacheRecvBuf2 = new BufferExt();
	private final BufferExt cacheRecvBuf3 = new BufferExt();
	@SuppressWarnings("FieldCanBeLocal")
	private @Nullable Instant lastRtcpPacketReceived = null;

	private final Queue<@NonNull BufferExt> queueSend = new ConcurrentLinkedQueue<>();

	private final SrtcpVarsInbound srtcpVarsInbound = new SrtcpVarsInbound();
	private final SrtcpVarsOutbound srtcpVarsOutbound = new SrtcpVarsOutbound();

	private final AtomicLong packetCntInbound = new AtomicLong(0L);
	private final AtomicLong packetCntOutbound = new AtomicLong(0L);

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
				if (params.getCryptoKmdInbound().isPresent() &&
						params.getCryptoKmdInbound().orElseThrow().encrKeyLen() > 0) {
					this.srtcpVarsInbound.ctxObjCur = new SrtcpContextInbound(params.getCryptoKmdInbound().orElseThrow());
				} else {
					// we cannot decrypt incoming RTCP packets
					this.srtcpVarsInbound.ctxObjCur = null;
					logWarn(getClass().getSimpleName() + ".ctor()",
							"missing KmdInbound, cannot decrypt SRTCP packets");
				}
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): KmdInbound: " +
						"SrtxpSecurityException caught: " + e.getMessage());
			}
			try {
				this.srtcpVarsOutbound.ctxObj = new SrtcpContextOutbound(params.getCryptoKmdOutbound().orElseThrow());
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): KmdOutbound: " +
						"SrtxpSecurityException caught: " + e.getMessage());
			}
		} else {
			this.srtcpVarsInbound.ctxObjCur = null;
			this.srtcpVarsOutbound.ctxObj = null;
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

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void appendToSendQueue(@NonNull BufferExt rtcpPacketsBuf) {
		BufferExt tmpBuf = new BufferExt();
		tmpBuf.copyOf(rtcpPacketsBuf);
		queueSend.add(tmpBuf);
	}

	public synchronized void appendByePacketToSendQueue() {
		final String FNC_NAME = getClass().getSimpleName() + ".appendByePacketToSendQueue()";

		logDebug(FNC_NAME, String.format("Sending BYE packet (ss=%s, SSRC=%s)",
				params.getIdStreamSource().getIdStr(), params.getSsrcId().toHexString(true)));
		BufferExt packetCompoundBuf = new BufferExt();
		sendBye_buildRtcpCompound(packetCompoundBuf);
		//
		queueSend.add(packetCompoundBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public long getPacketCountInbound() {
		return packetCntInbound.get();
	}

	public void setNextSrtcpKmdInbound(@NonNull SrtxpKmd kmd) {
		final String FNC_NAME = getClass().getSimpleName() + ".setNextSrtcpKmdInbound()";

		srtcpVarsInbound.ctxWriteLock.lock();
		try {
			if (srtcpVarsInbound.ctxObjCur == null) {
				return;  // if we didn't have a KMD up until now, we don't need to set a new one
			}
			logDebug(FNC_NAME, "Setting next SRTCP inbound KMD (ss=" + params.getIdStreamSource().getIdStr() +
					", MKI=" + Long.toUnsignedString(kmd.mki().value()) + ")");
			try {
				srtcpVarsInbound.ctxUpdatePending.set(true);
				srtcpVarsInbound.ctxObjNext = new SrtcpContextInbound(kmd);
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException("SrtxpSecurityException caught: " + e.getMessage());
			}
		} finally {
			srtcpVarsInbound.ctxWriteLock.unlock();
		}
	}

	public boolean hasSrtcpInboundRekeyingBeenCompleted() {
		return (! srtcpVarsInbound.ctxUpdatePending.get());
	}

	public long getPacketCountOutbound() {
		return packetCntOutbound.get();
	}

	public void setNextSrtcpKmdOutbound(@NonNull SrtxpKmd kmd) {
		final String FNC_NAME = getClass().getSimpleName() + ".setNextSrtcpKmdOutbound()";

		srtcpVarsOutbound.ctxWriteLock.lock();
		try {
			if (srtcpVarsOutbound.ctxObj == null) {
				return;  // if we didn't have a KMD up until now, we don't need to set a new one
			}
			logDebug(FNC_NAME, "Setting next SRTCP outbound KMD (ss=" + params.getIdStreamSource().getIdStr() +
					", MKI=" + Long.toUnsignedString(kmd.mki().value()) + ")");
			try {
				srtcpVarsOutbound.ctxUpdatePending.set(true);
				srtcpVarsOutbound.ctxObj = new SrtcpContextOutbound(kmd);
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException("SrtxpSecurityException caught: " + e.getMessage());
			}
		} finally {
			srtcpVarsOutbound.ctxWriteLock.unlock();
		}
	}

	public boolean hasSrtcpOutboundRekeyingBeenCompleted() {
		return (! srtcpVarsOutbound.ctxUpdatePending.get());
	}

	// -----------------------------------------------------------------------------------------------------------------

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
			// send outstanding packets
			if (! queueSend.isEmpty()) {
				logDebug(FNC_NAME, "Sending outstanding RTCP packets");
				while (! queueSend.isEmpty()) {
					try {
						sendFromQueue();
					} catch (Exception ignored) {
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
		packetCntInbound.incrementAndGet();

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
		if (params.getCryptoIsRtxpEncryptionEnabled()) {
			srtcpVarsOutbound.ctxReadLock.lock();
			try {
				if (srtcpVarsOutbound.ctxObj != null) {
					if (! srtcpVarsOutbound.ctxObj.getSsrcId().equals(params.getSsrcId())) {
						throw new SrtxpSecurityException(FNC_NAME + ": SSRC mismatch");
					}
					srtcpVarsOutbound.ctxObj.protectRtcpSrCompound(
							plainPktBuf,
							params.getSsrcId(),
							encrPktBuf
						);
					outpPacketPtr = encrPktBuf;
					if (srtcpVarsOutbound.ctxUpdatePending.get()) {
						srtcpVarsOutbound.ctxUpdatePending.set(false);
						packetCntOutbound.set(0L);
					}
				}
			} catch (SrtxpSecurityException e) {
				logError(FNC_NAME, "SrtxpSecurityException caught: " + e.getMessage());
				return;
			} finally {
				srtcpVarsOutbound.ctxReadLock.unlock();
			}
		}

		// send the compound packet as a DatagramPacket over the UDP socket
		if (parRtcpSocketUdp != null) {
			DatagramPacket sendDp = new DatagramPacket(
					outpPacketPtr.getBaPtr(),
					outpPacketPtr.getUsed(),
					params.getTpClientIpAddr().getIpAddrObj().orElseThrow(),
					params.getTpClientDestUdpPort().getPort16bit().orElseThrow()
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
		//logDebug(FNC_NAME, "sent RTCP packet");

		//
		packetCntOutbound.incrementAndGet();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendBye_buildEmptyRtcpSr(@NonNull BufferExt packetSrBuf) {
		RtcpInnerSenderInfoBlock siBlock = new RtcpInnerSenderInfoBlock(
				NtpTimestamp.ofNow(),
				RtspProtoRtpTimestamp.ofZero(),
				0,
				0
			);
		RtcpPacketSR packetSrObj = new RtcpPacketSR(params.getSsrcId(), siBlock, List.of());
		packetSrObj.copyRawPacketDataInto(packetSrBuf);
	}

	private void sendBye_buildRtcpCompound(@NonNull BufferExt packetCompoundBuf) {
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
		RtcpPacketBYE packetByeObj = new RtcpPacketBYE(List.of(params.getSsrcId()), null);
		BufferExt packetByeBuf = new BufferExt();
		packetByeObj.copyRawPacketDataInto(packetByeBuf);
		// Compound packet
		packetCompoundBuf.append(packetByeBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleReceived() {
		final String FNC_NAME = getClass().getSimpleName() + ".handleReceived()";

		List<@NonNull BufferExt> outputRawPackets = new ArrayList<>();
		unpackReceivedCompound(outputRawPackets);

		for (BufferExt rawPktBe : outputRawPackets) {
			final RtcpPacketHeader rtcpPktHd = new RtcpPacketHeader(rawPktBe);

			// handle the payload
			//logDebug(FNC_NAME, "Handling RTCP packet: " + rtcpPktHd);
			switch (rtcpPktHd.getPayloadType()) {
				case RR: handleRtcpPacketRR(rtcpPktHd, rawPktBe); break;
				case SR: handleRtcpPacketSR(rtcpPktHd, rawPktBe); break;
				case SDES: handleRtcpPacketSDES(rtcpPktHd, rawPktBe); break;
				case BYE: handleRtcpPacketBYE(rtcpPktHd, rawPktBe); break;
				default: logWarn(FNC_NAME, "have unsupported RTCP packet type: " + rtcpPktHd.getPayloadType());
			}
		}
	}

	private void unpackReceivedCompound(@NonNull List<@NonNull BufferExt> outputRawPackets) {
		final String FNC_NAME = getClass().getSimpleName() + ".unpackReceivedCompound()";

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
			if (! wasDecr && params.getCryptoIsRtxpEncryptionEnabled()) {
				srtcpVarsInbound.ctxReadLock.lock();
				try {
					if (srtcpVarsInbound.ctxObjCur != null) {
						decryptPacket(tmpPktSz);
						//
						wasDecr = true;
						tmpWasDecr = true;
						//
						if (srtcpVarsInbound.ctxUpdatePending.get()) {
							srtcpVarsInbound.ctxUpdatePending.set(false);
							packetCntInbound.set(0L);
						}
					}
				} catch (SrtxpSecurityException e) {
					logError(FNC_NAME, "SrtxpSecurityException caught: " + e.getMessage());
					cacheRecvBuf1.clear();
					return;
				} finally {
					srtcpVarsInbound.ctxReadLock.unlock();
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

			// add payload to output
			outputRawPackets.add(cacheRecvBuf3.clone());
			cacheRecvBuf3.clear();
		}
	}

	private void decryptPacket(int pktSz) throws SrtxpSecurityException {
		/*
		 * Note that the ReadLock is already locked at this point
		 */
		if (srtcpVarsInbound.ctxObjCur == null) {
			return;  // just for the linter
		}
		try {
			srtcpVarsInbound.ctxObjCur.unprotectSrtcpCompound(cacheRecvBuf1, cacheRecvBuf2);
		} catch (SrtxpInvalidMkiException e1) {
			if (srtcpVarsInbound.ctxObjNext == null) {
				throw new SrtxpSecurityException(e1.getMessage());
			}
			// replace the current KMD
			srtcpVarsInbound.ctxReadLock.unlock();
			srtcpVarsInbound.ctxWriteLock.lock();
			try {
				srtcpVarsInbound.ctxObjCur = srtcpVarsInbound.ctxObjNext;
				srtcpVarsInbound.ctxObjNext = null;
			} finally {
				srtcpVarsInbound.ctxWriteLock.unlock();
			}
			srtcpVarsInbound.ctxReadLock.lock();
			// try again
			srtcpVarsInbound.ctxObjCur.unprotectSrtcpCompound(cacheRecvBuf1, cacheRecvBuf2);
		}

		cacheRecvBuf3.copyOf(cacheRecvBuf2, 0, pktSz);  // contains the current decrypted packet
		cacheRecvBuf1.copyOf(cacheRecvBuf2, pktSz, cacheRecvBuf2.getUsed() - pktSz);  // copy remaining buffer to Buf1
		cacheRecvBuf2.clear();
	}

	private void handleRtcpPacketRR(@NonNull RtcpPacketHeader rtcpPktHd, @NonNull BufferExt rawPktBe) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketRR()";

		if (rtcpPktHd.getItemsCount() == 0) {
			logDebug(FNC_NAME, String.format("RTCP packet without items (ss=%s, SSRC=%s)",
					params.getIdStreamSource().getIdStr(), params.getSsrcId().toHexString(true)));
			return;
		}
		// read and validate the packet
		RtcpPacketRR rtcpPktInner = new RtcpPacketRR(rtcpPktHd, rawPktBe);
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

	private void handleRtcpPacketSR(@NonNull RtcpPacketHeader rtcpPktHd, @NonNull BufferExt rawPktBe) {
		// read and validate the packet
		RtcpPacketSR rtcpPktInner = new RtcpPacketSR(rtcpPktHd, rawPktBe);
		if (rtcpPktInner.getRawPacketSize() == 0) {
			throw new IllegalStateException();  // only for the linter
		}
	}

	private void handleRtcpPacketSDES(@NonNull RtcpPacketHeader rtcpPktHd, @NonNull BufferExt rawPktBe) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketSDES()";

		if (rtcpPktHd.getItemsCount() == 0) {
			logDebug(FNC_NAME, String.format("RTCP packet without items (ss=%s, SSRC=%s)",
					params.getIdStreamSource().getIdStr(), params.getSsrcId().toHexString(true)));
			return;
		}
		// read and validate the packet
		RtcpPacketSDES rtcpPktInner = new RtcpPacketSDES(rtcpPktHd, rawPktBe);
		if (rtcpPktInner.getItemsCount() > 1000) {
			throw new IllegalStateException();  // only for the linter
		}
	}

	private void handleRtcpPacketBYE(@NonNull RtcpPacketHeader rtcpPktHd, @NonNull BufferExt rawPktBe) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleRtcpPacketBYE()";

		// read and validate the packet
		RtcpPacketBYE rtcpPktInner = new RtcpPacketBYE(rtcpPktHd, rawPktBe);
		if (rtcpPktInner.getRawPacketSize() == 0) {
			return;  // only for the linter
		}
		logDebug(FNC_NAME, String.format("received BYE (ss=%s, SSRC=%s)",
				params.getIdStreamSource().getIdStr(), params.getSsrcId().toHexString(true)));
	}

}
