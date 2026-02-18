package org.tsitle.rtsp.threads.rtp.codec_h264;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH264DataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadH264;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH264;
import org.tsitle.rtsp.threads.rtp.FrameData;
import org.tsitle.rtsp.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH264;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.util.Objects;
import java.util.Optional;

public final class ThreadRtpSenderH264 extends ThreadRtpSenderBase {

	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;
	private @Nullable ThreadDataProvH264 threadDataProv;

	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();
	private final H264Info cacheH264Info = new H264Info();

	private int readNalUnitsCounter = 0;
	private final H264AccessUnit globalTempAu = new H264AccessUnit("TEMP");
	private final H264AccessUnit globalCurAu = new H264AccessUnit("CUR");
	private final H264AccessUnit globalNextAu = new H264AccessUnit("NEXT");
	private H264NalUnitData globalCurNudPtr = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH264 Thread-specific parameters
	 */
	public ThreadRtpSenderH264(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH264 paramsH264
			) {
		super(
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H264),
				RtpPacketType.V_H264
			);

		//
		this.rtpTicksPerFrame = (long)((double)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H264) /
				Objects.requireNonNull(paramsCommon).getAvFramesPerSecond());

		//
		paramsVideoCommon.validate();
		paramsH264.validate();

		//
		this.paramsVideoCommon = paramsVideoCommon.clone();
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
		threadDataProv = new ThreadDataProvH264(
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

		if (peekCurLastNalUnitData().isEmpty()) {
			/*
			 * Try to grab an entire Access Unit from the video stream.
			 * Stores the result in globalCurAu.
			 */
			try {
				frameDataSupplierGrabAccessUnit();
			} catch (InputStreamIoException | AvInvalidH264DataException ex) {
				cacheFrameData.haveErrorOther = true;
				cacheFrameData.errorMsg = FNC_NAME + ": " + ex;
				return cacheFrameData;
			}
		}

		// get a pointer to the current NAL Unit and move the pointer to the next NAL Unit
		Optional<H264NalUnitData> optCurNudPtr = popCurNalUnitData();
		if (optCurNudPtr.isEmpty()) {
			globalCurNudPtr = null;
			//
			cacheFrameData.haveErrorEof = true;
			cacheFrameData.errorMsg = FNC_NAME + ": EOF";
			return cacheFrameData;
		}
		globalCurNudPtr = optCurNudPtr.get();

		//
		cacheFrameData.rtpFrameTimestamp = globalCurAu.auTimestamp;
		cacheFrameData.totalFrameSize = globalCurNudPtr.fullDataSize;
		cacheFrameData.rtpPayloadData.copyOf(globalCurNudPtr.rtpPayloadData);

		return cacheFrameData;
	}

	@Override
	protected Boolean cbRtpPacketMarkerBitSupplier(int currentOffsetInFramePlusFragmentSize, int framePayloadSize) {
		return (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount &&
				currentOffsetInFramePlusFragmentSize == framePayloadSize);
	}

	@Override
	protected RtpPacketPayloadInterface cbRtpPacketPayloadSupplier(FrameFragmentData curFragmentData) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		if (globalCurNudPtr == null) {
			throw new IllegalStateException(FNC_NAME + ": globalCurNudPtr == null");
		}
		cacheRtpInnerPayloadBuf.copyOf(
				curFragmentData.frameData().rtpPayloadData,
				curFragmentData.fragmentOffset(),
				curFragmentData.fragmentSize()
			);

		return new RtpPacketPayloadH264(
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h264Info,
				cacheRtpInnerPayloadBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void frameDataSupplierGrabNalUnit() throws InputStreamEofException {
		if (threadDataProv == null || ! threadDataProv.isRunning()) {
			throw new InputStreamEofException();
		}

		// get the next frame to send over the wire from the input stream
		threadDataProv.getNextFrame(cacheOrgVideoFrameBuf, cacheH264Info);

		//
		if (globalTempAu.arrNalUnitCount == globalTempAu.arrNalUnitData.size()) {
			resizeArrayNalUnitData(globalTempAu, globalTempAu.arrNalUnitCount + 1);
		}
		H264NalUnitData tmpLocalNudPtr = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount++);

		//
		tmpLocalNudPtr.internalId = ++readNalUnitsCounter;
		tmpLocalNudPtr.fullDataSize = cacheOrgVideoFrameBuf.getUsed();

		//
		tmpLocalNudPtr.h264Info = cacheH264Info.clone();

		// extract the actual RTP/H264 payload
		tmpLocalNudPtr.rtpPayloadData.copyOf(
				cacheOrgVideoFrameBuf,
				tmpLocalNudPtr.h264Info.nalUnitOffset,
				tmpLocalNudPtr.h264Info.nalUnitLength
			);
	}

	private void frameDataSupplierGrabAccessUnit() throws InputStreamIoException, AvInvalidH264DataException {
		final String FNC_NAME = getClass().getSimpleName() + ".frameDataSupplierGrabAccessUnit()";

		globalCurAu.reset();

		//
		moveNextAuToTempAu();

		//
		globalCurAu.auTimestamp = getRtpTimestampAsInt();  // only changes after increasing the 'Real Frame Number'
		globalCurAu.isAuTimestampSet = true;

		boolean haveEof = false;
		boolean haveAuStartVcl = globalTempAu.arrNalUnitData.stream()
				.filter(nud -> nud.h264Info != null)
				.anyMatch(nud -> nud.h264Info.isVclFirstSliceSegmentInPic);

		while (threadDataProv != null && ! threadDataProv.haveEof() && ! haveEof) {
			/*
			 * Try to grab the next NAL Unit from the video stream.
			 * Stores the result in globalTempAu.
			 */
			try {
				frameDataSupplierGrabNalUnit();
			} catch (InputStreamEofException ex) {
				haveEof = true;
				continue;
			}

			//
			H264NalUnitData tmpNud = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount - 1);
			if (tmpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h264Info == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud.h264Info == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h264Info.isVclFirstSliceSegmentInPic) {
				// we have found the start of a new Access Unit - not counting any previous non-VCL NAL Units
				if (haveAuStartVcl) {
					// stop acquiring NAL Units
					break;
				}
				haveAuStartVcl = true;
			}
		}

		//
		moveTempAuToCurAu();
		appendSrcAuToDestAu(globalTempAu, globalNextAu, globalTempAu.arrNalUnitIx);

		// update frame number after having received a new Access Unit
		if (globalCurAu.arrNalUnitCount > 0) {
			incrRtpTsFrameNr();
		}
	}

	private void moveNextAuToTempAu() {
		globalTempAu.reset();

		appendSrcAuToDestAu(globalNextAu, globalTempAu, 0);
	}

	private static void appendSrcAuToDestAu(H264AccessUnit srcAu, H264AccessUnit destAu, int srcStartIx) {
		if (srcStartIx >= srcAu.arrNalUnitCount ) {
			srcAu.reset();
			return;
		}

		if (srcAu.arrNalUnitCount - srcStartIx > 0) {
			int tmpDstIx = destAu.arrNalUnitCount;
			destAu.arrNalUnitCount += srcAu.arrNalUnitCount - srcStartIx;
			resizeArrayNalUnitData(destAu, destAu.arrNalUnitCount);
			for (int tmpSrcIx = srcStartIx; tmpSrcIx < srcAu.arrNalUnitCount; tmpSrcIx++) {
				destAu.arrNalUnitData.get(tmpDstIx++).moveDataFrom(
						srcAu.arrNalUnitData.get(tmpSrcIx)
					);
			}
		}
		srcAu.reset();
	}

	private static void resizeArrayNalUnitData(H264AccessUnit au, int newSize) {
		if (newSize > au.arrNalUnitData.size()) {
			for (int ix = au.arrNalUnitData.size(); ix < newSize; ix++) {
				au.arrNalUnitData.add(new H264NalUnitData());
			}
		}
	}

	private enum AuState {
		SEEKING_AU_START,  // Looking for the start of a new AU
		IN_AU_PREFIX,      // Processing leading non-VCL NALs
		IN_AU_VCL,         // Processing VCL NALs of current picture
		IN_AU_SUFFIX       // Processing trailing non-VCL NALs
	}

	private void moveTempAuToCurAu() {
		final String FNC_NAME = getClass().getSimpleName() + ".moveTempAuToCurAu()";

		/*
		 * We should now have at least one VCL NAL Unit and optional non-VCL NAL Units in globalTempAu.
		 * Next, we need to move all NAL Units that belong to the
		 *   - current Access Unit to globalCurAu
		 *   - next Access Unit to globalNextAu
		 */
		AuState state = AuState.SEEKING_AU_START;
		for (int ix = 0; ix < globalTempAu.arrNalUnitCount; ix++) {
			H264NalUnitData tmpNud = globalTempAu.arrNalUnitData.get(ix);
			if (tmpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud == null at index " + ix);
			}
			if (tmpNud.h264Info == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud.h264Info == null at index " + ix);
			}

			boolean stopLoop = false;
			switch (state) {
				case AuState.SEEKING_AU_START:
					if (tmpNud.h264Info.isVclNalUnit && tmpNud.h264Info.isVclFirstSliceSegmentInPic) {
						state = AuState.IN_AU_VCL;
					} else if (isLeadingNonVcl(tmpNud.h264Info)) {
						state = AuState.IN_AU_PREFIX;
					}
					// add to globalCurAu
					break;
				case AuState.IN_AU_PREFIX:
					if (tmpNud.h264Info.isVclNalUnit && tmpNud.h264Info.isVclFirstSliceSegmentInPic) {
						// add to globalCurAu
						state = AuState.IN_AU_VCL;
					} else if (! isLeadingNonVcl(tmpNud.h264Info)) {
						// This NAL doesn't belong to the current AU
						stopLoop = true;
					} /*else {
						// add to globalCurAu
					}*/
					break;
				case AuState.IN_AU_VCL:
					if (tmpNud.h264Info.isVclNalUnit) {
						if (tmpNud.h264Info.isVclFirstSliceSegmentInPic) {
							// Start of new picture/AU
							stopLoop = true;
						} /*else {
							// More slices of the current picture
							// add to globalCurAu
						}*/
					} else if (isTrailingNonVcl(tmpNud.h264Info)) {
						// add to globalCurAu
						state = AuState.IN_AU_SUFFIX;
					} else {
						// Leading NAL of next AU
						stopLoop = true;
					}
					break;
				case AuState.IN_AU_SUFFIX:
					if (! isTrailingNonVcl(tmpNud.h264Info)) {
						// This starts a new AU
						stopLoop = true;
					} /*else {
						// add to globalCurAu
					}*/
					break;
				default:
					throw new IllegalStateException(FNC_NAME + ": unexpected state=" + state);
			}

			if (stopLoop) {
				break;
			}
			resizeArrayNalUnitData(globalCurAu, globalCurAu.arrNalUnitCount + 1);
			globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitCount++).moveDataFrom(tmpNud);
			++globalTempAu.arrNalUnitIx;
		}

		//debugPrintAu(globalCurAu);
	}

	private static boolean isLeadingNonVcl(H264Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case H264Info.NalUnitType.NVCL_SPS, H264Info.NalUnitType.NVCL_PPS,
						H264Info.NalUnitType.NVCL_AUD, H264Info.NalUnitType.NVCL_SEI -> true;
				default -> false;
			};
	}

	private static boolean isTrailingNonVcl(H264Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case H264Info.NalUnitType.NVCL_EOS, H264Info.NalUnitType.NVCL_EOB, H264Info.NalUnitType.NVCL_FD -> true;
				default -> false;
			};
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings("unused")
	private void debugPrintAu(H264AccessUnit au) {
		final String FNC_NAME = ThreadRtpSenderH264.class.getSimpleName() + ".debugPrintAu()";

		logDebug(FNC_NAME, "--");
		logDebug(FNC_NAME, au.toString());
		for (int ix = 0; ix < au.arrNalUnitCount; ix++) {
			H264NalUnitData nud = au.arrNalUnitData.get(ix);
			logDebug(FNC_NAME, "    " + nud);
		}
		logDebug(FNC_NAME, "--");
	}

	private Optional<H264NalUnitData> popCurNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitIx++));
	}

	private Optional<H264NalUnitData> peekCurLastNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitCount - 1));
	}

}
