package org.tsitle.lib_ffmpeg.tc;

import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.FfmpegCdcParamsAudio;
import org.tsitle.lib_ffmpeg.FfmpegCdcParamsVideo;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

/**
 * Transcoder for A/V streams.
 */
public final class FfmpegTranscoder implements AutoCloseable {

	private static class RecvTcAvHandler implements FfmpegReceiveTcAvInterface {
		@Nullable FfmpegCdcParamsVideo cdcParamsVideo = null;
		@Nullable FfmpegCdcParamsAudio cdcParamsAudio = null;
		final FfmpegTcAvPacketList rcvdPktsVid = new FfmpegTcAvPacketList();
		final FfmpegTcAvPacketList rcvdPktsAud = new FfmpegTcAvPacketList();

		@Override
		public void cbReceiveCdcParamsVideo(@NonNull FfmpegCdcParamsVideo params) {
			this.cdcParamsVideo = new FfmpegCdcParamsVideo();
			this.cdcParamsVideo.copyFrom(params);
		}

		@Override
		public void cbReceiveCdcParamsAudio(@NonNull FfmpegCdcParamsAudio params) {
			this.cdcParamsAudio = new FfmpegCdcParamsAudio();
			this.cdcParamsAudio.copyFrom(params);
		}

		@Override
		public void cbReceiveTranscodedVideoFrame(@NonNull FfmpegAvPktBasics videoFrame) {
			rcvdPktsVid.addPkt(videoFrame);
		}

		@Override
		public void cbReceiveTranscodedAudioSamples(@NonNull FfmpegAvPktBasics audioSamples) {
			rcvdPktsAud.addPkt(audioSamples);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final boolean copySubStreamVideo;
	private final @NonNull FfmpegTcParamsInpVideo sourceParamsVideo = new FfmpegTcParamsInpVideo();
	private final boolean copySubStreamAudio;
	private final @NonNull FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();

	private final RecvTcAvHandler recvTcAvHandler = new RecvTcAvHandler();

	private @Nullable FfmpegTcTranscoderVideo ffmpegTranscoderVideo = null;
	private @Nullable FfmpegTcTranscoderAudio ffmpegTranscoderAudio = null;

	private FfmpegTranscoder(
				boolean copySubStreamVideo,
				@NonNull FfmpegTcParamsInpVideo sourceParamsVideo,
				boolean copySubStreamAudio,
				@NonNull FfmpegTcParamsInpAudio sourceParamsAudio
			) {
		if (copySubStreamVideo && sourceParamsVideo.ffmpegCodec == FfmpegCodec.V_THEORA) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".ctor(): " +
					"Theora video cannot reliably be copied");
		}
		this.copySubStreamVideo = copySubStreamVideo;
		this.sourceParamsVideo.copyFrom(sourceParamsVideo);
		this.copySubStreamAudio = copySubStreamAudio;
		this.sourceParamsAudio.copyFrom(sourceParamsAudio);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new Transcoder.
	 * @param logMsgInterface 'Log message' instance
	 * @param sourceParamsVideo Video input parameters
	 * @param tcSettingsVideo Video output settings
	 * @return Transcoder object
	 */
	@SuppressWarnings("unused")
	public static @NonNull FfmpegTranscoder createForVideoOnly(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull FfmpegTcParamsInpVideo sourceParamsVideo,
				@NonNull FfmpegTcSettingsOutVideo tcSettingsVideo
			) {
		FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();
		FfmpegTcSettingsOutAudio tcSettingsAudio = new FfmpegTcSettingsOutAudio();

		return createForAv(
				logMsgInterface,
				sourceParamsVideo,
				tcSettingsVideo,
				sourceParamsAudio,
				tcSettingsAudio
			);
	}

	/**
	 * Create a new Transcoder.
	 * @param logMsgInterface 'Log message' instance
	 * @param sourceParamsAudio Audio input parameters
	 * @param tcSettingsAudio Audio output settings
	 * @return Transcoder object
	 */
	@SuppressWarnings("unused")
	public static @NonNull FfmpegTranscoder createForAudioOnly(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull FfmpegTcParamsInpAudio sourceParamsAudio,
				@NonNull FfmpegTcSettingsOutAudio tcSettingsAudio
			) {
		FfmpegTcParamsInpVideo sourceParamsVideo = new FfmpegTcParamsInpVideo();
		FfmpegTcSettingsOutVideo tcSettingsVideo = new FfmpegTcSettingsOutVideo();

		return createForAv(
				logMsgInterface,
				sourceParamsVideo,
				tcSettingsVideo,
				sourceParamsAudio,
				tcSettingsAudio
			);
	}

	/**
	 * Create a new Transcoder.
	 * @param logMsgInterface 'Log message' instance
	 * @param sourceParamsVideo Video input parameters
	 * @param tcSettingsVideo Video output settings
	 * @param sourceParamsAudio Audio input parameters
	 * @param tcSettingsAudio Audio output settings
	 * @return Transcoder object
	 */
	public static @NonNull FfmpegTranscoder createForAv(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull FfmpegTcParamsInpVideo sourceParamsVideo,
				@NonNull FfmpegTcSettingsOutVideo tcSettingsVideo,
				@NonNull FfmpegTcParamsInpAudio sourceParamsAudio,
				@NonNull FfmpegTcSettingsOutAudio tcSettingsAudio
			) {
		FfmpegTranscoder resObj = new FfmpegTranscoder(
				tcSettingsVideo.cfgCopySubStream,
				sourceParamsVideo,
				tcSettingsAudio.cfgCopySubStream,
				sourceParamsAudio
			);
		if (sourceParamsVideo.ffmpegCodec != FfmpegCodec.UNKNOWN &&
				tcSettingsVideo.cfgFfmpegCodec != FfmpegCodec.UNKNOWN &&
				! tcSettingsVideo.cfgCopySubStream) {
			resObj.ffmpegTranscoderVideo = new FfmpegTcTranscoderVideo(
					logMsgInterface,
					resObj.recvTcAvHandler,
					sourceParamsVideo,
					tcSettingsVideo
				);
		}
		if (sourceParamsAudio.ffmpegCodec != FfmpegCodec.UNKNOWN &&
				tcSettingsAudio.cfgFfmpegCodec != FfmpegCodec.UNKNOWN &&
				! tcSettingsAudio.cfgCopySubStream) {
			resObj.ffmpegTranscoderAudio = new FfmpegTcTranscoderAudio(
					logMsgInterface,
					resObj.recvTcAvHandler,
					sourceParamsAudio,
					tcSettingsAudio
				);
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public boolean willProduceVideo() {
		return (sourceParamsVideo.ffmpegCodec != FfmpegCodec.UNKNOWN &&
				(copySubStreamVideo || ffmpegTranscoderVideo != null));
	}

	@SuppressWarnings("unused")
	public boolean willProduceAudio() {
		return (sourceParamsAudio.ffmpegCodec != FfmpegCodec.UNKNOWN &&
				(copySubStreamAudio || ffmpegTranscoderAudio != null));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public long getDroppedFrameCount() {
		return (ffmpegTranscoderVideo == null ? 0 : ffmpegTranscoderVideo.getDroppedFrameCount());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public void getCdcParamsVideo(@NonNull FfmpegCdcParamsVideo params) {
		final String FNC_NAME = getClass().getSimpleName() + ".getCdcParamsVideo()";

		params.clear();
		if (ffmpegTranscoderVideo != null) {
			if (recvTcAvHandler.cdcParamsVideo == null) {
				throw new IllegalStateException(FNC_NAME + ": have not received cdcParamsVideo yet");
			}
			if ((recvTcAvHandler.cdcParamsVideo.ffmpegCodec == FfmpegCodec.V_H264 ||
						recvTcAvHandler.cdcParamsVideo.ffmpegCodec == FfmpegCodec.V_H265) &&
					recvTcAvHandler.cdcParamsVideo.extradataHex.isEmpty()) {
				throw new IllegalStateException(FNC_NAME + ": missing 'extradata' in cdcParamsVideo");
			}
			params.copyFrom(recvTcAvHandler.cdcParamsVideo);
		} else if (copySubStreamVideo) {
			params.ffmpegCodec = sourceParamsVideo.ffmpegCodec;
			params.imgDims = ImageDimensions.of(sourceParamsVideo.imgDims);
			params.fps = RationalNumber.of(sourceParamsVideo.frameRate);
			params.extradataHex.copyFrom(sourceParamsVideo.extradataHex);
		}
	}

	@SuppressWarnings("unused")
	public void getCdcParamsAudio(@NonNull FfmpegCdcParamsAudio params) {
		final String FNC_NAME = getClass().getSimpleName() + ".getCdcParamsAudio()";

		params.clear();
		if (ffmpegTranscoderAudio != null) {
			if (recvTcAvHandler.cdcParamsAudio == null) {
				throw new IllegalStateException(FNC_NAME + ": have not received cdcParamsAudio yet");
			}
			params.copyFrom(recvTcAvHandler.cdcParamsAudio);
		} else if (copySubStreamAudio) {
			params.ffmpegCodec = sourceParamsAudio.ffmpegCodec;
			params.sampleRate = sourceParamsAudio.sampleRate;
			params.channelCount = sourceParamsAudio.channelCount;
			params.extradataHex.copyFrom(sourceParamsAudio.extradataHex);
		}
	}

	@SuppressWarnings("unused")
	public @NonNull FfmpegTcAvPacketList transcodePacketFromBuffer(@NonNull FfmpegAvPktBasics inputFrameData)
			throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (inputFrameData.isVideo) {
			recvTcAvHandler.rcvdPktsVid.clear();
			if (ffmpegTranscoderVideo != null) {
				ffmpegTranscoderVideo.transcodePacketFromBuffer(inputFrameData);
			} else if (copySubStreamVideo) {
				recvTcAvHandler.rcvdPktsVid.addPkt(inputFrameData);
			}
			return recvTcAvHandler.rcvdPktsVid;
		}
		//
		recvTcAvHandler.rcvdPktsAud.clear();
		if (ffmpegTranscoderAudio != null) {
			ffmpegTranscoderAudio.transcodePacketFromBuffer(inputFrameData);
		} else if (copySubStreamAudio) {
			recvTcAvHandler.rcvdPktsAud.addPkt(inputFrameData);
		}
		return recvTcAvHandler.rcvdPktsAud;
	}

	@SuppressWarnings("unused")
	public @NonNull FfmpegTcAvPacketList transcodePacketFromDemuxer(
				@NonNull AVFormatContext inputAvFmtCtx,
				int subStreamIx,
				@NonNull FfmpegAvPktBasics inputFrameData
			) throws FfmpegDecoderNotFoundException, FfmpegGenericException, FfmpegEncoderNotFoundException {
		if (subStreamIx < 0) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".transcodePacketFromAvPkt(): " +
					"subStreamIx < 0");
		}
		if (inputFrameData.isVideo) {
			recvTcAvHandler.rcvdPktsVid.clear();
			if (ffmpegTranscoderVideo != null) {
				ffmpegTranscoderVideo.transcodePacketFromAvPkt(inputAvFmtCtx, subStreamIx, inputFrameData);
			} else if (copySubStreamVideo) {
				recvTcAvHandler.rcvdPktsVid.addPkt(inputFrameData);
			}
			return recvTcAvHandler.rcvdPktsVid;
		}
		//
		recvTcAvHandler.rcvdPktsAud.clear();
		if (ffmpegTranscoderAudio != null) {
			ffmpegTranscoderAudio.transcodePacketFromAvPkt(inputAvFmtCtx, subStreamIx, inputFrameData);
		} else if (copySubStreamAudio) {
			recvTcAvHandler.rcvdPktsAud.addPkt(inputFrameData);
		}
		return recvTcAvHandler.rcvdPktsAud;
	}

	@SuppressWarnings("unused")
	public @NonNull FfmpegTcAvPacketList getRemainingPackets(boolean getVideo) {
		if (getVideo) {
			return recvTcAvHandler.rcvdPktsVid;
		}
		return recvTcAvHandler.rcvdPktsAud;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void close() {
		recvTcAvHandler.rcvdPktsVid.clear();
		recvTcAvHandler.rcvdPktsAud.clear();
		if (ffmpegTranscoderVideo != null) {
			ffmpegTranscoderVideo.close();
			ffmpegTranscoderVideo = null;
		}
		if (ffmpegTranscoderAudio != null) {
			ffmpegTranscoderAudio.close();
			ffmpegTranscoderAudio = null;
		}
	}

}
