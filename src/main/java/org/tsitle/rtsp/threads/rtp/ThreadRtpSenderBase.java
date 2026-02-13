package org.tsitle.rtsp.threads.rtp;

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
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
	private final long rtpTicksPerFrame;

	/** Current RTP 'frame' number for RTP timestamps, either video frames or audio samples (64 bits unsigned) */
	private final AtomicLong rtpTsFrameNr = new AtomicLong(1);
	private short rtpSequNr;
	/** Current RTP 'frame' number for NTP timestamps, either video frames or audio frames (64 bits unsigned) */
	private final AtomicLong ntpTsFrameNr = new AtomicLong(1);
	protected int debugStreamOffset = 0;
	private final BufferExt cacheRtpFullData = new BufferExt();

	private final AdaptiveSendIntervalStats asdStats = new AdaptiveSendIntervalStats();
	private final SenderInfoStats siStats = new SenderInfoStats();
	/** State A: send frame; State B: optionally send RTCP SR */
	private boolean isMainLoopStateA = false;

	/**
	 * Constructor.
	 * @param paramsCommon Thread parameters
	 * @param rtpClockrate RTP Clock Rate
	 * @param rtpTicksPerFrame RTP ticks per frame
	 * @param rtpPacketType RTP packet type
	 */
	protected ThreadRtpSenderBase(
				ParamsThreadRtpSenderCommon paramsCommon,
				int rtpClockrate,
				long rtpTicksPerFrame,
				RtpPacketType rtpPacketType
			) {
		if (paramsCommon == null) {
			throw new IllegalArgumentException("Thread parameters cannot be null");
		}
		paramsCommon.validate();
		if (rtpClockrate < 1 || rtpClockrate > 90000 * 2) {
			throw new IllegalArgumentException("Invalid RTP clock rate: " + rtpClockrate);
		}
		if (rtpTicksPerFrame <= 0) {
			throw new IllegalArgumentException("Invalid RTP ticks per frame: " + rtpTicksPerFrame);
		}

		//
 		///
		this.paramsCommon = paramsCommon.clone();
		this.parComRtpSocketUdp = paramsCommon.getRtpSocketUdp().orElseThrow();
		///
		this.sendIntervalNs = (rtpPacketType.isVideo() ?
				((long)(1000.0f / paramsCommon.getAvFramesPerSecond()) * 1_000_000L) :
				(RtspConstants.RTP_SEND_INTERVAL_AUDIO_MS * 1_000_000L)
			);
		if (this.sendIntervalNs == 0L) {  // sanity check
			throw new IllegalStateException("sendIntervalNs is 0");
		}
		this.avFrameIntervalMs = (int)(1000.0f / paramsCommon.getAvFramesPerSecond());
		this.rtpClockrate = rtpClockrate;
		this.rtpTicksPerFrame = rtpTicksPerFrame;
		this.rtpSequNr = paramsCommon.getRtpSeqNrT0();

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

		try {
			receiveInitialClientPackets();

			//
			waitForParallelThreadToStart();

			//
			siStats.timeSessionStartWc = Instant.now();
			siStats.timeSessionStartNtpWc = NtpTimestampHelper.instantToNtpTimestamp(siStats.timeSessionStartWc);
			siStats.timeSessionStartMono = System.nanoTime();

			//
			asdStats.nextSendTime = siStats.timeSessionStartWc.plusNanos(sendIntervalNs / 2L);

			//
			while (! (doStop.get() || parComRtpSocketUdp.isClosed())) {
				if (! mainLoop()) {
					break;
				}
			}
		} catch (InterruptedException e) {
			System.err.println(FNC_NAME + ": Interrupted while sleeping");
		} finally {
			isRunning.set(false);
			System.out.println(FNC_NAME + ": <" + paramsCommon.getDebugSessionId().orElseThrow() +
					"|sid=" + paramsCommon.getStreamSourceId() + "> Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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

	protected void incrRtpAndNtpTsFrameNr(int delta) {
		rtpTsFrameNr.addAndGet(delta);
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
		//final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (isPaused.get()) {
			Thread.sleep(100);
			return true;
		}

		//
		if (isMainLoopStateA) {
			boolean haveMoreFrames = sendFrame();
			if (! haveMoreFrames) {
				return false;
			}
		} else {
			if (! sleepToAdjustFramerate()) {
				return false;
			}
			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock = getNtpTimestamp();
			siStats.rtpTimestamp += (int)(rtpTicksPerFrame / 2L);
		}

		//
		boolean resB = true;
		if (! isMainLoopStateA &&
				(siStats.lastSenderInfoSent == null ||
						Duration.between(siStats.lastSenderInfoSent, Instant.now()).toMillis() >= SEND_SR_INTERVAL_MS)) {
			resB = sendSenderReport();
		}

		//
		isMainLoopStateA = (! isMainLoopStateA);
		return resB;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean sleepToAdjustFramerate() throws InterruptedException {
		Duration tmpDur = Duration.between(Instant.now(), asdStats.nextSendTime);
		if (tmpDur.isPositive()) {
			Thread.sleep(tmpDur);
		}
		// "now" should ideally be equal to nextSendTime

		//
		asdStats.nextSendTime = asdStats.nextSendTime.plusNanos(sendIntervalNs / 2L);
		return true;
	}

	private boolean sendFrame() throws InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendFrame()";

		try {
			// acquire the next frame from the video stream
			final FrameData frameData = cbFrameDataSupplier();
			if (frameData.haveErrorEof) {
				System.err.println(FNC_NAME + ": haveErrorEof: " + frameData.errorMsg);
				throw new InputStreamEofException();
			}
			if (frameData.haveErrorOther) {
				throw new RtpFrameDataAcquException(frameData.errorMsg);
			}

			//
			if (! sleepToAdjustFramerate()) {
				return false;
			}

			//
			debugStreamOffset += frameData.totalFrameSize;

			//
			int sentTotalPktSize = 0;
			while (! doStop.get() && sentTotalPktSize < frameData.rtpPayloadData.getUsed()) {
				final int curPktSize = Math.min(UDP_PACKET_LEN, frameData.rtpPayloadData.getUsed() - sentTotalPktSize);
				final boolean isLastPktOfPayload = (sentTotalPktSize + curPktSize == frameData.rtpPayloadData.getUsed());
				final boolean isLastPktOfFrame = cbRtpPacketMarkerBitSupplier(
						sentTotalPktSize + curPktSize,
						frameData.rtpPayloadData.getUsed()
					);

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
						System.err.println(FNC_NAME + ": socket is closed");
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
					throw new UdpSocketIoException("send failed: " + ex.getMessage());
				}

				sentTotalPktSize += curPktSize;
				++siStats.rtpPacketsSent;
				siStats.rtpPayloadBytesSent += curPktSize;

				// update sequence number
				incrRtpSequNr();
			}

			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock = getNtpTimestamp();
			siStats.rtpTimestamp = frameData.rtpFrameTimestamp;
		} catch (InputStreamEofException ex) {
			System.err.println(FNC_NAME + ": InputStreamEofException caught: " + ex);
			return false;
		} catch (UdpSocketIoException ex) {
			System.err.println(FNC_NAME + ": " + ex);
			return false;
		} catch (RtpFrameDataAcquException ex) {
			System.err.println(FNC_NAME + ": FrameDataAcquException caught: " + ex.getMessage());
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private long getNtpTimestamp() {
		long deltaMono = (System.nanoTime() - siStats.timeSessionStartMono);
		return NtpTimestampHelper.addNanosToNtpTimestamp(siStats.timeSessionStartNtpWc, deltaMono);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendSenderReport_buildCompoundRtcp(BufferExt packetCompoundBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".sendSenderReport_buildCompoundRtcp()";

		if (siStats.timestampNtpWallclock == null) {
			throw new IllegalStateException(FNC_NAME + ": timestampNtpWallclock is null");
		}

		/*
		 * We need to send a compound RTCP packet that contains two RTCP packets:
		 *   1. Sender Report (SR) packet
		 *   2. Source Description (SDES) packet
		 * See https://datatracker.ietf.org/doc/html/rfc3550#section-6.1
		 */

		// SR packet
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
		BufferExt packetSrBuf = new BufferExt();
		packetSrObj.copyRawPacketDataInto(packetSrBuf);
		// SDES packet
		RtcpPacketSDES packetSdesObj = new RtcpPacketSDES(List.of(paramsCommon.getXsrcBlockEntry().orElseThrow()));
		BufferExt packetSdesBuf = new BufferExt();
		packetSdesObj.copyRawPacketDataInto(packetSdesBuf);
		// Compound packet
		packetCompoundBuf.clear();
		packetCompoundBuf.append(packetSrBuf);
		packetCompoundBuf.append(packetSdesBuf);
	}

	private boolean sendSenderReport() {
		// Compound packet
		BufferExt packetCompoundBuf = new BufferExt();
		sendSenderReport_buildCompoundRtcp(packetCompoundBuf);

		//
		paramsCommon.getCbRtcpAppendToOutgoingQueque().orElseThrow().accept(paramsCommon.getRtspSsrcId(), packetCompoundBuf);

		siStats.lastSenderInfoSent = Instant.now();
		return true;
	}

}
