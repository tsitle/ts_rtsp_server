package org.tsitle.rtsp.threads.rtp.codec_pcm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadPcm;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvPcm;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.util.Objects;

public final class ThreadRtpSenderPcm extends ThreadRtpSenderBase {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderPcm paramsPcm;
	private @Nullable ThreadDataProvPcm threadDataProv;

	/** Number of audio channels */
	public int audioChannelCount;

	/** Bits per sample (8 or 16) */
	public int audioBitsPerSample;
	/** RTP Payload type */
	private final RtpPacketType rtpPayloadType;

	private final PcmInfo curFramePcmInfo = new PcmInfo();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgAudioFrameBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 */
	public ThreadRtpSenderPcm(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm
			) {
		super(
				paramsCommon,
				Objects.requireNonNull(paramsPcm).getAudioSampleRateHz(),
				Objects.requireNonNull(paramsPcm).getAudioCodec()
			);

		//
		this.rtpTicksPerFrame = paramsPcm.getRtpAudioSpf();

		//
		paramsAudioCommon.validate();
		this.paramsAudioCommon = paramsAudioCommon.clone();
		paramsPcm.validate();
		this.paramsPcm = paramsPcm.clone();

		//
		this.audioChannelCount = paramsPcm.getAudioChannelCount();
		this.audioBitsPerSample = paramsPcm.getAudioBitsPerSample();
		this.rtpPayloadType = paramsPcm.getAudioCodec();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		if (threadDataProv != null) {
			threadDataProv.notifyCongestionLevelChange(congestionLevel);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void beforeRunHook() {
		threadDataProv = new ThreadDataProvPcm(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsAudioCommon,
				paramsPcm,
				(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
				paramsCommon.getDebugRewindMediaFiles()
			);
		threadDataProv.setName(Thread.currentThread().getName() + "-dataProv");
		threadDataProv.setDaemon(false);
		threadDataProv.start();

		//
		while (! threadDataProv.haveFullInputQueue()) {
			try {
				//noinspection BusyWait
				Thread.sleep(50);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	protected void stopThreadHook() {
		if (threadDataProv != null) {
			threadDataProv.stopThread();
			threadDataProv = null;
		}

		super.stopThreadHook();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		//
		cacheFrameData.rtpFrameTimestamp = getRtpTimestampAsInt();

		if (threadDataProv == null) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread not running";
		} else if (threadDataProv.haveEof()) {
			cacheFrameData.haveErrorEof = true;
			cacheFrameData.errorMsg = FNC_NAME + ": InputStreamEofException caught";
		} else {
			// get the next frame to send over the wire from the input stream
			try {
				threadDataProv.getNextFrame(cacheOrgAudioFrameBuf, curFramePcmInfo);

				//
				cacheFrameData.totalFrameSize = cacheOrgAudioFrameBuf.getUsed();

				// extract the actual RTP/(PCMU|LinearPCM) payload
				cacheFrameData.rtpPayloadData.copyOf(
						cacheOrgAudioFrameBuf,
						curFramePcmInfo.samplesOffset,
						curFramePcmInfo.samplesLength
					);

				// update frame number
				incrRtpAndNtpTsFrameNr();
			} catch (InputStreamEofException e) {
				cacheFrameData.haveErrorEof = true;
				cacheFrameData.errorMsg = FNC_NAME + ": EOF";
			}
		}

		return cacheFrameData;
	}

	@Override
	protected Boolean cbRtpPacketMarkerBitSupplier(int currentOffsetInFramePlusFragmentSize, int framePayloadSize) {
		/*
		 * For audio (without noise suppression) the marker bit is always set to 0.
		 * See https://datatracker.ietf.org/doc/html/rfc3551#section-4.1
		 */
		return false;
	}

	@Override
	protected RtpPacketPayloadInterface cbRtpPacketPayloadSupplier(FrameFragmentData curFragmentData) {
		cacheRtpInnerPayloadBuf.copyOf(
				curFragmentData.frameData().rtpPayloadData,
				curFragmentData.fragmentOffset(),
				curFragmentData.fragmentSize()
			);

		return new RtpPacketPayloadPcm(
				rtpPayloadType,
				curFragmentData.fragmentOffset(),
				curFramePcmInfo,
				cacheRtpInnerPayloadBuf
			);
	}

}
