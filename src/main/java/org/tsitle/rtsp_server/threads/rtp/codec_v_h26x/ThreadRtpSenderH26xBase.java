package org.tsitle.rtsp_server.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoH26xBase;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp_server.avstreams.AvStreamOutgoingBase;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.util.*;

public abstract class ThreadRtpSenderH26xBase<
			I extends CodecInfoH26xBase<I>,
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>,
			TDP extends ThreadDataProvBase<I, AVSTROG>
		> extends ThreadRtpSenderBase<I, AVSTRIC, AVSTROG, TDP> {

	private enum AuState {
		SEEKING_AU_START,  // Looking for the start of a new AU
		IN_AU_PREFIX,      // Processing leading non-VCL NALs
		IN_AU_VCL,         // Processing VCL NALs of current picture
		IN_AU_SUFFIX       // Processing trailing non-VCL NALs
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int AU_QUEUE_SIZE = 32;

	protected final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;

	private AuState globalCurAuState = AuState.SEEKING_AU_START;
	private final ArrayList<H26xNalUnitData<I>> globalAuQueue = new ArrayList<>();
	private int globalAuQuNudAvail = 0;
	private int globalAuQuNudIxRead = 0;
	private int globalAuQuNudIxWrite = 0;
	private boolean globalAuQuHaveOneAu = false;

	private boolean globalAuLastOutputNudWasEndOfAu = false;

	protected @Nullable I globalAuLastOutputNudH26xInfoPtr = null;

	/** Total RTP payload size of the entire AU -- current AU*/
	private long globalAuTotalRtpPayloadSizeCur = 0L;
	/** Total RTP payload size of the entire AU -- next AU */
	private long globalAuTotalRtpPayloadSizeNext = 0L;

	private long globalTotalNudCount = 0L;

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

		//
		for (int i = 0; i < AU_QUEUE_SIZE; i++) {
			globalAuQueue.add(createNud());
		}
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
	protected void beforeRunHook() throws InterruptedException, InputStreamEosException {
		super.beforeRunHook();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		while (! globalAuQuHaveOneAu && ! doStop.get()) {
			try {
				frameDataSupplierGrabNalUnit();
			} catch (InputStreamEosException e) {
				cacheFrameData.haveErrorEos = true;
				cacheFrameData.errorMsg = FNC_NAME + ": EOS reached";
				return cacheFrameData;
			}
		}

		if (globalAuQuNudAvail == 0) {
			throw new IllegalStateException(FNC_NAME + ": globalAuQuNudAvail == 0");
		}

		//
		H26xNalUnitData<I> outputNud = getNextNudForOutput();
		if (outputNud.h26xInfo == null) {
			throw new IllegalStateException(FNC_NAME + ": outputNud.h26xInfo == null");
		}
		/*logDebug(FNC_NAME, "outputNud=" +
				outputNud.toStringWithNUT(debugNudTypeToString(outputNud.h26xInfo.nalUnitTypeBy)));*/

		//
		globalAuLastOutputNudWasEndOfAu = outputNud.isEndOfAu;
		globalAuLastOutputNudH26xInfoPtr = outputNud.h26xInfo;

		//
		cacheFrameData.rtpFrameNr = outputNud.rtpFrameNr;
		cacheFrameData.totalFrameSize = outputNud.rawPayloadData.getUsed();
		cacheFrameData.rtpPayloadDataViewPtr = outputNud.rtpPayloadDataView;
		cacheFrameData.totalAuRtpPayloadSz = globalAuTotalRtpPayloadSizeCur;
		cacheFrameData.frameDesc = String.format("NAL Unit Type 0x%02X/%s",
				outputNud.h26xInfo.nalUnitTypeBy, debugNudTypeToString(outputNud.h26xInfo.nalUnitTypeBy));

		//
		if (outputNud.isEndOfAu) {
			globalAuQuHaveOneAu = false;
		}

		return cacheFrameData;
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		return (globalAuLastOutputNudWasEndOfAu && isLastFragment);
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract @NonNull H26xNalUnitData<I> createNud();

	protected abstract @NonNull I createCodecInfo();

	protected abstract @NonNull String debugNudTypeToString(byte nudTypeBy);

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract boolean isNalUnitNonVclSei(I nalInfo);

	protected abstract boolean isNalUnitLeadingNonVcl(I nalInfo);

	protected abstract boolean isNalUnitTrailingNonVcl(I nalInfo);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void frameDataSupplierGrabNalUnit() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".frameDataSupplierGrabNalUnit()";

		if (threadDataProv == null || ! threadDataProv.isRunning()) {
			logDebug(FNC_NAME, "threadDataProv is not running");
			throw new InputStreamEosException();
		}

		// store NUD in globalAuQueue
		H26xNalUnitData<I> tmpLatestNud = globalAuQueue.get(globalAuQuNudIxWrite);
		tmpLatestNud.reset();
		if (tmpLatestNud.h26xInfo == null) {
			tmpLatestNud.h26xInfo = createCodecInfo();
		} else {
			tmpLatestNud.h26xInfo.reset();
		}
		globalAuQuNudAvail++;
		tmpLatestNud.internalId = ++globalTotalNudCount;
		if (++globalAuQuNudIxWrite == AU_QUEUE_SIZE) {
			globalAuQuNudIxWrite = 0;
		}

		// get the next frame from the input stream
		threadDataProv.getNextFrame(tmpLatestNud.rawPayloadData, tmpLatestNud.h26xInfo);
		if (tmpLatestNud.rawPayloadData.isEmpty()) {
			logWarn(FNC_NAME, "tmpLatestNud.rawPayloadData is empty");
			throw new InputStreamEosException();
		}

		// filter out SEI NAL Units
		if (isNalUnitNonVclSei(tmpLatestNud.h26xInfo)) {
			--globalAuQuNudAvail;
			if (globalAuQuNudIxWrite == 0) {
				globalAuQuNudIxWrite = AU_QUEUE_SIZE - 1;
			} else {
				--globalAuQuNudIxWrite;
			}
			--globalTotalNudCount;
			//logDebug(FNC_NAME, "filtered out SEI NAL Unit");
			return;
		}

		// 'extract' the actual RTP/H26x payload
		tmpLatestNud.rtpPayloadDataView.setOffset(tmpLatestNud.h26xInfo.nalUnitOffset);
		tmpLatestNud.rtpPayloadDataView.setLength(tmpLatestNud.h26xInfo.nalUnitLength);

		//
		updateStateMachine(tmpLatestNud);
	}

	private void updateStateMachine(@NonNull H26xNalUnitData<I> latestNud) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateStateMachine()";

		if (latestNud.h26xInfo == null) {
			throw new IllegalStateException("latestNud.h26xInfo == null");
		}

		boolean closePreviousAu = false;
		switch (globalCurAuState) {
			case AuState.SEEKING_AU_START:
				if (latestNud.h26xInfo.isVclNalUnit && latestNud.h26xInfo.isVclFirstSliceSegmentInPic) {
					globalCurAuState = AuState.IN_AU_VCL;
				} else if (isNalUnitLeadingNonVcl(latestNud.h26xInfo)) {
					globalCurAuState = AuState.IN_AU_PREFIX;
				}
				break;
			case AuState.IN_AU_PREFIX:
				if (latestNud.h26xInfo.isVclNalUnit && latestNud.h26xInfo.isVclFirstSliceSegmentInPic) {
					globalCurAuState = AuState.IN_AU_VCL;
				} else if (! isNalUnitLeadingNonVcl(latestNud.h26xInfo)) {
					// This NAL doesn't belong to the current AU
					closePreviousAu = true;
				}
				break;
			case AuState.IN_AU_VCL:
				if (latestNud.h26xInfo.isVclNalUnit) {
					if (latestNud.h26xInfo.isVclFirstSliceSegmentInPic) {
						// Start of new picture/AU
						closePreviousAu = true;
					} /*else {
						// More slices of the current picture
					}*/
				} else if (isNalUnitTrailingNonVcl(latestNud.h26xInfo)) {
					globalCurAuState = AuState.IN_AU_SUFFIX;
				} else {
					// Leading NAL of next AU
					globalCurAuState = AuState.IN_AU_PREFIX;
					closePreviousAu = true;
				}
				break;
			case AuState.IN_AU_SUFFIX:
				if (! isNalUnitTrailingNonVcl(latestNud.h26xInfo)) {
					// This starts a new AU
					globalCurAuState = AuState.IN_AU_PREFIX;
					closePreviousAu = true;
				}
				break;
			default:
				throw new IllegalStateException(FNC_NAME + ": unexpected state=" + globalCurAuState);
		}

		//
		if (closePreviousAu) {
			// increase frame number when closing an Access Unit
			incrRtpTsFrameNr();
		}
		latestNud.rtpFrameNr = getRtpTsFrameNr();
		if (closePreviousAu && globalAuQuNudAvail > 1) {
			int prevNudIx;
			if (globalAuQuNudIxWrite == 0) {
				prevNudIx = AU_QUEUE_SIZE - 2;
			} else if (globalAuQuNudIxWrite == 1) {
				prevNudIx = AU_QUEUE_SIZE - 1;
			} else {
				prevNudIx = globalAuQuNudIxWrite - 2;
			}
			globalAuQueue.get(prevNudIx).isEndOfAu = true;
			globalAuQuHaveOneAu = true;
			//
			globalAuTotalRtpPayloadSizeCur = globalAuTotalRtpPayloadSizeNext;
			globalAuTotalRtpPayloadSizeNext = 0L;
		}
		globalAuTotalRtpPayloadSizeNext += latestNud.rtpPayloadDataView.getLength();
	}

	private @NonNull H26xNalUnitData<I> getNextNudForOutput() {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextNudForOutput()";

		if (globalAuQuNudAvail == 0) {
			throw new IllegalStateException(FNC_NAME + ": globalAuQuNudAvail == 0");
		}
		H26xNalUnitData<I> resPtr = globalAuQueue.get(globalAuQuNudIxRead);
		if (resPtr == null) {
			throw new IllegalStateException(FNC_NAME + ": resPtr == null");
		}
		if (++globalAuQuNudIxRead == AU_QUEUE_SIZE) {
			globalAuQuNudIxRead = 0;
		}
		return resPtr;
	}

}
