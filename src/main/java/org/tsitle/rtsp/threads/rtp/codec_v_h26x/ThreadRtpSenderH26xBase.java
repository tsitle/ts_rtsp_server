package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoH26xBase;
import org.tsitle.rtsp.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.rtp.FrameData;
import org.tsitle.rtsp.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.util.Objects;
import java.util.Optional;

public abstract class ThreadRtpSenderH26xBase<
			I extends CodecInfoH26xBase<I>,
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>,
			TDP extends ThreadDataProvBase<I, AVSTROG>
		> extends ThreadRtpSenderBase<I, AVSTRIC, AVSTROG, TDP> {

	protected final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;

	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();
	protected I cacheH26xInfo;

	private int readNalUnitsCounter = 0;
	private final H26xAccessUnit<I> globalTempAu = new H26xAccessUnit<>("TEMP");
	private final H26xAccessUnit<I> globalCurAu = new H26xAccessUnit<>("CUR");
	private final H26xAccessUnit<I> globalNextAu = new H26xAccessUnit<>("NEXT");
	protected H26xNalUnitData<I> globalCurNudPtr = null;

	private final RtpH26xPayloadBuffer rtpPayloadBufObj = new RtpH26xPayloadBuffer();

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param rtpPacketType RTP packet type
	 */
	protected ThreadRtpSenderH26xBase(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull RtpPacketType rtpPacketType
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
				paramsCommon,
				rtpPacketType.getVideoCodecRtpClockrate(),
				rtpPacketType
			);

		//
		if (rtpPacketType != RtpPacketType.V_H264 && rtpPacketType != RtpPacketType.V_H265) {
			throw new IllegalArgumentException("invalid rtpPacketType");
		}

		//
		this.rtpTicksPerFrame = (long)((double)rtpPacketType.getVideoCodecRtpClockrate() /
				Objects.requireNonNull(paramsCommon).getAvFramesPerSecond());

		//
		paramsVideoCommon.validate();
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
	protected void beforeRunHook() throws InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".beforeRunHook()";

		super.beforeRunHook();
		//
		try {
			frameDataSupplierGrabAccessUnit();  // puts frames in CUR and NEXT
			//
			appendSrcAuToDestAu(globalNextAu, globalTempAu, globalNextAu.arrNalUnitIx);
			appendSrcAuToDestAu(globalCurAu, globalNextAu, globalCurAu.arrNalUnitIx);
			appendSrcAuToDestAu(globalTempAu, globalNextAu, globalTempAu.arrNalUnitIx);
			//
			resetRtpTsFrameNr();
		} catch (InputStreamIoException | AvInvalidCodecDataException e) {
			logError(FNC_NAME, e.toString());
		}
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
			} catch (InputStreamIoException | AvInvalidCodecDataException e) {
				cacheFrameData.haveErrorOther = true;
				cacheFrameData.errorMsg = FNC_NAME + ": " + e;
				return cacheFrameData;
			}
		}

		// get a pointer to the current NAL Unit and move the pointer to the next NAL Unit
		Optional<H26xNalUnitData<I>> optCurNudPtr = popCurNalUnitData();
		if (optCurNudPtr.isEmpty()) {
			globalCurNudPtr = null;
			//
			cacheFrameData.haveErrorEos = true;
			cacheFrameData.errorMsg = FNC_NAME + ": EOS reached";
			return cacheFrameData;
		}
		globalCurNudPtr = optCurNudPtr.get();

		//
		///
		cacheFrameData.rtpFrameNr = globalCurNudPtr.rtpFrameNr;
		cacheFrameData.totalFrameSize = globalCurNudPtr.fullDataSize;
		cacheFrameData.rtpPayloadDataPtr = globalCurNudPtr.rtpPayloadDataPtr;
		//noinspection DataFlowIssue
		rtpPayloadBufObj.markForDiscard(globalCurNudPtr.rtpPayloadDataPtr);
		///
		cacheFrameData.totalAuRtpPayloadSz = globalCurAu.totalRtpPayloadSize;
		if (globalCurNudPtr.h26xInfo != null) {
			cacheFrameData.frameDesc = String.format("NAL Unit Type 0x%02X", globalCurNudPtr.h26xInfo.nalUnitTypeBy);
		}

		return cacheFrameData;
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		return (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount && isLastFragment);
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract boolean isNalUnitNonVclSei(I nalInfo);

	protected abstract boolean isNalUnitLeadingNonVcl(I nalInfo);

	protected abstract boolean isNalUnitTrailingNonVcl(I nalInfo);

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract I getCloneOfH26xInfo(@NonNull I src);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void frameDataSupplierGrabNalUnit() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".frameDataSupplierGrabNalUnit()";

		int timeoutCnt = 0;
		while (threadDataProv != null && ! threadDataProv.isRunning() && timeoutCnt++ < 250) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				throw new InputStreamEosException();
			}
		}
		if (threadDataProv == null) {
			throw new InputStreamEosException();
		}
		if (! threadDataProv.isRunning()) {
			logError(FNC_NAME, "threadDataProv is not running");
			throw new InputStreamEosException();
		}
		if (cacheH26xInfo == null) {
			throw new IllegalStateException("cacheH26xInfo == null");
		}

		// get the next frame to send over the wire from the input stream
		threadDataProv.getNextFrame(cacheOrgVideoFrameBuf, cacheH26xInfo);
		if (cacheOrgVideoFrameBuf.isEmpty()) {
			logWarn(FNC_NAME, "cacheOrgVideoFrameBuf isEmpty");
			throw new InputStreamEosException();
		}

		//
		if (globalTempAu.arrNalUnitCount == globalTempAu.arrNalUnitData.size()) {
			resizeArrayNalUnitData(globalTempAu, globalTempAu.arrNalUnitCount + 1);
		}
		H26xNalUnitData<I> tmpLocalNudPtr = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount++);

		//
		tmpLocalNudPtr.internalId = ++readNalUnitsCounter;
		tmpLocalNudPtr.fullDataSize = cacheOrgVideoFrameBuf.getUsed();

		//
		tmpLocalNudPtr.h26xInfo = getCloneOfH26xInfo(cacheH26xInfo);

		// extract the actual RTP/H26x payload
		BufferExt tmpBufPtr = rtpPayloadBufObj.getBufferObjPtr();
		tmpBufPtr.copyOf(
				cacheOrgVideoFrameBuf,
				Objects.requireNonNull(tmpLocalNudPtr.h26xInfo).nalUnitOffset,
				Objects.requireNonNull(tmpLocalNudPtr.h26xInfo).nalUnitLength
			);
		tmpLocalNudPtr.rtpPayloadDataPtr = tmpBufPtr;
	}

	private void frameDataSupplierGrabAccessUnit() throws InputStreamIoException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".frameDataSupplierGrabAccessUnit()";

		globalCurAu.reset();

		//
		moveNextAuToTempAu();

		//
		boolean haveAuStartVcl = globalTempAu.arrNalUnitData.stream()
				.filter(nud -> nud.h26xInfo != null)
				.anyMatch(nud -> nud.h26xInfo.isVclFirstSliceSegmentInPic);

		while (threadDataProv != null && ! threadDataProv.haveEos()) {
			/*
			 * Try to grab the next NAL Unit from the video stream.
			 * Stores the result in globalTempAu.
			 */
			try {
				frameDataSupplierGrabNalUnit();
			} catch (InputStreamEosException e) {
				if (threadDataProv != null && threadDataProv.isRunning()) {
					logWarn(FNC_NAME, "EOS reached");
				}
				break;
			}

			//
			H26xNalUnitData<I> tmpNud = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount - 1);
			if (tmpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h26xInfo == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpNud.h26xInfo == null at index " + (globalTempAu.arrNalUnitCount - 1));
			}
			if (tmpNud.h26xInfo.isVclFirstSliceSegmentInPic) {
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

	private static <I extends CodecInfoH26xBase<I>> void appendSrcAuToDestAu(
				H26xAccessUnit<I> srcAu,
				H26xAccessUnit<I> destAu,
				int srcStartIx
			) {
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

	private static <I extends CodecInfoH26xBase<I>> void resizeArrayNalUnitData(
				H26xAccessUnit<I> au,
				int newSize
			) {
		if (newSize > au.arrNalUnitData.size()) {
			for (int ix = au.arrNalUnitData.size(); ix < newSize; ix++) {
				au.arrNalUnitData.add(new H26xNalUnitData<>());
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

		final long rtpFrameNr = getRtpTsFrameNr();

		/*
		 * We should now have at least one VCL NAL Unit and optional non-VCL NAL Units in globalTempAu.
		 * Next, we need to move all NAL Units that belong to the
		 *   - current Access Unit to globalCurAu
		 *   - next Access Unit to globalNextAu
		 */
		AuState state = AuState.SEEKING_AU_START;
		for (int ix = 0; ix < globalTempAu.arrNalUnitCount; ix++) {
			H26xNalUnitData<I> tmpInpNud = globalTempAu.arrNalUnitData.get(ix);
			if (tmpInpNud == null) {
				throw new IllegalStateException(FNC_NAME + ": tmpInpNud == null at index " + ix);
			}
			if (tmpInpNud.h26xInfo == null) {
				continue;
			}

			// filter out SEI NAL Units
			if (isNalUnitNonVclSei(tmpInpNud.h26xInfo)) {
				tmpInpNud.reset();
				continue;
			}

			//
			boolean stopLoop = false;
			switch (state) {
				case AuState.SEEKING_AU_START:
					if (tmpInpNud.h26xInfo.isVclNalUnit && tmpInpNud.h26xInfo.isVclFirstSliceSegmentInPic) {
						state = AuState.IN_AU_VCL;
					} else if (isNalUnitLeadingNonVcl(tmpInpNud.h26xInfo)) {
						state = AuState.IN_AU_PREFIX;
					}
					// add to globalCurAu
					break;
				case AuState.IN_AU_PREFIX:
					if (tmpInpNud.h26xInfo.isVclNalUnit && tmpInpNud.h26xInfo.isVclFirstSliceSegmentInPic) {
						// add to globalCurAu
						state = AuState.IN_AU_VCL;
					} else if (! isNalUnitLeadingNonVcl(tmpInpNud.h26xInfo)) {
						// This NAL doesn't belong to the current AU
						stopLoop = true;
					} /*else {
						// add to globalCurAu
					}*/
					break;
				case AuState.IN_AU_VCL:
					if (tmpInpNud.h26xInfo.isVclNalUnit) {
						if (tmpInpNud.h26xInfo.isVclFirstSliceSegmentInPic) {
							// Start of new picture/AU
							stopLoop = true;
						} /*else {
							// More slices of the current picture
							// add to globalCurAu
						}*/
					} else if (isNalUnitTrailingNonVcl(tmpInpNud.h26xInfo)) {
						// add to globalCurAu
						state = AuState.IN_AU_SUFFIX;
					} else {
						// Leading NAL of next AU
						stopLoop = true;
					}
					break;
				case AuState.IN_AU_SUFFIX:
					if (! isNalUnitTrailingNonVcl(tmpInpNud.h26xInfo)) {
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
			H26xNalUnitData<I> tmpOutputNud = globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitCount);
			tmpOutputNud.moveDataFrom(tmpInpNud);
			tmpOutputNud.rtpFrameNr = rtpFrameNr;
			globalCurAu.totalRtpPayloadSize += (tmpOutputNud.rtpPayloadDataPtr == null ? 0 :
					tmpOutputNud.rtpPayloadDataPtr.getUsed());
			++globalCurAu.arrNalUnitCount;
			++globalTempAu.arrNalUnitIx;
		}

		//debugPrintAu(globalCurAu);
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings("unused")
	private void debugPrintAu(H26xAccessUnit<I> au) {
		final String FNC_NAME = ThreadRtpSenderH26xBase.class.getSimpleName() + ".debugPrintAu()";

		logDebug(FNC_NAME, "--");
		logDebug(FNC_NAME, au.toString());
		for (int ix = 0; ix < au.arrNalUnitCount; ix++) {
			H26xNalUnitData<I> nud = au.arrNalUnitData.get(ix);
			logDebug(FNC_NAME, "    " + nud);
		}
		logDebug(FNC_NAME, "--");
	}

	private Optional<H26xNalUnitData<I>> popCurNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitIx++));
	}

	private Optional<H26xNalUnitData<I>> peekCurLastNalUnitData() {
		if (globalCurAu.arrNalUnitIx == globalCurAu.arrNalUnitCount) {
			return Optional.empty();
		}
		return Optional.of(globalCurAu.arrNalUnitData.get(globalCurAu.arrNalUnitCount - 1));
	}

}
