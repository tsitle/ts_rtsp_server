package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.helpers.NtpTimestampHelper;
import org.tsitle.rtsp.packets.rtcp.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.exceptions.RtpFrameDataAcquException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainer;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public abstract class ThreadRtpSenderBase extends ThreadPausableBase {

	/** Interval for sending Sender Reports (in milliseconds) */
	private static final int SEND_SR_INTERVAL_MS = 2000;

	/** Length of UDP packets */
	protected static final int UDP_PACKET_LEN = 1490;

	/** Buffer used to store the RTP/XXX payload */
	protected final BufferExt cacheRtpInnerPayloadBuf = new BufferExt();
	/** Stores the current frame data */
	protected final FrameData cacheFrameData = new FrameData();

	/** Thread parameters */
	protected final ParamsThreadRtpSenderCommon paramsCommon;
	private final DatagramSocket parComRtpSocketUdp;
	/** Interval for sending frames over the wire (>= FRAME_PERIOD_MS * 1000000) */
	private final long sendIntervalNs;
	/** Video or audio frame interval in milliseconds */
	@SuppressWarnings({"unused", "FieldCanBeLocal"})
	private final int avFrameIntervalMs;
	/** RTP Clock Rate */
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private final int rtpClockrate;
	/** RTP ticks per frame */
	protected long rtpTicksPerFrame;
	/** RTP packet type */
	private final RtpPacketType rtpPacketType;

	/** Current RTP 'frame' number for RTP timestamps, either video frames or audio samples (64 bits unsigned) */
	private final AtomicLong rtpTsFrameNr = new AtomicLong(1);
	private short rtpSequNr;
	/** Current RTP 'frame' number for NTP timestamps, either video frames or audio frames (64 bits unsigned) */
	private final AtomicLong ntpTsFrameNr = new AtomicLong(1);
	protected int debugStreamOffset = 0;
	private final BufferExt cacheRtpFullData = new BufferExt();
	private boolean isFirstPktOfFrame = true;

	private final AdaptiveSendIntervalStats asdStats = new AdaptiveSendIntervalStats();
	private final SenderInfoStats siStats = new SenderInfoStats();
	/** State A: send frame; State B: optionally send RTCP SR */
	private boolean isMainLoopStateA = true;

	/**
	 * Constructor.
	 * @param paramsCommon Thread parameters
	 * @param rtpClockrate RTP Clock Rate
	 * @param rtpPacketType RTP packet type
	 */
	protected ThreadRtpSenderBase(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				int rtpClockrate,
				@NonNull RtpPacketType rtpPacketType
			) {
		super(paramsCommon.getLogMsgInterface().orElseThrow());

		paramsCommon.validate();
		if (rtpClockrate < 1 || rtpClockrate > 90000 * 2) {
			throw new IllegalArgumentException("Invalid RTP clock rate: " + rtpClockrate);
		}

		//
 		///
		this.paramsCommon = paramsCommon.clone();
		this.parComRtpSocketUdp = paramsCommon.getRtpSocketUdp().orElseThrow();
		///
		this.sendIntervalNs = (rtpPacketType.isVideo() ?
				((long)(1000.0 / (double)paramsCommon.getAvFramesPerSecond()) * 1_000_000L) :
				(RtspConstants.RTP_SEND_INTERVAL_AUDIO_MS * 1_000_000L)
			);
		if (this.sendIntervalNs == 0L) {  // sanity check
			throw new IllegalStateException("sendIntervalNs is 0");
		}
		this.avFrameIntervalMs = (int)(1000.0 / (double)paramsCommon.getAvFramesPerSecond());
		this.rtpClockrate = rtpClockrate;
		this.rtpTicksPerFrame = -1L;  // needs to be set by child class
		this.rtpSequNr = paramsCommon.getRtpSeqNrT0();
		this.rtpPacketType = rtpPacketType;

		//
		this.siStats.rtpTimestamp = this.paramsCommon.getRtpTimestampT0();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public synchronized long getFramesSent() {
		return rtpTsFrameNr.get();
	}

	public abstract void notifyCongestionLevelChange(int congestionLevel);

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);

		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		//
		beforeRunHook();

		//
		try {
			receiveInitialClientPackets();

			//
			waitForParallelThreadToStart();

			//
			siStats.timeSessionStartWc = Instant.now();
			siStats.timeSessionStartNtpWc = NtpTimestampHelper.instantToNtpTimestamp(siStats.timeSessionStartWc);
			siStats.timeSessionStartMono = System.nanoTime();

			//
			asdStats.sleepCounter = 0;
			asdStats.nextSendTimeNs = siStats.timeSessionStartMono + (sendIntervalNs / 2L);

			//
			while (! (doStop.get() || parComRtpSocketUdp.isClosed())) {
				if (! mainLoop()) {
					break;
				}
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "Interrupted while sleeping");
		} finally {
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void beforeRunHook();

	@Override
	protected void stopThreadHook() {
		parComRtpSocketUdp.close();
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract FrameData cbFrameDataSupplier();

	protected abstract Boolean cbRtpPacketMarkerBitSupplier(int currentOffsetInFramePlusFragmentSize, int framePayloadSize);

	protected abstract RtpPacketPayloadInterface cbRtpPacketPayloadSupplier(FrameFragmentData curFragmentData);

	// -----------------------------------------------------------------------------------------------------------------

	protected short getRtpSequNr() {
		return rtpSequNr;
	}

	protected void incrRtpSequNr() {
		++rtpSequNr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected int getRtpTimestampAsInt() {
		return paramsCommon.getRtpTimestampT0() + (int)((rtpTsFrameNr.get() - 1) * rtpTicksPerFrame);
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void incrRtpAndNtpTsFrameNr() {
		rtpTsFrameNr.incrementAndGet();
		//
		ntpTsFrameNr.incrementAndGet();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Try to receive initial packets sent by the client.<br />
	 * This is usually done for NAT/Firewall port testing.
	 */
	private void receiveInitialClientPackets() {
		DatagramPacket recvDp = new DatagramPacket(new byte[UDP_PACKET_LEN], UDP_PACKET_LEN);
		for (int i = 0; i < 10; ++i) {
			try {
				//noinspection resource
				paramsCommon.getRtpSocketUdp().orElseThrow().receive(recvDp);
			} catch (SocketTimeoutException e) {
				// ignore
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Wait for the parallel thread to start playback. If this is a video thread, then wait for the audio thread or vice versa.
	 * If there is no parallel thread, then playback will start immediately.
	 */
	private void waitForParallelThreadToStart() throws InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".waitForParallelThreadToStart()";

		paramsCommon.getCbNotifyThreadReady().orElseThrow().accept(paramsCommon.getStreamSourceId());

		int timeoutCnt = 0;
		boolean isReady = false;
		while (++timeoutCnt < 10 * 500) {
			if (paramsCommon.getCbThreadMayStartPlayback().orElseThrow().get()) {
				isReady = true;
				break;
			}
			Thread.sleep(Duration.ofNanos(100_000L));
		}
		if (! isReady) {
			throw new RuntimeException(FNC_NAME + ": Timeout waiting for parallel thread to start");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop() throws InterruptedException {
		if (isPaused.get()) {
			Thread.sleep(100);
			return true;
		}

		//
		if (isMainLoopStateA) {
			boolean haveMoreFrames = sendFrame();
			//noinspection RedundantIfStatement
			if (! haveMoreFrames) {
				return false;
			}
		} else {
			if (siStats.lastSenderInfoSent == null ||
					Duration.between(siStats.lastSenderInfoSent, Instant.now()).toMillis() >= SEND_SR_INTERVAL_MS) {
				if (! sendSenderReport()) {
					return false;
				}
			}
			//
			sleepToAdjustFramerate();
			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock = getNtpTimestamp();
			siStats.rtpTimestamp += (int)(rtpTicksPerFrame / 2L);
			//
			isMainLoopStateA = true;
		}

		//
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Sleeps until the specified target time (in nanoseconds since epoch) is reached.
	 * Uses a hybrid approach: Thread.sleep() for coarse waiting, then busy-waiting
	 * for high precision without overshooting.
	 * @param targetTimeNanos The target time in nanoseconds (System.nanoTime() format)
	 * @throws IllegalArgumentException if targetTimeNanos is in the past
	 */
	private void sleepUntilNanos(long targetTimeNanos) {
		long currentTime = System.nanoTime();

		if (targetTimeNanos <= currentTime) {
			logDebug("sleepUntilNanos()",
					"Target time is in the past or current time (" +
						((float)(currentTime - targetTimeNanos) / 1_000.0f) +
						"us, r=" + ntpTsFrameNr.get() +
						", s=" + asdStats.sleepCounter + ")");  // @TODO
			return;
		}

		long remainingNanos = targetTimeNanos - currentTime;

		// Phase 1: Coarse waiting using Thread.sleep() for milliseconds
		// Leave a buffer to avoid overshooting
		long sleepBufferNanos = 2_000_000; // 2ms buffer

		if (remainingNanos > sleepBufferNanos) {
			long sleepMillis = (remainingNanos - sleepBufferNanos) / 1_000_000;
			if (sleepMillis > 0) {
				try {
					Thread.sleep(sleepMillis);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}

		// Phase 2: Medium precision using LockSupport.parkNanos()
		// For microsecond-level precision
		currentTime = System.nanoTime();
		remainingNanos = targetTimeNanos - currentTime;
		long parkBufferNanos = 100_000; // 100µs buffer

		if (remainingNanos > parkBufferNanos) {
			LockSupport.parkNanos(remainingNanos - parkBufferNanos);
		}

		// Phase 3: High precision busy-waiting for the final nanoseconds
		while (System.nanoTime() < targetTimeNanos) {
			// Busy wait - yields CPU but maintains high precision
			Thread.onSpinWait(); // JDK 9+ hint for busy waiting
		}
	}

	private void sleepToAdjustFramerate() {
		sleepUntilNanos(asdStats.nextSendTimeNs - 10_000L);
		// "now" should ideally be equal to nextSendTime

		//
		++asdStats.sleepCounter;
		asdStats.nextSendTimeNs = siStats.timeSessionStartMono +
				((asdStats.sleepCounter / 2L) * sendIntervalNs) + ((asdStats.sleepCounter % 2L) * (sendIntervalNs / 2L));
	}

	private boolean sendFrame() {
		final String FNC_NAME = getClass().getSimpleName() + ".sendFrame()";

		try {
			// acquire the next frame from the video stream
			final FrameData frameData = cbFrameDataSupplier();
			if (frameData.haveErrorEof) {
				logError(FNC_NAME, "haveErrorEof: " + frameData.errorMsg);
				throw new InputStreamEofException();
			}
			if (frameData.haveErrorOther) {
				throw new RtpFrameDataAcquException(frameData.errorMsg);
			}

			// only sleep if this is the first packet of the frame/AU
			if (isFirstPktOfFrame) {
				/*
				 * Note: the frame counter has already been incremented in cbFrameDataSupplier()
				 */
				//
				sleepToAdjustFramerate();
				// update SenderInfo NTP and RTP timestamp
				siStats.timestampNtpWallclock = getNtpTimestamp();
				siStats.rtpTimestamp = frameData.rtpFrameTimestamp;
				//
				isFirstPktOfFrame = false;
			}

			//
			debugStreamOffset += frameData.totalFrameSize;

			//
			int sentTotalPktSize = 0;
			boolean isLastPktOfFrame = false;
			while (! doStop.get() && sentTotalPktSize < frameData.rtpPayloadData.getUsed()) {
				final int curPktSize = Math.min(UDP_PACKET_LEN, frameData.rtpPayloadData.getUsed() - sentTotalPktSize);
				isLastPktOfFrame = cbRtpPacketMarkerBitSupplier(
						sentTotalPktSize + curPktSize,
						frameData.rtpPayloadData.getUsed()
					);

				boolean tmpResB = sendSinglePacket(
						sentTotalPktSize,
						curPktSize,
						frameData,
						isLastPktOfFrame
					);
				if (! tmpResB) {
					return false;
				}

				sentTotalPktSize += curPktSize;
				++siStats.rtpPacketsSent;
				siStats.rtpPayloadBytesSent += curPktSize;

				// update sequence number
				incrRtpSequNr();
			}

			//
			if (isLastPktOfFrame || rtpPacketType.isAudio()) {
				isFirstPktOfFrame = true;
				isMainLoopStateA = false;
			}
		} catch (InputStreamEofException ex) {
			logError(FNC_NAME, "InputStreamEofException caught: " + ex);
			return false;
		} catch (UdpSocketIoException ex) {
			logError(FNC_NAME, ex.toString());
			return false;
		} catch (RtpFrameDataAcquException ex) {
			logError(FNC_NAME, "RtpFrameDataAcquException caught: " + ex.getMessage());
			return false;
		}
		return true;
	}

	private boolean sendSinglePacket(
				int sentTotalPktSize,
				int curPktSize,
				FrameData frameData,
				boolean isLastPktOfFrame
			) throws UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendSinglePacket()";

		final boolean isLastPktOfPayload = (sentTotalPktSize + curPktSize == frameData.rtpPayloadData.getUsed());

		FrameFragmentData curFragmentData = new FrameFragmentData(
				frameData,
				sentTotalPktSize,
				curPktSize,
				isLastPktOfPayload
			);
		RtpPacketPayloadInterface curInnerPayload = cbRtpPacketPayloadSupplier(curFragmentData);
		RtpPacketContainer curPacketContainer = new RtpPacketContainer(
				paramsCommon.getRtspSsrcId(),
				getRtpSequNr(),
				isLastPktOfFrame,
				frameData.rtpFrameTimestamp,
				curInnerPayload
			);

		// retrieve the packet bitstream and store it in an array of bytes
		curPacketContainer.copyRawPacketDataInto(cacheRtpFullData);

		if (parComRtpSocketUdp.isClosed()) {
			if (! doStop.get()) {
				logError(FNC_NAME, "socket is closed");
			}
			return false;
		}
		// send the packet as a DatagramPacket over the UDP socket
		DatagramPacket sendDp = new DatagramPacket(
				cacheRtpFullData.getBuf(),
				cacheRtpFullData.getUsed(),
				paramsCommon.getClientIpAddr().orElseThrow(),
				paramsCommon.getClientDestPortRtp()
			);
		try {
			parComRtpSocketUdp.send(sendDp);
		} catch (IOException ex) {
			if (doStop.get()) {
				return false;
			}
			throw new UdpSocketIoException(FNC_NAME + ": send() failed: " + ex.getMessage());
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private long getNtpTimestamp() {
		long deltaMono = (System.nanoTime() - siStats.timeSessionStartMono);
		return NtpTimestampHelper.addNanosToNtpTimestamp(siStats.timeSessionStartNtpWc, deltaMono);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendSenderReport_buildRtcpSr(BufferExt packetSrBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".sendSenderReport_buildRtcpSr()";

		if (siStats.timestampNtpWallclock == null) {
			throw new IllegalStateException(FNC_NAME + ": timestampNtpWallclock is null");
		}

		RtcpInnerSenderInfoBlock siBlock = new RtcpInnerSenderInfoBlock(
				siStats.timestampNtpWallclock,
				siStats.rtpTimestamp,
				siStats.rtpPacketsSent,
				siStats.rtpPayloadBytesSent
			);
		RtcpPacketSR packetSrObj = new RtcpPacketSR(
				paramsCommon.getRtspSsrcId(),
				siBlock,
				List.of()
			);
		packetSrObj.copyRawPacketDataInto(packetSrBuf);
	}

	private void sendSenderReport_buildRtcpCompound(BufferExt packetCompoundBuf) {
		/*
		 * We need to send a compound RTCP packet that contains two RTCP packets:
		 *   1. Sender Report (SR) packet
		 *   2. Source Description (SDES) packet
		 * See https://datatracker.ietf.org/doc/html/rfc3550#section-6.1
		 */

		packetCompoundBuf.clear();
		// SR packet
		sendSenderReport_buildRtcpSr(packetCompoundBuf);
		// SDES packet
		RtcpPacketSDES packetSdesObj = new RtcpPacketSDES(List.of(paramsCommon.getXsrcBlockEntry().orElseThrow()));
		BufferExt packetSdesBuf = new BufferExt();
		packetSdesObj.copyRawPacketDataInto(packetSdesBuf);
		// Compound packet
		packetCompoundBuf.append(packetSdesBuf);
	}

	private boolean sendSenderReport() {
		// Compound packet
		BufferExt packetCompoundBuf = new BufferExt();
		sendSenderReport_buildRtcpCompound(packetCompoundBuf);

		//
		paramsCommon.getCbRtcpAppendToOutgoingQueque().orElseThrow().accept(paramsCommon.getRtspSsrcId(), packetCompoundBuf);

		siStats.lastSenderInfoSent = Instant.now();
		return true;
	}

}
