package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFactory;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.NtpTimestamp;
import org.tsitle.rtsp.packets.rtcp.*;
import org.tsitle.rtsp.packets.rtp.*;
import org.tsitle.rtsp.security.SrtpContextOutbound;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.rtsp.proto.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpTimestamp;
import org.tsitle.rtsp.helpers.TimestampEpochNs;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ThreadRtpSenderBase<
			I extends CodecInfoInterface<I>,
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>,
			TDP extends ThreadDataProvBase<I, AVSTROG>
		> extends ThreadPausableBase {

	/** Interval for sending Sender Reports (in milliseconds) */
	private static final int SEND_SR_INTERVAL_MS = 500;

	/** Length of UDP packets */
	protected static final int UDP_PACKET_LEN = 1000 + RtpPacketContainerBase.RTP_CONT_HEADER_SIZE + 4 + (128 * 2);

	protected final Class<AVSTRIC> avStreamIncomingType;
	protected @Nullable AVSTRIC avStreamIncomingObj;
	protected @Nullable TDP threadDataProv;
	protected final Class<AVSTROG> avStreamOutgoingType;

	/** Buffer view for reading the RTP/XXX payload */
	protected @Nullable BufferView cacheRtpInnerPayloadBufView = null;
	/** Stores the current frame data */
	protected final FrameData cacheFrameData = new FrameData();
	protected final ParamsContainerBase cacheParamsBase = new ParamsContainerBase();

	/** Thread parameters */
	protected final ParamsThreadRtpSenderCommon paramsCommon;
	private final @Nullable DatagramSocket parComRtpSocketUdp;
	private final @Nullable RtxpTcpReadWrite parComRtpRwIfTcp;
	/** RTP Clock Rate */
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private final int rtpClockrate;
	/** RTP ticks per frame */
	protected long rtpTicksPerFrame;
	/** RTP packet type */
	protected final RtpPacketType rtpPacketType;
	/** Adjusted RTP timestamp T0 */
	private final @NonNull RtspProtoRtpTimestamp rtpTsT0Adj = RtspProtoRtpTimestamp.ofZero();
	/** System.nanoTime when the RTP timestamp T0 was adjusted (in nanoseconds) */
	@SuppressWarnings("FieldCanBeLocal")
	private final @NonNull TimestampEpochNs rtpTsT0GenAdj = TimestampEpochNs.ofEmpty();
	/** Current RTP timestamp */
	private final @NonNull RtspProtoRtpTimestamp rtpTsCurrent = RtspProtoRtpTimestamp.ofZero();

	/** Current RTP 'frame' number for RTP timestamps, either video frames or audio samples (64 bits unsigned) */
	private final AtomicLong rtpTsFrameNr = new AtomicLong(-1);
	private final @NonNull RtspProtoRtpSeqNr rtpSequNr = new RtspProtoRtpSeqNr();
	protected int debugStreamOffset = 0;
	private boolean isFirstPktOfFrame = true;

	private final AdaptiveScheduler adaptiveScheduler;
	private final TimeNtpTsInfo timeNtpTsInfo = new TimeNtpTsInfo();
	private final SenderInfoStats siStats = new SenderInfoStats();
	/** State A: send frame; State B: optionally send RTCP SR */
	private boolean isMainLoopStateA = true;

	private int udpMaxPacketLenDelta;
	private long largestFrame = 0L;

	private final SrtpVarsOutbound srtpVarsOutbound = new SrtpVarsOutbound();

	private final AtomicLong packetCntOutbound = new AtomicLong(0L);

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Thread parameters
	 * @param rtpClockrate RTP Clock Rate
	 * @param rtpPacketType RTP packet type
	 */
	protected ThreadRtpSenderBase(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				int rtpClockrate,
				@NonNull RtpPacketType rtpPacketType
			) {
		super(paramsCommon.getLogMsgInterface().orElseThrow());

		// sanity check
		if (UDP_PACKET_LEN > 1400) {
			throw new AssertionError("UDP_PACKET_LEN is too large: " + UDP_PACKET_LEN);
		}

		//
		paramsCommon.validate();
		if (rtpClockrate < 1 || rtpClockrate > 90000 * 2) {
			throw new IllegalArgumentException("Invalid RTP clock rate: " + rtpClockrate);
		}

		//
		this.avStreamIncomingType = avStreamIncomingType;
		this.avStreamOutgoingType = avStreamOutgoingType;
		//
		this.paramsCommon = paramsCommon.clone();
		this.parComRtpSocketUdp = paramsCommon.getTpSocketUdp().orElse(null);
		this.parComRtpRwIfTcp = paramsCommon.getTpClientDestTcpIf().orElse(null);
		//
		final double sendIntervalNs = (1_000_000_000.0 / paramsCommon.getAvFramesPerSecond());
		if (sendIntervalNs < 1_000_000.0) {  // sanity check
			throw new IllegalStateException("sendInterval is < 1ms");
		}
		this.rtpClockrate = rtpClockrate;
		this.rtpTicksPerFrame = -1L;  // needs to be set by child class
		this.rtpSequNr.copyFrom(paramsCommon.getRtpSeqNrT0().orElseThrow());
		this.rtpPacketType = rtpPacketType;

		//
		this.adaptiveScheduler = new AdaptiveScheduler(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsCommon.getAvFramesPerSecond()
			);

		//
		if (paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			try {
				this.srtpVarsOutbound.ctxObj = new SrtpContextOutbound(paramsCommon.getCryptoKmdOutbound().orElseThrow());
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
						"SrtxpSecurityException caught: " + e.getMessage());
			}
		} else {
			this.srtpVarsOutbound.ctxObj = null;
		}

		//
		udpMaxPacketLenDelta = RtpPacketContainerBase.RTP_CONT_HEADER_SIZE + 4;
		if (rtpPacketType == RtpPacketType.V_JPEG) {
			// RTP/JPEG header can be rather big
			udpMaxPacketLenDelta += RtpPacketMjpeg.INNER_HEADER_MAIN_SIZE +
					RtpPacketMjpeg.INNER_HEADER_QT_PRE_SIZE + 128 * 2;
		} else if (rtpPacketType == RtpPacketType.V_H264 || rtpPacketType == RtpPacketType.V_H265) {
			udpMaxPacketLenDelta += RtpPacketH264.INNER_HEADER_SIZE_MAX;
		}
		if (paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			if (this.srtpVarsOutbound.ctxObj == null) {
				throw new IllegalStateException("srtpCtxOutbound == null");
			}
			udpMaxPacketLenDelta += this.srtpVarsOutbound.ctxObj.getSrtpExtraPacketLength();
		}
		if (UDP_PACKET_LEN - udpMaxPacketLenDelta < 128) {
			throw new AssertionError("UDP packet length too small");
		}
		while ((UDP_PACKET_LEN - udpMaxPacketLenDelta) % 4 != 0) {
			++udpMaxPacketLenDelta;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract void notifyCongestionLevelChange(int congestionLevel);

	// -----------------------------------------------------------------------------------------------------------------

	public long getPacketCountOutbound() {
		return packetCntOutbound.get();
	}

	public void setNextSrtpKmdOutbound(@NonNull SrtxpKmd kmd) {
		final String FNC_NAME = getClass().getSimpleName() + ".setNextSrtpKmdOutbound()";

		srtpVarsOutbound.ctxWriteLock.lock();
		try {
			if (srtpVarsOutbound.ctxObj == null) {
				return;  // if we didn't have a KMD up until now, we don't need to set a new one
			}
			logDebug(FNC_NAME, "Setting next SRTP outbound KMD (ss=" + paramsCommon.getIdStreamSource().getIdStr() +
					", MKI=" + Long.toUnsignedString(kmd.mki().value()) + ")");
			try {
				srtpVarsOutbound.ctxUpdatePending.set(true);
				srtpVarsOutbound.ctxObj = new SrtpContextOutbound(kmd);
			} catch (SrtxpSecurityException e) {
				throw new IllegalArgumentException("SrtxpSecurityException caught: " + e.getMessage());
			}
		} finally {
			srtpVarsOutbound.ctxWriteLock.unlock();
		}
	}

	public boolean hasSrtpOutboundRekeyingBeenCompleted() {
		return (! srtpVarsOutbound.ctxUpdatePending.get());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		// sanity check
		if (rtpTicksPerFrame < 1L) {
			throw new AssertionError("rtpTicksPerFrame is < 1");
		}

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		final URI tmpAvStreamIncomingUri = paramsCommon.getAvStreamIncomingUri().orElseThrow();
		try (AVSTRIC tmpAvStreamInc = AvStreamIncomingFactory.createAvStreamIncoming(
					avStreamIncomingType,
					paramsCommon.getLogMsgInterface().orElse(null),
					paramsCommon.getIdStreamSource(),
					tmpAvStreamIncomingUri
				)) {
			avStreamIncomingObj = tmpAvStreamInc;

			//
			resetRtpTsFrameNr();
			//
			beforeRunHook();

			//
			receiveInitialClientPackets();

			//
			waitForParallelThreadToStart();

			//
			timeNtpTsInfo.timeSessionStartNtpWc.copyFrom(NtpTimestamp.ofNow());
			timeNtpTsInfo.timeSessionStartMonoNs.setEpochNsUnsigned64bit(System.nanoTime());

			// adjust RTP timestamp T0
			rtpTsT0GenAdj.setEpochNsUnsigned64bit(System.nanoTime());
			rtpTsT0GenAdj.writeProtect();
			rtpTsT0Adj.copyFrom(getRtpTimestampAsInt_t0org_forNow(rtpTsT0GenAdj));
			rtpTsT0Adj.writeProtect();

			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock.copyFrom(getNtpTimestamp(rtpTsT0GenAdj));
			siStats.rtpTimestamp.copyFrom(rtpTsT0Adj);
			sendSenderReport();

			//
			while (! doStop.get()) {
				if ((parComRtpSocketUdp != null && parComRtpSocketUdp.isClosed()) ||
						(parComRtpRwIfTcp != null && parComRtpRwIfTcp.isSocketClosed())) {
					break;
				}
				if (! mainLoop()) {
					break;
				}
			}
		} catch (AvCannotOpenInputException e) {
			logError(FNC_NAME, "AvCannotOpenInputException caught: " + e.getMessage());
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "InputStreamEosException caught");
		} catch (UdpSocketIoException e) {
			logError(FNC_NAME, e.toString());
		} catch (TcpSocketIoException e) {
			// fail silently
		} catch (RtpFrameDataAcquException e) {
			logError(FNC_NAME, "RtpFrameDataAcquException caught: " + e.getMessage());
		} catch (RtpThreadsDidNotStartException e) {
			logError(FNC_NAME, "RtpThreadsDidNotStartException caught: " + e.getMessage());
		} catch (InterruptedException e) {
			logError(FNC_NAME, "Interrupted while sleeping");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			avStreamIncomingObj = null;
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract @NonNull TDP newThreadDataProv();

	protected void beforeRunHook() throws InterruptedException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".beforeRunHook()";

		threadDataProv = newThreadDataProv();
		threadDataProv.setName(Thread.currentThread().getName() + "-dataProv");
		threadDataProv.setDaemon(false);
		threadDataProv.start();

		//
		int loopCnt = 0;
		while (! doStop.get() && threadDataProv != null &&
				(! (threadDataProv.isRunning() && threadDataProv.haveFullInputQueue())) &&
				! threadDataProv.haveEos()) {
			//noinspection BusyWait
			Thread.sleep(50);
			if (++loopCnt % 10 == 0) {
				logDebug(FNC_NAME, "Waiting for input queue to fill up: have " + threadDataProv.getInputQueueSize());
			}
			if (loopCnt >= 10 * 10) {  // 5 seconds
				break;
			}
		}
		if (! doStop.get() && threadDataProv != null && threadDataProv.isRunning() &&
				threadDataProv.haveFullInputQueue() && ! threadDataProv.haveEos()) {
			logDebug(FNC_NAME, "DataProv ready");
		} else {
			if (doStop.get()) {
				logError(FNC_NAME, "DataProv start-up failed: doStop==true");
			} else if (threadDataProv == null) {
				logError(FNC_NAME, "DataProv start-up failed: threadDataProv==null");
			} else if (! threadDataProv.isRunning()) {
				logError(FNC_NAME, "DataProv start-up failed: !isRunning");
			} else if (! threadDataProv.haveFullInputQueue()) {
				logError(FNC_NAME, "DataProv start-up failed: !haveFullInputQueue");
			} else if (threadDataProv.haveEos()) {
				logError(FNC_NAME, "DataProv start-up failed: haveEos");
			} else {
				logError(FNC_NAME, "DataProv start-up failed");
			}
			throw new InputStreamEosException();
		}
	}

	@Override
	protected void stopThreadHook() {
		if (threadDataProv != null) {
			threadDataProv.stopThread();
			threadDataProv = null;
		}
		if (parComRtpSocketUdp != null) {
			parComRtpSocketUdp.close();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract @NonNull FrameData cbFrameDataSupplier();

	protected @NonNull FrameData defaultFrameDataSupplier(@NonNull I codecInfoObj) {
		final String FNC_NAME = getClass().getSimpleName() + ".defaultFrameDataSupplier()";

		cacheFrameData.reset();

		//
		if (threadDataProv == null || ! threadDataProv.isRunning()) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread not running";
		} else if (threadDataProv.haveEos()) {
			cacheFrameData.haveErrorEos = true;
			cacheFrameData.errorMsg = FNC_NAME + ": EOS reached";
		} else {
			// get the next frame to send over the wire from the input stream
			try {
				threadDataProv.getNextFrame(cacheFrameData.rtpPayloadDataForDefFdSupplier, codecInfoObj);
				if (cacheFrameData.rtpPayloadDataForDefFdSupplier.isEmpty()) {
					throw new InputStreamEosException();
				}

				//
				cacheFrameData.totalFrameSize = cacheFrameData.rtpPayloadDataForDefFdSupplier.getUsed();
				cacheFrameData.rtpFrameNr = getRtpTsFrameNr();

				// 'extract' the actual RTP/XXX payload
				cacheFrameData.rtpPayloadDataViewPtr = new BufferView(cacheFrameData.rtpPayloadDataForDefFdSupplier);
				cacheFrameData.rtpPayloadDataViewPtr.setOffset(codecInfoObj.getPayloadOffset());
				cacheFrameData.rtpPayloadDataViewPtr.setLength(codecInfoObj.getPayloadLength());

				//
				cacheFrameData.totalAuRtpPayloadSz = cacheFrameData.rtpPayloadDataViewPtr.getLength();
				cacheFrameData.frameDesc = "Generic Single Frame AU";

				// update frame number
				incrRtpTsFrameNr();
			} catch (InputStreamEosException e) {
				cacheFrameData.haveErrorEos = true;
				cacheFrameData.errorMsg = FNC_NAME + ": EOS reached";
			}
		}

		return cacheFrameData;
	}

	protected abstract @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment);

	protected void prepareRtpPacketDataForFragment(@NonNull FrameFragmentData curFragmentData) {
		if (curFragmentData.frameData().rtpPayloadDataViewPtr == null) {
			throw new IllegalStateException("curFragmentData.frameData().rtpPayloadDataViewPtr == null");
		}
		BufferView tmpBvPtr = curFragmentData.frameData().rtpPayloadDataViewPtr;
		cacheRtpInnerPayloadBufView = tmpBvPtr.clone();
		cacheRtpInnerPayloadBufView.setOffset(tmpBvPtr.getOffset() + curFragmentData.fragmentOffset());
		cacheRtpInnerPayloadBufView.setLength(curFragmentData.fragmentSize());

		//
		cacheParamsBase.reset();
		cacheParamsBase.ssrcId.copyFrom(paramsCommon.getSsrcId());
		cacheParamsBase.sequenceNumber.copyFrom(getRtpSequNr());
		cacheParamsBase.doSetMarker = cbRtpPacketMarkerBitSupplier(
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment()
			);
		cacheParamsBase.rtpTimestamp.copyFrom(curFragmentData.frameRtpTimestamp());
	}

	protected abstract @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData);

	protected @NonNull RtpPacketContainerBase encryptRtpPacketPayload(@NonNull RtpPacketContainerBase plainPacket) {
		final String FNC_NAME = getClass().getSimpleName() + ".encryptRtpPacketPayload()";

		srtpVarsOutbound.ctxReadLock.lock();
		try {
			if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled() || srtpVarsOutbound.ctxObj == null) {
				return plainPacket;
			}
			try {
				if (srtpVarsOutbound.cacheRtpEncrPacket == null || srtpVarsOutbound.ctxUpdatePending.get()) {
					srtpVarsOutbound.cacheRtpEncrPacket = new RtpEncryptedPacket(
							plainPacket.getPayloadType(),
							plainPacket,
							srtpVarsOutbound.ctxObj
						);
				} else {
					srtpVarsOutbound.cacheRtpEncrPacket.updatePacket(plainPacket);
				}
				if (srtpVarsOutbound.ctxUpdatePending.get()) {
					srtpVarsOutbound.ctxUpdatePending.set(false);
					packetCntOutbound.set(0L);
				}
				return srtpVarsOutbound.cacheRtpEncrPacket;
			} catch (SrtxpSecurityException e) {
				final String errMsg = "SrtxpSecurityException caught: " + e.getMessage();
				logError(FNC_NAME, errMsg);
				throw new IllegalStateException(FNC_NAME + ": " + errMsg);
			}
		} finally {
			srtpVarsOutbound.ctxReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull RtspProtoRtpSeqNr getRtpSequNr() {
		return rtpSequNr.clone();
	}

	protected void incrRtpSequNr() {
		rtpSequNr.increment();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	protected long getRtpTsFrameNr() {
		return rtpTsFrameNr.get();
	}

	protected void incrRtpTsFrameNr() {
		rtpTsFrameNr.incrementAndGet();
	}

	protected void resetRtpTsFrameNr() {
		rtpTsFrameNr.set(1);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Try to receive initial packets sent by the client.<br />
	 * This is usually done for NAT/Firewall port testing.
	 */
	private void receiveInitialClientPackets() throws UdpSocketIoException {
		if (parComRtpSocketUdp == null) {
			return;  // we are using TCP
		}
		DatagramPacket recvDp = new DatagramPacket(new byte[UDP_PACKET_LEN], UDP_PACKET_LEN);
		for (int i = 0; i < 10; ++i) {
			try {
				if (parComRtpSocketUdp.isClosed()) {
					break;
				}
				parComRtpSocketUdp.receive(recvDp);
			} catch (SocketTimeoutException e) {
				// ignore
			} catch (IOException e) {
				throw new UdpSocketIoException(e.getMessage());
			}
		}
	}

	/**
	 * Wait for the parallel thread to start playback. If this is a video thread, then wait for the audio thread or vice versa.
	 * If there is no parallel thread, then playback will start immediately.
	 */
	private void waitForParallelThreadToStart() throws InterruptedException, RtpThreadsDidNotStartException {
		final String FNC_NAME = getClass().getSimpleName() + ".waitForParallelThreadToStart()";

		paramsCommon.getCbNotifyThreadReady().orElseThrow().accept(paramsCommon.getIdStreamSource());
		//logDebug(FNC_NAME, "Parallel thread notified");

		int timeoutCnt = 0;
		boolean isReady = false;
		while (++timeoutCnt < 10 * 1000 * 5) {  // 5 seconds
			if (paramsCommon.getCbThreadMayStartPlayback().orElseThrow().get()) {
				isReady = true;
				break;
			}
			Thread.sleep(Duration.ofNanos(100_000L));
		}
		if (! isReady) {
			throw new RtpThreadsDidNotStartException(FNC_NAME + ": Timeout waiting for parallel thread to start");
		}
		//logDebug(FNC_NAME, "Parallel thread started after " + (timeoutCnt / 10) + " ms");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop()
			throws InterruptedException, InputStreamEosException, RtpFrameDataAcquException, UdpSocketIoException, TcpSocketIoException {
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
			if (! siStats.timestampNtpWallclock.isEmpty() &&
					(siStats.lastSenderInfoSent == null ||
							Duration.between(siStats.lastSenderInfoSent, Instant.now()).toMillis() >= SEND_SR_INTERVAL_MS)) {
				sendSenderReport();
			}
			//
			isMainLoopStateA = true;
		}

		//
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoRtpTimestamp getRtpTimestampAsInt_t0adj_forFrameNr(long rtpFrameNr) {
		return rtpTsT0Adj.add((rtpFrameNr - 1) * rtpTicksPerFrame);
	}

	private @NonNull RtspProtoRtpTimestamp getRtpTimestampAsInt_t0adj_forNow(@NonNull TimestampEpochNs currentSysNanos) {
		long elapsedNs = currentSysNanos.getEpochNsUnsigned64bit().orElse(0L) - rtpTsT0GenAdj.getEpochNsUnsigned64bit().orElse(0L);
		long elapsedTicks = ((elapsedNs * rtpClockrate) / 1_000_000_000L);
		return rtpTsT0Adj.add(elapsedTicks);
	}

	private @NonNull RtspProtoRtpTimestamp getRtpTimestampAsInt_t0org_forNow(@NonNull TimestampEpochNs currentSysNanos) {
		ParamsThreadRtpSenderCommon.RtpTsT0WithEpoch tmpRtpTsT0WithEpoch = paramsCommon.getRtpTimestampT0WithEpoch().orElseThrow();
		long elapsedNs = currentSysNanos.getEpochNsUnsigned64bit().orElse(0L) -
				tmpRtpTsT0WithEpoch.rtpGenTsT0Ns().getEpochNsUnsigned64bit().orElse(0L);
		long elapsedTicks = ((elapsedNs * rtpClockrate) / 1_000_000_000L);
		return tmpRtpTsT0WithEpoch.rtpTsT0().add(elapsedTicks);
	}

	private boolean sendFrame()
			throws InputStreamEosException, RtpFrameDataAcquException, UdpSocketIoException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendFrame()";

		// acquire the next frame from the video stream
		long tmpTsNs = System.nanoTime();
		final FrameData frameData = cbFrameDataSupplier();
		if (frameData.haveErrorEos) {
			logDebug(FNC_NAME, "haveErrorEos: " + frameData.errorMsg);
			throw new InputStreamEosException();
		}
		if (frameData.haveErrorOther) {
			throw new RtpFrameDataAcquException(frameData.errorMsg);
		}
		if (frameData.rtpPayloadDataViewPtr == null) {
			throw new IllegalStateException(FNC_NAME + ": frameData.rtpPayloadDataViewPtr == null");
		}
		//
		if (paramsCommon.getIsStreamSourceFromFile()) {
			long tmpDeltaFdsNs = (System.nanoTime() - tmpTsNs);
			if (tmpDeltaFdsNs > 5_000_000L) {
				logWarn(FNC_NAME, String.format("cbFrameDataSupplier took %.3f us", tmpDeltaFdsNs / 1000.0));
			}
		}

		// only sleep if this is the first packet of the frame/AU
		boolean tmpStoreIs1stPktOfFrame = isFirstPktOfFrame;
		if (isFirstPktOfFrame) {
			if (paramsCommon.getIsStreamSourceFromFile()) {
				adaptiveScheduler.waitForNextFrame();
			}
			//
			TimestampEpochNs tmpCurSysNanos = TimestampEpochNs.ofNow();
			if (paramsCommon.getIsStreamSourceFromFile()) {
				rtpTsCurrent.copyFrom(getRtpTimestampAsInt_t0adj_forFrameNr(frameData.rtpFrameNr));
			} else {
				rtpTsCurrent.copyFrom(getRtpTimestampAsInt_t0adj_forNow(tmpCurSysNanos));
			}
			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock.copyFrom(getNtpTimestamp(tmpCurSysNanos));
			siStats.rtpTimestamp.copyFrom(rtpTsCurrent);
			//
			isFirstPktOfFrame = false;
		}

		//
		debugStreamOffset += frameData.totalFrameSize;

		//
		if (frameData.totalFrameSize > largestFrame) {
			largestFrame = frameData.totalFrameSize;
			if (largestFrame > 250_000) {
				logWarn(FNC_NAME, (rtpPacketType.isVideo() ? "image" : "audio") +
						" quality/size might be too high (frame sz=" + largestFrame + " bytes)");
			}
		}

		//
		int sentTotalPktSize = 0;
		int curPktIndex = 0;
		int estTotalPktCnt = Math.max(1, 1 + (int)(frameData.totalAuRtpPayloadSz / (UDP_PACKET_LEN - udpMaxPacketLenDelta)));
		boolean isLastPktOfFrame = false;
		while (! doStop.get() && sentTotalPktSize < frameData.rtpPayloadDataViewPtr.getLength()) {
			final int curPktSize = Math.min(
					UDP_PACKET_LEN - udpMaxPacketLenDelta,
					frameData.rtpPayloadDataViewPtr.getLength() - sentTotalPktSize
				);
			final boolean isLastPktOfPayload = (sentTotalPktSize + curPktSize == frameData.rtpPayloadDataViewPtr.getLength());
			isLastPktOfFrame = cbRtpPacketMarkerBitSupplier(sentTotalPktSize, isLastPktOfPayload);

			//
			boolean tmpResB = sendSinglePacket(
					sentTotalPktSize,
					curPktSize,
					curPktIndex++,
					estTotalPktCnt,
					frameData,
					isLastPktOfPayload,
					tmpStoreIs1stPktOfFrame,
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
			//
			long tmpDeltaSendFrameNs = siStats.timestampNtpWallclock.diffNanos(getNtpTimestamp());
			if (tmpDeltaSendFrameNs > adaptiveScheduler.getSendIntervalNs() - 1_000_000L) {
				logWarn(FNC_NAME, String.format("send frame/AU took %.3f us", tmpDeltaSendFrameNs / 1000.0));
			}
		}

		return true;
	}

	private boolean sendSinglePacket(
				int sentTotalPktSize,
				int curPktSize,
				int curPktIndex,
				int estTotalPktCnt,
				@NonNull FrameData frameData,
				boolean isLastPktOfPayload,
				@SuppressWarnings("unused") boolean isFirstPktOfFrameOrAu,
				boolean isLastPktOfFrameOrAu
			) throws UdpSocketIoException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendSinglePacket()";

		FrameFragmentData curFragmentData = new FrameFragmentData(
				frameData,
				rtpTsCurrent,
				sentTotalPktSize,
				curPktSize,
				curPktIndex,
				isLastPktOfPayload
			);
		RtpPacketContainerBase curPacketContainer = cbRtpPacketPayloadSupplier(curFragmentData);

		// retrieve the packet bitstream and store it in an array of bytes
		if (curPacketContainer.getPacketSize() > UDP_PACKET_LEN) {
			throw new IllegalStateException(FNC_NAME + ": buffer > UDP_PACKET_LEN (d=" +
					(curPacketContainer.getPacketSize() - UDP_PACKET_LEN) + ")");
		}

		if (parComRtpSocketUdp != null) {
			if (parComRtpSocketUdp.isClosed()) {
				if (! doStop.get()) {
					logError(FNC_NAME, "socket is closed");
				}
				return false;
			}
			// send the packet as a DatagramPacket over the UDP socket
			DatagramPacket sendDp = new DatagramPacket(
					curPacketContainer.getPacketBufferPtr().getBaPtr(),
					curPacketContainer.getPacketSize(),
					paramsCommon.getTpClientIpAddr().getIpAddrObj().orElseThrow(),
					paramsCommon.getTpClientDestUdpPort().getPort16bit().orElseThrow()
				);
			try {
				parComRtpSocketUdp.send(sendDp);
			} catch (IOException e) {
				if (doStop.get()) {
					return false;
				}
				throw new UdpSocketIoException(FNC_NAME + ": send() failed: " + e.getMessage());
			}
		} else if (parComRtpRwIfTcp != null) {
			if (parComRtpRwIfTcp.isSocketClosed()) {
				// fail silently
				return false;
			}
			// send the packet over the TCP socket
			BufferView tmpBv = new BufferView(
					curPacketContainer.getPacketBufferPtr(),
					0,
					curPacketContainer.getPacketSize()
				);
			parComRtpRwIfTcp.writeRtpBinary(tmpBv, paramsCommon.getTpClientDestTcpChann());
		}

		//
		packetCntOutbound.incrementAndGet();

		//
		if (! isLastPktOfFrameOrAu && estTotalPktCnt > 1) {
			adaptiveScheduler.sleepUntilNanos(System.nanoTime() + 50_000L, false);
		}
		//
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull NtpTimestamp getNtpTimestamp() {
		return getNtpTimestamp(TimestampEpochNs.ofNow());
	}

	private @NonNull NtpTimestamp getNtpTimestamp(@NonNull TimestampEpochNs currentSysNanos) {
		long deltaMono = (currentSysNanos.getEpochNsUnsigned64bit().orElseThrow() -
				timeNtpTsInfo.timeSessionStartMonoNs.getEpochNsUnsigned64bit().orElseThrow());
		return timeNtpTsInfo.timeSessionStartNtpWc.addNanos(deltaMono);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void sendSenderReport_buildRtcpSr(@NonNull BufferExt packetSrBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".sendSenderReport_buildRtcpSr()";

		if (siStats.timestampNtpWallclock.isEmpty()) {
			throw new IllegalStateException(FNC_NAME + ": timestampNtpWallclock is empty");
		}

		RtcpInnerSenderInfoBlock siBlock = new RtcpInnerSenderInfoBlock(
				siStats.timestampNtpWallclock,
				siStats.rtpTimestamp,
				siStats.rtpPacketsSent,
				siStats.rtpPayloadBytesSent
			);
		/*if (rtpPacketType.isVideo()) {
			logDebug(FNC_NAME, "siBlock=" + siBlock);
		}*/
		RtcpPacketSR packetSrObj = new RtcpPacketSR(
				paramsCommon.getSsrcId(),
				siBlock,
				List.of()
			);
		packetSrObj.copyRawPacketDataInto(packetSrBuf);
	}

	private void sendSenderReport_buildRtcpCompound(@NonNull BufferExt packetCompoundBuf) {
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

	private void sendSenderReport() {
		BufferExt packetCompoundBuf = new BufferExt();
		sendSenderReport_buildRtcpCompound(packetCompoundBuf);

		//
		paramsCommon.getCbRtcpAppendToOutgoingQueue().orElseThrow()
				.accept(paramsCommon.getSsrcId(), packetCompoundBuf);

		siStats.lastSenderInfoSent = Instant.now();
	}

}
