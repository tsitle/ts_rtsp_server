package org.tsitle.rtsp.threads.rtp.codec_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketMjpeg;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvMjpeg;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.JpegInfo;
import org.tsitle.rtsp.exceptions.*;

import java.util.Objects;

public final class ThreadRtpSenderMjpeg extends ThreadRtpSenderBase<JpegInfo, ThreadDataProvMjpeg> {

	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;

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
		this.rtpTicksPerFrame = (long)((double)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG) /
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

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvMjpeg newThreadDataProv() {
		return new ThreadDataProvMjpeg(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsVideoCommon,
				(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
				paramsCommon.getDebugRewindMediaFiles()
			);
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
	protected @NonNull FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		//
		cacheFrameData.rtpFrameTimestamp = getRtpTimestampAsInt();

		if (threadDataProv == null || ! threadDataProv.isRunning()) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": DataProvider thread not running";
		} else if (threadDataProv.haveEof()) {
			cacheFrameData.haveErrorEof = true;
			cacheFrameData.errorMsg = FNC_NAME + ": InputStreamEofException caught";
		} else {
			// get the next frame to send over the wire from the input stream
			try {
				threadDataProv.getNextFrame(cacheOrgVideoFrameBuf, curFrameJpegInfo);

				//
				cacheFrameData.totalFrameSize = cacheOrgVideoFrameBuf.getUsed();

				// extract the actual RTP/JPEG payload
				cacheFrameData.rtpPayloadData.copyOf(
						cacheOrgVideoFrameBuf,
						curFrameJpegInfo.sos_scanDataOffs,
						curFrameJpegInfo.sos_scanDataLength
					);

				// update frame number
				incrRtpTsFrameNr();
			} catch (InputStreamEofException e) {
				cacheFrameData.haveErrorEof = true;
				cacheFrameData.errorMsg = FNC_NAME + ": EOF";
			}
		}

		return cacheFrameData;
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(boolean isLastFragment) {
		return isLastFragment;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		return new RtpPacketMjpeg(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFrameJpegInfo,
				cacheRtpInnerPayloadBuf
			);
	}

}
