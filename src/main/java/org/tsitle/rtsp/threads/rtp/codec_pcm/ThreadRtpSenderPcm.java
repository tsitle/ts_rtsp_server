package org.tsitle.rtsp.threads.rtp.codec_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.avdata.PcmParser;
import org.tsitle.rtsp.avinputstreams.AudioStreamPcm;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidPcmDataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadPcm;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.io.FileNotFoundException;
import java.util.Objects;

public final class ThreadRtpSenderPcm extends ThreadRtpSenderBase {

	/** Number of audio channels */
	public int audioChannelCount;

	/** Bits per sample (8 or 16) */
	public int audioBitsPerSample;
	/** AudioStream object used to access audio frames */
	private final AudioStreamPcm audioStream;
	/** RTP Payload type */
	private final RtpPacketType rtpPayloadType;

	private PcmInfo curFramePcmInfo = null;
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgAudioFrameBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @throws FileNotFoundException If the audio file cannot be opened
	 */
	public ThreadRtpSenderPcm(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm
			) throws FileNotFoundException {
		super(
				paramsCommon,
				Objects.requireNonNull(paramsPcm).getAudioSampleRateHz(),
				1L,
				Objects.requireNonNull(paramsPcm).getAudioCodec()
			);

		//
		paramsAudioCommon.validate();
		paramsPcm.validate();

		//
		this.audioChannelCount = paramsPcm.getAudioChannelCount();
		this.audioBitsPerSample = paramsPcm.getAudioBitsPerSample();
		this.audioStream = new AudioStreamPcm(
				paramsAudioCommon.getAudioFilePath().orElseThrow(),
				paramsPcm.getAudioChannelCount(),
				this.audioBitsPerSample,
				paramsPcm.getRtpAudioSpf(),
				paramsPcm.getIsAudioInputBigEndian()
			);
		this.rtpPayloadType = paramsPcm.getAudioCodec();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		//
		cacheFrameData.rtpFrameTimestamp = getRtpTimestampAsInt();

		int samplesPerChannelInFrame = 0;

		try {
			if (! audioStream.hasMoreFrames() && paramsCommon.getDebugRewindMediaFiles()) {
				logDebug(FNC_NAME, "haveEof, rewinding");
				audioStream.rewind();
			}
			// get the next frame to send from the audio, as well as its size
			audioStream.getNextFrame(cacheOrgAudioFrameBuf);
			if (cacheOrgAudioFrameBuf.getUsed() == 0) {
				// we have reached the end of the audio file
				throw new InputStreamEofException();
			}

			//
			cacheFrameData.totalFrameSize = cacheOrgAudioFrameBuf.getUsed();

			//
			curFramePcmInfo = PcmParser.parsePcmData(
					cacheOrgAudioFrameBuf,
					audioChannelCount,
					audioBitsPerSample
				);
			samplesPerChannelInFrame = curFramePcmInfo.samplesPerChannelInAudioData;

			// extract the actual RTP/(PCMU|LinearPCM) payload
			cacheFrameData.rtpPayloadData.copyOf(
					cacheOrgAudioFrameBuf,
					curFramePcmInfo.samplesOffset,
					curFramePcmInfo.samplesLength
				);
		} catch (AvInvalidPcmDataException | InputStreamIoException ex) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": " + ex;
		} catch (InputStreamEofException ex) {
			cacheFrameData.haveErrorEof = true;
			cacheFrameData.errorMsg = FNC_NAME + ": InputStreamEofException caught";
		}

		// update frame number
		incrRtpAndNtpTsFrameNr(samplesPerChannelInFrame);

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
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		if (curFramePcmInfo == null) {
			throw new IllegalStateException(FNC_NAME + ": curFramePcmInfo == null");
		}
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
