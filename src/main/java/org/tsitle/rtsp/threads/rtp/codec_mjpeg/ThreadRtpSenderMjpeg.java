package org.tsitle.rtsp.threads.rtp.codec_mjpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvMjpeg;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.JpegInfo;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadMjpeg;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ThreadRtpSenderMjpeg extends ThreadRtpSenderBase {

	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;
	private @Nullable ThreadDataProvMjpeg threadDataProv;

	private final JpegInfo curFrameJpegInfo = new JpegInfo();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsMjpeg Thread-specific parameters
	 */
	public ThreadRtpSenderMjpeg(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderMjpeg paramsMjpeg
			) {
		super(
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG),
				RtpPacketType.V_JPEG
			);

		//
		this.rtpTicksPerFrame = (long)((float)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG) /
				Objects.requireNonNull(paramsCommon).getAvFramesPerSecond());

		//
		paramsVideoCommon.validate();
		this.paramsVideoCommon = paramsVideoCommon.clone();
		paramsMjpeg.validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
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
		threadDataProv = new ThreadDataProvMjpeg(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsVideoCommon,
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
				Instant tmpNow = Instant.now();
				threadDataProv.getNextFrame(cacheOrgVideoFrameBuf, curFrameJpegInfo);
				Instant tmpAfter = Instant.now();
				long tmpDiff = Duration.between(tmpNow, tmpAfter).toNanos();
				if (tmpDiff > 100_000L) {  // @TODO
					logDebug(FNC_NAME, "getNextFrame() took " + (Duration.between(tmpNow, tmpAfter).toNanos() / 1000L) + " us");
				}

				//
				cacheFrameData.totalFrameSize = cacheOrgVideoFrameBuf.getUsed();

				// extract the actual RTP/JPEG payload
				cacheFrameData.rtpPayloadData.copyOf(
						cacheOrgVideoFrameBuf,
						curFrameJpegInfo.sos_scanDataOffs,
						curFrameJpegInfo.sos_scanDataLength
					);

				// update frame number
				incrRtpAndNtpTsFrameNr();
			} catch (InputStreamEofException e) {
				cacheFrameData.haveErrorEof = true;
				cacheFrameData.errorMsg = FNC_NAME + ": InputStreamEofException caught where it should not occur";
			}
		}

		return cacheFrameData;
	}

	@Override
	protected Boolean cbRtpPacketMarkerBitSupplier(int currentOffsetInFramePlusFragmentSize, int framePayloadSize) {
		return (currentOffsetInFramePlusFragmentSize == framePayloadSize);
	}

	@Override
	protected RtpPacketPayloadInterface cbRtpPacketPayloadSupplier(FrameFragmentData curFragmentData) {
		cacheRtpInnerPayloadBuf.copyOf(
				curFragmentData.frameData().rtpPayloadData,
				curFragmentData.fragmentOffset(),
				curFragmentData.fragmentSize()
			);

		return new RtpPacketPayloadMjpeg(
				curFragmentData.fragmentOffset(),
				curFrameJpegInfo,
				cacheRtpInnerPayloadBuf
			);
	}

}
