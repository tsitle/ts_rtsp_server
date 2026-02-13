package org.tsitle.rtsp.threads.rtp.h265;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.H265Info;
import org.tsitle.rtsp.avdata.H265Parser;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.exceptions.AvInvalidH265DataException;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadH265;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.avinputstreams.VideoStreamH265;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;

public final class ThreadRtpSenderH265 extends ThreadRtpSenderBase {

	/** VideoStream object used to access video frames */
	private final VideoStreamH265 videoStream;

	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();

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
	 * @throws FileNotFoundException If the video file cannot be opened
	 */
	public ThreadRtpSenderH265(
				ParamsThreadRtpSenderCommon paramsCommon,
				ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				ParamsThreadRtpSenderH265 paramsH265
			) throws FileNotFoundException {
		super(
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H265),
				(long)((float)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_H265) /
						Objects.requireNonNull(paramsCommon).getAvFramesPerSecond()),
				RtpPacketType.V_H265
			);

		//
		if (paramsVideoCommon == null) {
			throw new IllegalArgumentException("Thread parameters cannot be null");
		}
		paramsVideoCommon.validate();
		if (paramsH265 == null) {
			throw new IllegalArgumentException("Thread parameters cannot be null");
		}
		paramsH265.validate();
		//
		this.videoStream = new VideoStreamH265(paramsVideoCommon.getVideoFilePath().orElseThrow());
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
			throw new IllegalStateException(FNC_NAME + ": globalCurNudPtr == null");
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

		return new RtpPacketPayloadH265(
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h265Info,
				cacheRtpInnerPayloadBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void frameDataSupplierGrabNalUnit()
			throws InputStreamEofException, InputStreamIoException, AvInvalidH265DataException {
		// get the next frame to send from the video, as well as its size
		videoStream.getNextFrame(cacheOrgVideoFrameBuf);
		if (cacheOrgVideoFrameBuf.getUsed() < videoStream.getMagicBytesLength() +
				H265Parser.NAL_UNIT_HEADER_SIZE) {
			// we have reached the end of the video file
			throw new InputStreamEofException();
		}

		//
		if (globalTempAu.arrNalUnitCount == globalTempAu.arrNalUnitData.size()) {
			resizeArrayNalUnitData(globalTempAu, globalTempAu.arrNalUnitCount + 1);
		}
		HevcNalUnitData tmpLocalNudPtr = globalTempAu.arrNalUnitData.get(globalTempAu.arrNalUnitCount++);

		//
		tmpLocalNudPtr.internalId = ++readNalUnitsCounter;
		tmpLocalNudPtr.fullDataSize = cacheOrgVideoFrameBuf.getUsed();

		//
		tmpLocalNudPtr.h265Info = H265Parser.parseH265Data(
				debugStreamOffset,
				videoStream.getMagicBytesLength(),
				cacheOrgVideoFrameBuf
			);

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

		while (videoStream.hasMoreFrames() && ! haveEof) {
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

		if (! (haveEof || videoStream.hasMoreFrames()) && paramsCommon.getDebugRewindMediaFiles()) {
			System.out.println(FNC_NAME + ": haveEof, rewinding");
			videoStream.rewind();
		}

		//
		moveTempAuToCurAu();
		appendSrcAuToDestAu(globalTempAu, globalNextAu, globalTempAu.arrNalUnitIx);

		// update frame number after having received a new Access Unit
		if (globalCurAu.arrNalUnitCount > 0) {
			incrRtpAndNtpTsFrameNr();
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

	private void moveTempAuToCurAu() {
		final String FNC_NAME = getClass().getSimpleName() + ".moveTempAuToCurAu()";

		/*
		 * We should now have at least one VCL NAL Unit and optional non-VCL NAL Units in globalTempAu.
		 * Next, we need to move all NAL Units that belong to the
		 *   - current Access Unit to globalCurAu
		 *   - next Access Unit to globalNextAu
		 */
		int state = 0;
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
				case 0:
					if (tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
						state = 1;
					}
					break;
				case 1:  // haveAuStartVcl
					if (tmpNud.h265Info.isVclFirstSliceSegmentInPic) {
						stopLoop = true;
					} else if (! tmpNud.h265Info.isVclNalUnit) {
						switch (tmpNud.h265Info.nalUnitTypeEn) {
							case H265Info.NalUnitType.NVCL_EOS,
									H265Info.NalUnitType.NVCL_EOB,
									H265Info.NalUnitType.NVCL_FD,
									H265Info.NalUnitType.NVCL_SEI_SUFFIX:
								// add to globalCurAu
								break;
							default:
								stopLoop = true;
						}
					}
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
	}

	@SuppressWarnings("unused")
	private static void debugPrintAu(HevcAccessUnit au) {
		final String FNC_NAME = ThreadRtpSenderH265.class.getSimpleName() + ".debugPrintAu()";

		System.out.println();
		System.out.println(FNC_NAME + ": " + au);
		for (int ix = 0; ix < au.arrNalUnitCount; ix++) {
			HevcNalUnitData nud = au.arrNalUnitData.get(ix);
			System.out.println(FNC_NAME + ":   " + nud);
		}
		System.out.println();
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
