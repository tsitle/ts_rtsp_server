package org.tsitle.rtsp_server.threads.rtsp_play;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdSubStreamNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.common.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HostnameHelper;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.ThreadPausableBase;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_dataprov.threads_dmxFc.ThreadDataProvDmxFc;
import org.tsitle.rtsp_server.threads.rtcp.RtcpReceivedByeInterface;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.builders.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;

import java.net.URI;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class RtspChildThreadMng {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull Set<@NonNull RtspProtoIdSubStream> subStreamIds = new HashSet<>();
	private final @NonNull RtspProtoIdSession idSession = RtspProtoIdSession.ofEmpty();
	private final @NonNull RtspProtoIpAddr clientIpAddr = RtspProtoIpAddr.ofLoopback();
	private final @NonNull Map<@NonNull RtspProtoIdSubStream, @NonNull RtspProtoSetupInfoForSubStream> setupInfoPerSsMap = new HashMap<>();
	private final @NonNull RtspChildThreadsCbNotifyThreadReadyInterface rctcbNtr;
	private final @NonNull RtspChildThreadsCbRtxpTcpInterface rctcbRtpTcp;
	private final @NonNull RtspChildThreadsCbRtcpFromRtpInterface rctcbRtcpFromRtp;
	private final @NonNull RtcpReceivedByeInterface rtcpReceivedByeInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;

	private final ReadWriteLock theLockCtfos = new ReentrantReadWriteLock();
	private final Lock theReadLockCtfos = theLockCtfos.readLock();
	private final Lock theWriteLockCtfos = theLockCtfos.writeLock();
	private final Map<@NonNull RtspProtoIdSubStream, @NonNull ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();

	public @Nullable ThreadDataProvDmxFc childThreadDmxFc = null;

	/** Input Source ID currently in use */
	private final @NonNull RtspProtoIdInputSource usedIdInputSource = RtspProtoIdInputSource.ofEmpty();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param subStreamIds Sub-Stream IDs
	 * @param idSession Session ID
	 * @param clientIpAddr Client IP address
	 * @param setupInfoPerSsMap Setup info per Sub-Stream
	 * @param rctcbNtr 'Notify Thread is Ready' interface for RTP threads
	 * @param rctcbRtpTcp 'RTxP TCP' instance for RTP threads
	 * @param rctcbRtcpFromRtp 'Send RTCP packets from RTP' interface for RTP threads
	 * @param rtcpReceivedByeInterface 'Received BYE packet' interface for RTCP threads
	 * @param availableStreamsInterface Available streams instance (only required for requests from the server)
	 * @param globalSessionInfoInterface Global session info instance (only required for requests from the server)
	 */
	RtspChildThreadMng(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull Set<@NonNull RtspProtoIdSubStream> subStreamIds,
				@NonNull RtspProtoIdSession idSession,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull Map<@NonNull RtspProtoIdSubStream, @NonNull RtspProtoSetupInfoForSubStream> setupInfoPerSsMap,
				@NonNull RtspChildThreadsCbNotifyThreadReadyInterface rctcbNtr,
				@NonNull RtspChildThreadsCbRtxpTcpInterface rctcbRtpTcp,
				@NonNull RtspChildThreadsCbRtcpFromRtpInterface rctcbRtcpFromRtp,
				@NonNull RtcpReceivedByeInterface rtcpReceivedByeInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface
			) {
		if (subStreamIds.isEmpty()) {
			throw new IllegalArgumentException("subStreamIds is empty");
		}
		if (idSession.isEmpty()) {
			throw new IllegalArgumentException("idSession is empty");
		}
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("clientIpAddr is empty");
		}
		this.logMsgInterface = logMsgInterface;
		for (RtspProtoIdSubStream tmpIdSs : subStreamIds){
			this.subStreamIds.add(tmpIdSs.clone());
		}
		this.idSession.copyFrom(idSession);
		this.clientIpAddr.copyFrom(clientIpAddr);
		this.setupInfoPerSsMap.putAll(setupInfoPerSsMap);
		this.rctcbNtr = rctcbNtr;
		this.rctcbRtpTcp = rctcbRtpTcp;
		this.rctcbRtcpFromRtp = rctcbRtcpFromRtp;
		this.rtcpReceivedByeInterface = rtcpReceivedByeInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesAll() {
		theReadLockCtfos.lock();
		try {
			return new ArrayList<>(childThreadsForOneStreamMap.values());
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesOnlyRunning() {
		theReadLockCtfos.lock();
		try {
			final Collection<@NonNull ChildThreadsForOneStream> resC = new ArrayList<>();
			for (ChildThreadsForOneStream ctfos : childThreadsForOneStreamMap.values()) {
				if (ctfos.rtpThreadSender == null || ! ctfos.rtpThreadSender.isRunning()) {
					continue;
				}
				if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
					continue;
				}
				resC.add(ctfos);
			}
			return resC;
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean ctfosMapContainsKey(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLockCtfos.lock();
		try {
			return childThreadsForOneStreamMap.containsKey(idSubStream);
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	@NonNull ChildThreadsForOneStream getCtfosMapValue(@NonNull RtspProtoIdSubStream idSubStream) {
		theReadLockCtfos.lock();
		try {
			if (! childThreadsForOneStreamMap.containsKey(idSubStream)) {
				throw new IllegalArgumentException("Sub-Stream ID not found in child threads map: " + idSubStream);
			}
			return childThreadsForOneStreamMap.get(idSubStream);
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	void startChildThreads(@NonNull RtspProtoRscUrl rscUrl) {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		if (rscUrl.idInputSource.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": idInputSource must be set");
		}
		if (! usedIdInputSource.isEmpty()) {  // sanity check
			throw new IllegalStateException(FNC_NAME + ": An Input Source is already in use");
		}
		usedIdInputSource.copyFrom(rscUrl.idInputSource);
		usedIdInputSource.writeProtect();

		//
		String cnameHostname;
		try {
			URI tmpIsUri = HostnameHelper.convertRtspUrlIntoURI(rscUrl.getUrlStr());
			cnameHostname = tmpIsUri.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		//
		theWriteLockCtfos.lock();
		try {
			for (RtspProtoIdSubStream tmpIdSs : subStreamIds) {
				if (childThreadsForOneStreamMap.containsKey(tmpIdSs)) {
					throw new IllegalStateException(FNC_NAME + ": Child thread for ss='" +
							tmpIdSs.getIdStr().orElse("-unset-") + "' already exists");
				}
				//
				RtspProtoIdEsSource tmpIdEs;
				try {
					tmpIdEs = globalSessionInfoInterface.getElementaryStreamSourceIdBySubStreamId(tmpIdSs, clientIpAddr);
				} catch (RtspProtoIdSubStreamNotFoundException e) {
					logError(FNC_NAME, "Elementary-Stream Source ID for ss='" +
							tmpIdSs.getIdStr().orElse("-unset-") + "' not found");
					continue;
				}
				//
				RtspProtoEsSourceExpandedInfo tmpAvSsi =
						availableStreamsInterface.getElementaryStreamSourceExpInfo(tmpIdEs);
				//
				if (tmpAvSsi.esSourceType() == RtspProtoEsSourceType.ST_ES_MQ && tmpAvSsi.codec() == RtpPacketType.UNKNOWN) {
					logError(FNC_NAME, "ss='" + tmpIdSs.getIdStr().orElse("-unset-") + "': " +
							"Source is a message queue, but codec is not set");
					continue;
				}
				//
				if (tmpAvSsi.esSourceType() == RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_FC && childThreadDmxFc == null) {
					childThreadDmxFc = new ThreadDataProvDmxFc(
							logMsgInterface,
							tmpAvSsi.inputUri()
						);
					childThreadDmxFc.setName(
							"RTP_" +
							"#sid" + idSession.getIdStr().orElseThrow() +
							"#c" + tmpAvSsi.codec().getValue() +
							"-dmxFc"
						);
					childThreadDmxFc.setDaemon(false);
					childThreadDmxFc.start();
				}
				//
				ChildThreadsForOneStream ctfos = new ChildThreadsForOneStream(
						tmpIdSs,
						usedIdInputSource,
						tmpIdEs
					);
				childThreadsForOneStreamMap.put(tmpIdSs, ctfos);

				startSendRtp_oneStream(ctfos, cnameHostname);
				startRtcp_oneStream(ctfos);
			}
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": Could not find SSI: " + e.getMessage());
		} finally {
			theWriteLockCtfos.unlock();
		}
	}

	void pauseOrStopChildThreads(boolean doPause) {
		if (usedIdInputSource.isEmpty()) {  // sanity check
			return;
		}
		theReadLockCtfos.lock();
		try {
			if (! doPause && childThreadDmxFc != null) {
				childThreadDmxFc.stopThread();  // blocks until the thread has actually stopped
			}
			//
			for (RtspProtoIdSubStream tmpIdSs : subStreamIds) {
				if (! childThreadsForOneStreamMap.containsKey(tmpIdSs)) {
					continue;
				}
				ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpIdSs);
				if (ctfos.rtcpThreadSendRecv != null) {
					if (doPause) {
						ctfos.rtcpThreadSendRecv.pauseThread();
					} else {
						ctfos.rtcpThreadSendRecv.stopThread();  // blocks until the thread has actually stopped
					}
				}
				if (ctfos.rtpThreadSender != null) {
					if (doPause) {
						ctfos.rtpThreadSender.pauseThread();
					} else {
						ctfos.rtpThreadSender.stopThread();  // blocks until the thread has actually stopped
					}
				}
			}
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	void unpauseChildThreads() {
		if (usedIdInputSource.isEmpty()) {  // sanity check
			return;
		}
		theReadLockCtfos.lock();
		try {
			for (RtspProtoIdSubStream tmpIdSs : subStreamIds) {
				if (! childThreadsForOneStreamMap.containsKey(tmpIdSs)) {
					continue;
				}
				ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpIdSs);
				if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
					ctfos.rtcpThreadSendRecv.unpauseThread();
				}
				if (ctfos.rtpThreadSender != null && ctfos.rtpThreadSender.isPaused()) {
					ctfos.rtpThreadSender.unpauseThread();
				}
			}
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	boolean seekStream(double targetTimestamp) {
		if (usedIdInputSource.isEmpty()) {  // sanity check
			return false;
		}
		theReadLockCtfos.lock();
		try {
			if (childThreadDmxFc == null) {
				return false;
			}
			return childThreadDmxFc.seekStream(targetTimestamp);
		} finally {
			theReadLockCtfos.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	boolean isTcpConnectionAlive() {
		return rctcbRtpTcp.isTcpConnectionAlive();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void stopChildThread(@Nullable ThreadPausableBase thread) {
		if (thread != null) {
			if (thread.isPaused()) {
				thread.unpauseThread();
			}
			thread.stopThread();  // blocks until the thread has actually stopped
		}
	}

	private void startRtcp_oneStream(@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".startRtcp_oneStream()";

		stopChildThread(ctfos.rtcpThreadSendRecv);
		//
		if (! setupInfoPerSsMap.containsKey(ctfos.idSubStream)) {
			throw new IllegalStateException(FNC_NAME + ": idSubStream not found");
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = setupInfoPerSsMap.get(ctfos.idSubStream);
		//
		RtspProtoEsSourceExpandedInfo tmpAvSsi;
		try {
			tmpAvSsi = availableStreamsInterface.getElementaryStreamSourceExpInfo(ctfos.idEsSource);
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": idEsSource not found");
		}
		//
		BuilderThreadRtcp.Builder tmpBuilder = BuilderThreadRtcp.builder()
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.debugIdSession(idSession)
				.idEsSource(ctfos.idEsSource)
				.idSubStream(ctfos.idSubStream)
				.idSsrc(tmpSiSs.getSsrcOutboundPtr())
				.rtcpReceivedByeInterface(rtcpReceivedByeInterface)
				.tpClientIpAddr(clientIpAddr);
		if (tmpSiSs.getSubStreamTpPtr().getIsUdp()) {
			tmpBuilder
					.tpClientDestUdpPortRtcp(tmpSiSs.getSubStreamTpPtr().getClientUdpPortRtcpPtr())
					.tpSocketUdpRtcp(Objects.requireNonNull(tmpSiSs.getServerUdpSocketRtcpPtr()));
		} else {
			tmpBuilder
					.tpClientDestTcpIf(rctcbRtpTcp)
					.tpClientDestTcpChannRtcp(tmpSiSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr());
		}
		ctfos.rtcpThreadSendRecv = tmpBuilder
				.cryptoIsRtxpEncryptionEnabled(tmpSiSs.getSubStreamTpPtr().getIsEncr())
				.cryptoKmdInboundRtcp(tmpSiSs.getKmdInboundCurPtr().getKmd().orElse(null))
				.cryptoKmdOutboundRtcp(tmpSiSs.getKmdOutboundPtr().getKmd().orElse(null))
				.build();
		ctfos.rtcpThreadSendRecv.setName(
				"RTCP" +
				"#sid" + idSession.getIdStr().orElseThrow() +
				"#es" + ctfos.idEsSource.getIdStr().orElse("-unset-") +
				"#" + tmpAvSsi.codec().getValue()
			);
		ctfos.rtcpThreadSendRecv.setDaemon(false);
		ctfos.rtcpThreadSendRecv.start();
	}

	private <B extends BuilderThreadRtpSenderBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?>>
			BuilderThreadRtpSenderBase<B, T> buildThreadRtpSender(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					@NonNull RtspProtoEsSourceExpandedInfo avSsi,
					@NonNull RtspProtoIdEsSource idEsSource,
					double avFpsAsDbl,
					@NonNull RtcpInnerXsrcBlock xsrcBlock
				) {
		if (streamInfo.getSubStreamTpPtr().getIsUdp()) {
			builder
					.comTpClientDestUdpPortRtp(streamInfo.getSubStreamTpPtr().getClientUdpPortRtpPtr())
					.comTpSocketUdpRtp(Objects.requireNonNull(streamInfo.getServerUdpSocketRtpPtr()));
		} else {
			builder
					.comTpClientDestTcpIf(rctcbRtpTcp)
					.comTpClientDestTcpChannRtp(streamInfo.getSubStreamTpPtr().getClientTcpChannRtpPtr());
		}
		if (childThreadDmxFc != null) {
			builder.comDmxFcReadNextAvPacketInterface(childThreadDmxFc);
		}
		return builder
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.comDebugIdSession(idSession)
				.comIdEsSource(idEsSource)
				.comIdSubStream(streamInfo.getRscUrlSubStreamPtr().idSubStream)
				.comIdSsrc(streamInfo.getSsrcOutboundPtr())
				.comTpClientIpAddr(clientIpAddr)
				.comCryptoIsRtxpEncryptionEnabled(streamInfo.getSubStreamTpPtr().getIsEncr())
				.comCryptoKmdOutboundRtp(streamInfo.getKmdOutboundPtr().getKmd().orElse(null))
				.comEsStreamSourceType(avSsi.esSourceType())
				.comAvFps(avFpsAsDbl)
				.comRtpSeqNrT0(streamInfo.getRtpSeqNrT0Ptr())
				.comRtpTimestampT0(
						new ParamsThreadRtpSenderCommon.RtpTsT0WithMonoRef(
								streamInfo.getRtpTimestampT0Ptr(),
								streamInfo.getRtpGenTsT0MonoNsPtr()
							)
					)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendSrToOutgoingQueue(rctcbRtcpFromRtp::cbSendRtcpSrPacketFromRtp)
				.comCbRtcpAppendByeToOutgoingQueue(rctcbRtcpFromRtp::cbSendRtcpByePacketFromRtp)
				.comCbNotifyThreadReady(rctcbNtr::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(rctcbNtr::cbThreadMayStartPlayback)
				.comAvStreamIncomingUri(avSsi.inputUri());
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?>>
			BuilderThreadRtpSenderVideoBase<B, T> buildThreadVideo(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					@NonNull RtspProtoEsSourceExpandedInfo avSsi,
					@NonNull RtspProtoIdEsSource idEsSource,
					@NonNull FrameRateEnum avFpsAsEn,
					@NonNull RtcpInnerXsrcBlock xsrcBlock
				) {
		return (BuilderThreadRtpSenderVideoBase<B, T>)buildThreadRtpSender(
						builder,
						streamInfo,
						avSsi,
						idEsSource,
						avFpsAsEn.getFrDbl(),
						xsrcBlock
					)
				.comIsVideoThread(true);
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?>>
			BuilderThreadRtpSenderBase<B, T> buildThreadAudio(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					@NonNull RtspProtoEsSourceExpandedInfo avSsi,
					@NonNull RtspProtoIdEsSource idEsSource,
					double avFpsAsDbl,
					@NonNull RtcpInnerXsrcBlock xsrcBlock,
					int samplesPerFrame
				) {
		BuilderThreadRtpSenderAudioBase<B, T> b = (BuilderThreadRtpSenderAudioBase<B, T>)buildThreadRtpSender(
						builder,
						streamInfo,
						avSsi,
						idEsSource,
						avFpsAsDbl,
						xsrcBlock
					)
				.comIsVideoThread(false);
		return b
				.audComRtpAudioSpf(samplesPerFrame)
				.audComSamplerate(avSsi.audioSampleRate());
	}

	private void startSendRtp_oneStream(@NonNull ChildThreadsForOneStream ctfos, @NonNull String cnameHostname)
			throws RtspProtoIdEsSourceNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".startSendRtp_oneStream()";

		stopChildThread(ctfos.rtpThreadSender);
		//
		if (! setupInfoPerSsMap.containsKey(ctfos.idSubStream)) {
			throw new IllegalStateException(FNC_NAME + ": idSubStream not found");
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = setupInfoPerSsMap.get(ctfos.idSubStream);
		//
		RtcpInnerXsrcBlock xsrcBlock = new RtcpInnerXsrcBlock(
				1,
				tmpSiSs.getSsrcOutboundPtr(),
				List.of(
						new RtcpInnerXsrcBlock.BlockEntry(
								RtcpInnerXsrcBlock.BlockType.CNAME,
								idSession.getIdStr().orElseThrow() + "@" + cnameHostname
							)
					)
			);
		// sanity check
		if (tmpSiSs.getRscUrlSubStreamPtr().idSubStream.isEmpty()) {
			throw new IllegalStateException(FNC_NAME + ": idSubStream is empty");
		}
		//
		RtspProtoEsSourceExpandedInfo tmpAvSsi;
		try {
			tmpAvSsi = availableStreamsInterface.getElementaryStreamSourceExpInfo(ctfos.idEsSource);
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": idEsSource not found");
		}
		//
		switch (tmpAvSsi.codec()) {
			case A_AAC:
				BuilderThreadRtpSenderBase<?, ?> builderAac = buildThreadAudio(
						BuilderThreadRtpSenderAac.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						availableStreamsInterface.computeElementaryStreamSource_virtualFps(ctfos.idEsSource),
						xsrcBlock,
						availableStreamsInterface.getElementaryStreamSource_samplesPerFrame(ctfos.idEsSource)
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderAac.build();
				break;
			case A_AC3:
				BuilderThreadRtpSenderBase<?, ?> builderAc3 = buildThreadAudio(
						BuilderThreadRtpSenderAc3.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						availableStreamsInterface.computeElementaryStreamSource_virtualFps(ctfos.idEsSource),
						xsrcBlock,
						availableStreamsInterface.getElementaryStreamSource_samplesPerFrame(ctfos.idEsSource)
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderAc3.build();
				break;
			case A_MPEG:
				BuilderThreadRtpSenderBase<?, ?> builderMpa = buildThreadAudio(
						BuilderThreadRtpSenderMpa.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						availableStreamsInterface.computeElementaryStreamSource_virtualFps(ctfos.idEsSource),
						xsrcBlock,
						availableStreamsInterface.getElementaryStreamSource_samplesPerFrame(ctfos.idEsSource)
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderMpa.build();
				break;
			case A_OPUS:
				BuilderThreadRtpSenderBase<?, ?> builderOpus = buildThreadAudio(
						BuilderThreadRtpSenderOpus.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						availableStreamsInterface.computeElementaryStreamSource_virtualFps(ctfos.idEsSource),
						xsrcBlock,
						availableStreamsInterface.getElementaryStreamSource_samplesPerFrame(ctfos.idEsSource)
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderOpus.build();
				break;
			case V_H264:
				BuilderThreadRtpSenderBase<?, ?> builderH264 = buildThreadVideo(
						BuilderThreadRtpSenderH264.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderH264.build();
				break;
			case V_H265:
				BuilderThreadRtpSenderBase<?, ?> builderH265 = buildThreadVideo(
						BuilderThreadRtpSenderH265.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderH265.build();
				break;
			case V_MJPEG:
				BuilderThreadRtpSenderBase<?, ?> builderMjpeg = buildThreadVideo(
						BuilderThreadRtpSenderMjpeg.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderMjpeg.build();
				break;
			case V_VP8:
				BuilderThreadRtpSenderBase<?, ?> builderVp8 = buildThreadVideo(
						BuilderThreadRtpSenderVp8.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderVp8.build();
				break;
			default:
				if (! tmpAvSsi.codec().isPcmAudio()) {
					throw new IllegalStateException(FNC_NAME + ": Unsupported codec: " + tmpAvSsi.codec());
				}
				//
				BuilderThreadRtpSenderBase<?, ?> builderPcm = buildThreadAudio(
						BuilderThreadRtpSenderPcm.builder(),
						tmpSiSs,
						tmpAvSsi,
						ctfos.idEsSource,
						availableStreamsInterface.computeElementaryStreamSource_virtualFps(ctfos.idEsSource),
						xsrcBlock,
						availableStreamsInterface.getElementaryStreamSource_samplesPerFrame(ctfos.idEsSource)
					);
				//
				((BuilderThreadRtpSenderPcm.Builder)builderPcm)
						.audPcmChannelCount(tmpAvSsi.audioChannelCount())
						.audPcmBitsPerSample(tmpAvSsi.codec().getPcmAudioBitsPerSample().orElseThrow())
						.audPcmInputBigEndian(tmpAvSsi.isAudioPcmBigEndian())
						.audPcmCodec(tmpAvSsi.codec());
				//
				ctfos.rtpThreadSender = (ThreadRtpSenderBase<?, ?, ?>)builderPcm.build();
		}
		ctfos.rtpThreadSender.setName(
				"RTP_" +
				"#sid" + idSession.getIdStr().orElseThrow() +
				"#es" + ctfos.idEsSource.getIdStr().orElse("-unset-") +
				"#" + tmpAvSsi.codec().getValue()
			);
		ctfos.rtpThreadSender.setDaemon(false);
		ctfos.rtpThreadSender.start();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
