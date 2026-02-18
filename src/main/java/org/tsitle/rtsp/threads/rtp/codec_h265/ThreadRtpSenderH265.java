package org.tsitle.rtsp.threads.rtp.codec_h265;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketH265;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH265;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.H265Info;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.exceptions.AvInvalidH265DataException;

import java.util.Objects;
import java.util.Optional;

public final class ThreadRtpSenderH265 extends ThreadRtpSenderBase {

	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;
	private @Nullable ThreadDataProvH265 threadDataProv;

	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();
	private final H265Info cacheH265Info = new H265Info();

	private int readNalUnitsCounter = 0;
	private final HevcAccessUnit globalTempAu = new HevcAccessUnit("TEMP");
	private final HevcAccessUnit globalCurAu = new HevcAccessUnit("CUR");
	private final HevcAccessUnit globalNextAu = new HevcAccessUnit("NEXT");
	private HevcNalUnitData globalCurNudPtr = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH265 Thread-specific parameters
	 */
	public ThreadRtpSenderH265(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH265 paramsH265
			) {
		super(
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H265),
				RtpPacketType.V_H265
			);

		//
		this.rtpTicksPerFrame = (long)((double)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H265) /
				Objects.requireNonNull(paramsCommon).getAvFramesPerSecond());

		//
		paramsVideoCommon.validate();
		this.paramsVideoCommon = paramsVideoCommon.clone();
		paramsH265.validate();
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
		threadDataProv = new ThreadDataProvH265(
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
	protected @NonNull FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		if (peekCurLastNalUnitData().isEmpty()) {
			/*
			 * Try to grab an entire Access Unit from the video stream.
			 * Stores the result in globalCurAu.
			 */
			try {
				frameDataSupplierGrabAccessUnit();
			} catch (InputStreamIoException | AvInvalidH265DataException ex) {
				cacheFrameData.haveErrorOther = true;
				cacheFrameData.errorMsg = FNC_NAME + ": " + ex;
				return cacheFrameData;
			}
		}

		// get a pointer to the current NAL Unit and move the pointer to the next NAL Unit
		Optional<HevcNalUnitData> optCurNudPtr = popCurNalUnitData();
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
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(boolean isLastFragment) {
		return (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount && isLastFragment);
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		if (globalCurNudPtr == null) {
			throw new IllegalStateException(FNC_NAME + ": globalCurNudPtr == null");
		}
		prepareRtpPacketDataForFragment(curFragmentData);
		return new RtpPacketH265(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h265Info,
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
		threadDataProv.getNextFrame(cacheOrgVideoFrameBuf, cacheH265Info);

		//
		if (globalTempAu.arrNalUnitCount == globalTempAu.arrNalUnitData.size()) {
			resizeArrayNalUnitData(globalTempAu, globalTempAu.arrNalUnitCount + 1);
		}
		HevcNalUnitData tmpLocalNudPtr = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount++);

		//
		tmpLocalNudPtr.internalId = ++readNalUnitsCounter;
		tmpLocalNudPtr.fullDataSize = cacheOrgVideoFrameBuf.getUsed();

		//
		tmpLocalNudPtr.h265Info = cacheH265Info.clone();

		// extract the actual RTP/H265 payload
		tmpLocalNudPtr.rtpPayloadData.copyOf(
				cacheOrgVideoFrameBuf,
				tmpLocalNudPtr.h265Info.nalUnitOffset,
				tmpLocalNudPtr.h265Info.nalUnitLength
			);
	}

	private void frameDataSupplierGrabAccessUnit() throws InputStreamIoException, AvInvalidH265DataException {
		final String FNC_NAME = getClass().getSimpleName() + ".frameDataSupplierGrabAccessUnit()";

		globalCurAu.reset();

		//
		moveNextAuToTempAu();

		//
		globalCurAu.auTimestamp = getRtpTimestampAsInt();  // only changes after increasing the 'Real Frame Number'
		globalCurAu.isAuTimestampSet = true;

		boolean haveEof = false;
		boolean haveAuStartVcl = globalTempAu.arrNalUnitData.stream()
				.filter(nud -> nud.h265Info != null)
				.anyMatch(nud -> nud.h265Info.isVclFirstSliceSegmentInPic);

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
			HevcNalUnitData tmpNud = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount - 1);
			if (tmpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h265Info == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud.h265Info == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
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

	private static void appendSrcAuToDestAu(HevcAccessUnit srcAu, HevcAccessUnit destAu, int srcStartIx) {
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

	private static void resizeArrayNalUnitData(HevcAccessUnit au, int newSize) {
		if (newSize > au.arrNalUnitData.size()) {
			for (int ix = au.arrNalUnitData.size(); ix < newSize; ix++) {
				au.arrNalUnitData.add(new HevcNalUnitData());
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
			HevcNalUnitData tmpNud = globalTempAu.arrNalUnitData.get(ix);
			if (tmpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud == null at index " + ix);
			}
			if (tmpNud.h265Info == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud.h265Info == null at index " + ix);
			}

			boolean stopLoop = false;
			switch (state) {
				case AuState.SEEKING_AU_START:
					if (tmpNud.h265Info.isVclNalUnit && tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
						state = AuState.IN_AU_VCL;
					} else if (isLeadingNonVcl(tmpNud.h265Info)) {
						state = AuState.IN_AU_PREFIX;
					}
					// add to globalCurAu
					break;
				case AuState.IN_AU_PREFIX:
					if (tmpNud.h265Info.isVclNalUnit && tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
						// add to globalCurAu
						state = AuState.IN_AU_VCL;
					} else if (! isLeadingNonVcl(tmpNud.h265Info)) {
						// This NAL doesn't belong to the current AU
						stopLoop = true;
					} /*else {
						// add to globalCurAu
					}*/
					break;
				case AuState.IN_AU_VCL:
					if (tmpNud.h265Info.isVclNalUnit) {
						if (tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
							// Start of new picture/AU
							stopLoop = true;
						} /*else {
							// More slices of the current picture
							// add to globalCurAu
						}*/
					} else if (isTrailingNonVcl(tmpNud.h265Info)) {
						// add to globalCurAu
						state = AuState.IN_AU_SUFFIX;
					} else {
						// Leading NAL of next AU
						stopLoop = true;
					}
					break;
				case AuState.IN_AU_SUFFIX:
					if (! isTrailingNonVcl(tmpNud.h265Info)) {
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

	private static boolean isLeadingNonVcl(H265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_VPS, NVCL_SPS, NVCL_PPS, NVCL_AUD, NVCL_SEI_PREFIX -> true;
				default -> false;
			};
	}

	private static boolean isTrailingNonVcl(H265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_SEI_SUFFIX, NVCL_FD, NVCL_EOS, NVCL_EOB -> true;
				default -> false;
			};
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings("unused")
	private void debugPrintAu(HevcAccessUnit au) {
		final String FNC_NAME = ThreadRtpSenderH265.class.getSimpleName() + ".debugPrintAu()";

		logDebug(FNC_NAME, "--");
		logDebug(FNC_NAME, au.toString());
		for (int ix = 0; ix < au.arrNalUnitCount; ix++) {
			HevcNalUnitData nud = au.arrNalUnitData.get(ix);
			logDebug(FNC_NAME, "    " + nud);
		}
		logDebug(FNC_NAME, "--");
	}

	private Optional<HevcNalUnitData> popCurNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitIx++));
	}

	private Optional<HevcNalUnitData> peekCurLastNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitCount - 1));
	}

}
