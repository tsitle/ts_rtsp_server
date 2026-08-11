package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerSenderInfoBlock;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpPacketSDES;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpPacketSR;
import org.tsitle.lib_xrtxp.packets.rtp.*;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketH264;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketMjpeg;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketVp8;
import org.tsitle.lib_xrtxp.packets.srtp.RtpEncryptedPacket;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingBase;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.exceptions.*;
import org.tsitle.lib_xrtxp.common.types.NtpTimestamp;
import org.tsitle.lib_xrtxp.kmd.SrtpContextOutbound;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.rtsp_server.threads.ThreadPausableBase;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ThreadRtpSenderBase<
			I extends CodecInfoInterface<I>,
			AVSTRIC extends AvStreamIncomingBase,
			TDP extends ThreadDataProvEsBase<I>
		> extends ThreadPausableBase {

	private record Sf_sfasf_Result(boolean resB, boolean isLastPktOfFrame) { }

	/** Interval for sending Sender Reports (in milliseconds) */
	private static final int SEND_SR_INTERVAL_MS = 500;

	/** Length of UDP packets */
	protected static final int UDP_PACKET_LEN = 1000 + RtpPacketContainerBase.RTP_CONT_HEADER_SIZE + 4 + (128 * 2);

	protected final Class<AVSTRIC> avStreamIncomingType;
	protected @Nullable TDP threadDataProv;

	/** Buffer view for reading the RTP/XXX payload */
	protected @Nullable BufferView cacheRtpInnerPayloadBufView = null;
	/** Stores the current frame data */
	protected final FrameData cacheFrameData = new FrameData();
	protected final ParamsContainerBase cacheParamsBase = new ParamsContainerBase();

	/** Thread parameters */
	protected final ParamsThreadRtpSenderCommon paramsCommon;
	private final @Nullable DatagramSocket parComRtpSocketUdp;
	private final @Nullable RtspChildThreadsCbRtxpTcpInterface parComRtpRwIfTcp;
	/** RTP Clock Rate */
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private final int rtpClockrate;
	/** RTP ticks per frame */
	protected long rtpTicksPerFrame;
	/** RTP packet type */
	protected final RtpPacketType rtpPacketType;
	/** Current RTP timestamp */
	private final @NonNull RtspProtoRtpTimestamp rtpTsCurrent = RtspProtoRtpTimestamp.ofZero();

	/** Current RTP 'frame' number for RTP timestamps, either video frames or audio samples (64 bits unsigned) */
	private final AtomicLong rtpTsFrameNr = new AtomicLong(-1);
	private final @NonNull RtspProtoRtpSeqNr rtpSequNr = RtspProtoRtpSeqNr.ofEmpty();
	protected int debugStreamOffset = 0;
	private boolean isFirstPktOfFrame = true;

	private final AdaptiveScheduler adaptiveScheduler;
	protected long nextRtpTicksPerFrame = -1L;
	protected double nextFpsForAdaptiveScheduler = -1.0;
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
	 * @param paramsCommon Thread parameters
	 * @param rtpClockrate RTP Clock Rate
	 * @param rtpPacketType RTP packet type
	 */
	protected ThreadRtpSenderBase(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				int rtpClockrate,
				@NonNull RtpPacketType rtpPacketType
			) {
		super(paramsCommon.getLogMsgInterface().orElseThrow());

		final String errMsgPrefix = getClass().getSimpleName() + ".ctor(): ";

		// sanity check
		if (UDP_PACKET_LEN > 1400) {
			throw new RuntimeException(errMsgPrefix + "UDP_PACKET_LEN is too large: " + UDP_PACKET_LEN);
		}

		//
		paramsCommon.validate();
		if (rtpClockrate < 1 || rtpClockrate > 90000 * 2) {
			throw new IllegalArgumentException(errMsgPrefix + "Invalid RTP clock rate: " + rtpClockrate);
		}

		//
		this.avStreamIncomingType = avStreamIncomingType;
		//
		this.paramsCommon = paramsCommon.clone();
		this.parComRtpSocketUdp = paramsCommon.getTpSocketUdp().orElse(null);
		this.parComRtpRwIfTcp = paramsCommon.getTpClientDestTcpIf().orElse(null);
		//
		if (rtpPacketType.isVideo() && paramsCommon.getAvFramesPerSecond() < 0.1) {
			throw new IllegalStateException(errMsgPrefix + "FPS < 0.1");
		}
		if (paramsCommon.getAvFramesPerSecond() >= 0.1) {
			final double tmpSendIntervalNs = (1_000_000_000.0 / paramsCommon.getAvFramesPerSecond());
			if (tmpSendIntervalNs < 1_000_000.0) {  // sanity check
				throw new IllegalStateException(errMsgPrefix + "sendInterval is < 1ms");
			}
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
				throw new IllegalArgumentException(errMsgPrefix + "SrtxpSecurityException caught: " + e.getMessage());
			}
		} else {
			this.srtpVarsOutbound.ctxObj = null;
		}

		//
		udpMaxPacketLenDelta = RtpPacketContainerBase.RTP_CONT_HEADER_SIZE + 4;
		if (rtpPacketType == RtpPacketType.V_MJPEG) {
			// RTP/JPEG header can be rather big
			udpMaxPacketLenDelta += RtpPacketMjpeg.INNER_HEADER_MAIN_SIZE +
					RtpPacketMjpeg.INNER_HEADER_QT_PRE_SIZE + 128 * 2;
		} else if (rtpPacketType == RtpPacketType.V_H264 || rtpPacketType == RtpPacketType.V_H265) {
			udpMaxPacketLenDelta += RtpPacketH264.INNER_HEADER_SIZE_MAX;
		} else if (rtpPacketType == RtpPacketType.V_VP8) {
			udpMaxPacketLenDelta += RtpPacketVp8.INNER_HEADER_SIZE_MAX;
		}
		if (paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			if (this.srtpVarsOutbound.ctxObj == null) {
				throw new IllegalStateException(errMsgPrefix + "srtpCtxOutbound == null");
			}
			udpMaxPacketLenDelta += this.srtpVarsOutbound.ctxObj.getSrtpExtraPacketLength();
		}
		if (UDP_PACKET_LEN - udpMaxPacketLenDelta < 128) {
			throw new IllegalStateException(errMsgPrefix + "UDP packet length too small");
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
			logDebug(FNC_NAME, "Setting next SRTP outbound KMD (esSrc=" +
					paramsCommon.getIdEsSource().getIdStr().orElse("-unset-") +
					", MKI=" + Long.toUnsignedString(kmd.mki().getValue().orElse(0L)) + ")");
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
			throw new RuntimeException(FNC_NAME + ": rtpTicksPerFrame is < 1");
		}

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		try {
			resetRtpTsFrameNr();
			//
			beforeRunHook();

			//
			receiveInitialClientPackets();

			//
			waitForParallelThreadToStart();

			//
			timeNtpTsInfo.timeSessionStartNtpWc.copyFrom(NtpTimestamp.ofNow());

			// update SenderInfo NTP and RTP timestamp
			siStats.timestampNtpWallclock.copyFrom(timeNtpTsInfo.timeSessionStartNtpWc);
			siStats.rtpTimestamp.copyFrom(paramsCommon.getRtpTimestampT0WithMonoRef().orElseThrow().rtpTsT0());
			sendSenderReport();

			//
			while (! doStop.get()) {
				if (parComRtpSocketUdp != null && parComRtpSocketUdp.isClosed()) {
					break;
				}
				if (! mainLoop()) {
					break;
				}
			}
		} catch (InputStreamEosException e) {
			logDebug(FNC_NAME, "InputStreamEosException caught");
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
			//e.printStackTrace();
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			// send a BYE packet to let the client know that this stream has ended
			try {
				paramsCommon.getCbRtcpAppendByeToOutgoingQueue().orElseThrow().accept(paramsCommon.getSsrcId());
			} catch (Exception e) {
				// ignore
			}
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public void stopAsap() {
		doStop.set(true);
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
				(! (threadDataProv.isRunning() && threadDataProv.haveFullInputQueue()))) {
			//noinspection BusyWait
			Thread.sleep(10);
			if (++loopCnt % 50 == 0) {
				logDebug(FNC_NAME, "Waiting for input queue to fill up: have " + threadDataProv.getInputQueueSize());
			}
			if (loopCnt >= 100 * 5) {  // 5 seconds
				break;
			}
		}
		if (doStop.get()) {
			logError(FNC_NAME, "DataProv start-up failed: doStop==true");
			return;
		}
		if (threadDataProv == null) {
			logError(FNC_NAME, "DataProv start-up failed: threadDataProv==null");
			throw new InputStreamEosException();
		}
		if (threadDataProv.isRunning() &&
				threadDataProv.haveFullInputQueue() && ! threadDataProv.haveEos()) {
			logDebug(FNC_NAME, "DataProv ready");
		} else {
			if (! threadDataProv.isRunning()) {
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
		if (threadDataProv == null) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread non-existent";
		} else if (threadDataProv.haveEos()) {
			cacheFrameData.haveErrorEos = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread reached EOS";
		} else if (! threadDataProv.isRunning()) {
			cacheFrameData.haveErrorEos = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread not running";
		} else {
			// get the next frame to send over the wire from the input stream
			try {
				threadDataProv.getNextFrame(
						cacheFrameData.rtpPayloadDataForDefFdSupplier,
						cacheFrameData.stTimestamp,
						codecInfoObj
					);
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
			} catch (InputStreamThreadEndedException e) {
				cacheFrameData.haveErrorEos = true;
				cacheFrameData.errorMsg = FNC_NAME + ": Input thread ended";
			} catch (AvInvalidCodecDataException e) {
				cacheFrameData.haveErrorEos = true;
				cacheFrameData.errorMsg = FNC_NAME + ": Invalid Codec Data: " + e.getMessage();
			}
		}

		return cacheFrameData;
	}

	protected abstract int cbFragmentSizeAdjust(int fragmentSize);

	protected int defaultFragmentSizeAdjust(int fragmentSize) {
		return fragmentSize;
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

		paramsCommon.getCbNotifyThreadReady().orElseThrow().accept(paramsCommon.getIdSubStream());
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
		return paramsCommon.getRtpTimestampT0WithMonoRef().orElseThrow().rtpTsT0().add((rtpFrameNr - 1) * rtpTicksPerFrame);
	}

	private boolean sendFrame()
			throws InputStreamEosException, RtpFrameDataAcquException, UdpSocketIoException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendFrame()";

		// acquire the next frame from the video stream
		final FrameData frameData = sendFrame_acquireFd(FNC_NAME);

		// only sleep if this is the first packet of the frame/AU
		boolean tmpStoreIs1stPktOfFrame = isFirstPktOfFrame;
		if (isFirstPktOfFrame) {
			sendFrame_actionBeforeFirstPktOfFrame(frameData);
		}

		//
		debugStreamOffset += frameData.totalFrameSize;

		//
		sendFrame_updateStatsLargestFrame(FNC_NAME, frameData);

		//
		Sf_sfasf_Result tmpRes = sendFrame_splitFrameAndSendFragments(frameData, tmpStoreIs1stPktOfFrame);
		if (! tmpRes.resB) {
			return false;
		}

		//
		if (tmpRes.isLastPktOfFrame || rtpPacketType.isAudio()) {
			sendFrame_actionAfterLastPktOfFrame(FNC_NAME);
		}

		return true;
	}

	private @NonNull FrameData sendFrame_acquireFd(@NonNull String fncName)
			throws RtpFrameDataAcquException, InputStreamEosException {
		long tmpTsNs = System.nanoTime();
		final FrameData frameData = cbFrameDataSupplier();
		if (frameData.haveErrorEos) {
			logDebug(fncName, "haveErrorEos: " + frameData.errorMsg);
			throw new InputStreamEosException();
		}
		if (frameData.haveErrorOther) {
			throw new RtpFrameDataAcquException(frameData.errorMsg);
		}
		if (frameData.rtpPayloadDataViewPtr == null) {
			throw new IllegalStateException(fncName + ": frameData.rtpPayloadDataViewPtr == null");
		}
		//
		if (paramsCommon.getEsSourceType().orElseThrow().isFromFile() && rtpTsFrameNr.get() > 2) {
			long tmpDeltaFdsNs = (System.nanoTime() - tmpTsNs);
			if (tmpDeltaFdsNs > 5_000_000L) {
				logWarn(fncName, String.format("cbFrameDataSupplier took %.3f us", tmpDeltaFdsNs / 1000.0));
			}
		}
		return frameData;
	}

	private void sendFrame_actionBeforeFirstPktOfFrame(final @NonNull FrameData frameData) {
		/*
		 * E.g., for E-AC-3, the frame duration is not constant, so we need to adjust the sleep time
		 * and the [rtpTicksPerFrame].
		 */
		if (nextFpsForAdaptiveScheduler > 0.0) {
			adaptiveScheduler.setFps(nextFpsForAdaptiveScheduler);
			nextFpsForAdaptiveScheduler = -1.0;
		}
		if (nextRtpTicksPerFrame > 0L) {
			rtpTicksPerFrame = nextRtpTicksPerFrame;
			nextRtpTicksPerFrame = -1L;
		}
		//
		adaptiveScheduler.waitForNextFrame();
		//
		rtpTsCurrent.copyFrom(getRtpTimestampAsInt_t0adj_forFrameNr(frameData.rtpFrameNr));
		// update SenderInfo NTP and RTP timestamp
		siStats.timestampNtpWallclock.copyFrom(NtpTimestamp.ofNow());
		siStats.rtpTimestamp.copyFrom(rtpTsCurrent);
		//
		isFirstPktOfFrame = false;
	}

	private void sendFrame_updateStatsLargestFrame(@NonNull String fncName, final @NonNull FrameData frameData) {
		if (frameData.totalFrameSize <= largestFrame) {
			return;
		}
		largestFrame = frameData.totalFrameSize;
		if (largestFrame > 32L * 1024L) {  // purely informational
			logDebug(fncName, "largest frame: " + (largestFrame / 1024L) + " kB");
		}
		if (largestFrame > 250_000) {
			logWarn(fncName, (rtpPacketType.isVideo() ? "image" : "audio") +
					" quality/size might be too high (frame sz=" + largestFrame + " bytes)");
		}
	}

	private @NonNull Sf_sfasf_Result sendFrame_splitFrameAndSendFragments(final @NonNull FrameData frameData, boolean tmpStoreIs1stPktOfFrame)
			throws TcpSocketIoException, UdpSocketIoException {
		if (frameData.rtpPayloadDataViewPtr == null) {
			throw new IllegalStateException(getClass().getSimpleName() + ".sendFrame_splitFrameAndSendFragments(): " +
					"frameData.rtpPayloadDataViewPtr == null");
		}

		int sentTotalPktSize = 0;
		int curPktIndex = 0;
		int estTotalPktCnt = Math.max(1, 1 + (int)(frameData.totalAuRtpPayloadSz / (UDP_PACKET_LEN - udpMaxPacketLenDelta)));
		boolean isLastPktOfFrame = false;
		while (! doStop.get() && sentTotalPktSize < frameData.rtpPayloadDataViewPtr.getLength()) {
			final int curPktSize = Math.min(
					cbFragmentSizeAdjust(UDP_PACKET_LEN - udpMaxPacketLenDelta),
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
				return new Sf_sfasf_Result(false, false);
			}

			sentTotalPktSize += curPktSize;
			++siStats.rtpPacketsSent;
			siStats.rtpPayloadBytesSent += curPktSize;

			// update sequence number
			incrRtpSequNr();
		}

		return new Sf_sfasf_Result(true, isLastPktOfFrame);
	}

	private void sendFrame_actionAfterLastPktOfFrame(@NonNull String fncName) {
		isFirstPktOfFrame = true;
		isMainLoopStateA = false;
		//
		long tmpDeltaSendFrameNs = siStats.timestampNtpWallclock.diffNanos(NtpTimestamp.ofNow());
		if (adaptiveScheduler.getIsWaitForNextFrameEnabled() &&
				tmpDeltaSendFrameNs > adaptiveScheduler.getSendIntervalNs() - 1_000_000L) {
			logDebug(fncName, String.format("send frame/AU took %.3f us", tmpDeltaSendFrameNs / 1000.0));
		}
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
				estTotalPktCnt,
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
			// send the packet over the TCP socket
			BufferView tmpBv = new BufferView(
					curPacketContainer.getPacketBufferPtr(),
					0,
					curPacketContainer.getPacketSize()
				);
			try {
				parComRtpRwIfTcp.cbSendRtpBinaryOverTcp(tmpBv, paramsCommon.getTpClientDestTcpChann());
			} catch (TcpSocketClosedException e) {
				// fail silently
				return false;
			}
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
		paramsCommon.getCbRtcpAppendSrToOutgoingQueue().orElseThrow()
				.accept(paramsCommon.getSsrcId(), packetCompoundBuf);

		siStats.lastSenderInfoSent = Instant.now();
	}

}
