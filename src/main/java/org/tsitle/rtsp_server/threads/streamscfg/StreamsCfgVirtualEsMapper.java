package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSettingsRsi;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoAudio;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoVideo;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataForSdpHelper;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.availstreams.RtspAvailableStreamsSvc;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSsNg;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

final class StreamsCfgVirtualEsMapper {

	record VirtualEsObjs(
			@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> mapVirtInternalIdEsToEsCfgObj,
			@NonNull Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal,
			@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei
		) { }

	private final FfmpegDmxSubStreamInfoVideo ffSubStreamInfoVideo = new FfmpegDmxSubStreamInfoVideo();
	private final FfmpegDmxSubStreamInfoAudio ffSubStreamInfoAudio = new FfmpegDmxSubStreamInfoAudio();

	private StreamsCfgVirtualEsMapper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull VirtualEsObjs createVirtualEsesFromDemuxedSource(
				@NonNull RtspSrvConfigStreamsSsNg ssCfgObj,
				@NonNull String extRealSsId
			) throws ConfigInvalidException {
		final String FNC_NAME = StreamsCfgVirtualEsMapper.class.getSimpleName() + ".createVirtualEsesFromDemuxedSource()";

		StreamsCfgVirtualEsMapper vem = new StreamsCfgVirtualEsMapper();

		final String errMsgSuffix = "for Muxed-Stream Source ID '" + extRealSsId + "'";

		final URI internalUri;
		if (ssCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_MS_FILE) {
			internalUri = ssCfgObj.getSsSourceMuxFc().orElseThrow().getInputUri();
		} else if (ssCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_MS_RTSP) {
			internalUri = ssCfgObj.getSsSourceMuxRtsp().orElseThrow().getInputUri();
		} else {
			throw new ConfigInvalidException(FNC_NAME + ": Unsupported ES source type '" + ssCfgObj.getEsSourceType() + "' " +
					errMsgSuffix);
		}

		final String errMsgUri = RtspSrvConfigStreamsSsNg.buildMsSourceUriForErrorMsgs(internalUri);

		//
		vem.readSubStreamInfos(internalUri, errMsgSuffix);

		//
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> mapVirtualInternalIdToCfgObj = new HashMap<>();
		Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal = new HashMap<>();
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei = new HashMap<>();

		//
		FfmpegDmxSubStreamInfoVideo tmpFfSsInfoVid = vem.ffSubStreamInfoVideo;
		if (tmpFfSsInfoVid.ffmpegCodec.isVideo()) {
			RtspSrvConfigStreamsSsNg tmpEsSrcObj = RtspSrvConfigStreamsSsNg.createVirtualSsFromDemuxedSubStream(
					ssCfgObj.getEsSourceType(),
					internalUri
				);
			String tmpExternalId = generateVirtualDemuxedExternalEsId(extRealSsId, true);
			RtspProtoIdEsSource tmpInternalId = RtspAvailableStreamsSvc.computeInternalEsId(tmpExternalId);
			mapVirtExternalEsIdToInternal.put(tmpExternalId, tmpInternalId);
			mapVirtualInternalIdToCfgObj.put(tmpInternalId, tmpEsSrcObj);

			//
			RtpPacketType videoCodec = convertFfmpegVideoCodecToRtpPacketType(tmpFfSsInfoVid.ffmpegCodec);
			FrameRateEnum videoFps = FrameRateEnum.of(tmpFfSsInfoVid.fps.toDouble());
			if (videoFps == FrameRateEnum.UNKNOWN) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle FPS value " + tmpFfSsInfoVid.fps + " " +
						"for MS Source '" + errMsgUri + "'");
			}
			RtspProtoEsSourceExpandedInfo eseiVideo = createEsei_video(
					tmpFfSsInfoVid.subStreamIx,
					videoCodec,
					ssCfgObj.getEsSourceType(),
					internalUri,
					tmpFfSsInfoVid.durationSecs,
					videoFps,
					ExtradataForSdpHelper.buildExtradataForSdp(videoCodec, tmpFfSsInfoVid.extradataHex)
				);
			mapVirtEsIdToEsei.put(tmpInternalId, eseiVideo);
		}

		//
		FfmpegDmxSubStreamInfoAudio tmpFfSsInfoAud = vem.ffSubStreamInfoAudio;
		if (tmpFfSsInfoAud.ffmpegCodec.isAudio()) {
			RtspSrvConfigStreamsSsNg tmpEsSrcObj = RtspSrvConfigStreamsSsNg.createVirtualSsFromDemuxedSubStream(
					ssCfgObj.getEsSourceType(),
					internalUri
				);
			String tmpExternalId = generateVirtualDemuxedExternalEsId(extRealSsId, false);
			RtspProtoIdEsSource tmpInternalId = RtspAvailableStreamsSvc.computeInternalEsId(tmpExternalId);
			mapVirtExternalEsIdToInternal.put(tmpExternalId, tmpInternalId);
			mapVirtualInternalIdToCfgObj.put(tmpInternalId, tmpEsSrcObj);

			//
			RtpPacketType audioCodec = convertFfmpegAudioCodecToRtpPacketType(
					tmpFfSsInfoAud.ffmpegCodec,
					tmpFfSsInfoAud.sampleRate,
					(byte)tmpFfSsInfoAud.channelCount
				);
			SampleRateEnum audioSr = SampleRateEnum.of(tmpFfSsInfoAud.sampleRate.getSrHz());
			if (audioSr == SampleRateEnum.UNKNOWN) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle SampleRate value " + tmpFfSsInfoAud.sampleRate + " " +
						"for MS Source '" + errMsgUri + "'");
			}
			if (audioCodec.isPcmAudio() &&
					(tmpFfSsInfoAud.channelCount < 1 ||
							tmpFfSsInfoAud.channelCount > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX)) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle PCM Audio ChannelCount value " +
						tmpFfSsInfoAud.channelCount + " " + "for MS Source '" + errMsgUri + "'");
			}
			if (audioCodec == RtpPacketType.A_OPUS &&
					(tmpFfSsInfoAud.channelCount < 1 ||
							tmpFfSsInfoAud.channelCount > DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX)) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle Opus Audio ChannelCount value " +
						tmpFfSsInfoAud.channelCount + " " + "for MS Source '" + errMsgUri + "'");
			}
			ExtradataContainerHex audioExtradataHex = ExtradataContainerHex.ofEmpty();
			if (tmpFfSsInfoAud.ffmpegCodec == FfmpegCodec.A_AAC) {
				audioExtradataHex.copyFrom(tmpFfSsInfoAud.extradataHex);
			}
			RtspProtoEsSourceExpandedInfo eseiAudio = createEsei_audio(
					tmpFfSsInfoAud.subStreamIx,
					audioCodec,
					ssCfgObj.getEsSourceType(),
					internalUri,
					tmpFfSsInfoAud.durationSecs,
					(byte)tmpFfSsInfoAud.channelCount,
					audioSr,
					tmpFfSsInfoAud.samplesPerFrame,
					tmpFfSsInfoAud.ffmpegCodec == FfmpegCodec.A_PCM_S16BE,
					audioExtradataHex
				);
			mapVirtEsIdToEsei.put(tmpInternalId, eseiAudio);
		}

		//
		return new VirtualEsObjs(mapVirtualInternalIdToCfgObj, mapVirtExternalEsIdToInternal, mapVirtEsIdToEsei);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void readSubStreamInfos(@NonNull URI internalUri, @NonNull String errMsgSuffix) throws ConfigInvalidException {
		final String realUri = internalUri.toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");
		final String errMsgUri = RtspSrvConfigStreamsSsNg.buildMsSourceUriForErrorMsgs(internalUri);

		//
		FfmpegDmxSettingsRsi dmxSettingsRsi = new FfmpegDmxSettingsRsi();
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsRsi.cfgAllowedCodecsVideo.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsRsi.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);

		try {
			FfmpegDemuxer.readStreamInfos(
					null,
					realUri,
					dmxSettingsRsi,
					ffSubStreamInfoVideo,
					ffSubStreamInfoAudio
			);
		} catch (FfmpegGenericException e) {
			throw new ConfigInvalidException("Failed to read sub-stream infos " + errMsgSuffix + " " +
					"(src='" + errMsgUri + "'): " + e.getMessage());
		}

		if (ffSubStreamInfoVideo.ffmpegCodec == FfmpegCodec.UNKNOWN && ffSubStreamInfoAudio.ffmpegCodec == FfmpegCodec.UNKNOWN) {
			throw new ConfigInvalidException("No A/V sub-streams found " + errMsgSuffix);
		}

		if (ffSubStreamInfoVideo.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO.contains(ffSubStreamInfoVideo.ffmpegCodec)) {
				throw new ConfigInvalidException("Video sub-stream codec " + ffSubStreamInfoVideo.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
			if (ffSubStreamInfoVideo.fps.toDouble() > 120.0) {
				/*
				 * FFmpeg sometimes reports the Time Base as the Frame Rate for RTSP streams.
				 * Then the FPS is 90000. So we set it to a safe 30.
				 */
				ffSubStreamInfoVideo.fps.copyFrom(RationalNumber.ofFps(30.0));
			}
			if (FrameRateEnum.of(ffSubStreamInfoVideo.fps.toDouble()) == FrameRateEnum.UNKNOWN) {
				approximateFps();
			}
		}
		if (ffSubStreamInfoAudio.ffmpegCodec != FfmpegCodec.UNKNOWN) {
			if (! DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO.contains(ffSubStreamInfoAudio.ffmpegCodec)) {
				throw new ConfigInvalidException("Audio sub-stream codec " + ffSubStreamInfoAudio.ffmpegCodec + " is not supported " +
						errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.channelCount < 1) {
				throw new ConfigInvalidException("Audio sub-stream has no channels " + errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.ffmpegCodec.isPcmAudio() &&
					ffSubStreamInfoAudio.channelCount > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX) {
				throw new ConfigInvalidException("PCM Audio sub-stream with more than " +
						DpConstants.DP_PCM_AUDIO_CHANNELS_MAX + " channels " + errMsgSuffix);
			}
			if (ffSubStreamInfoAudio.ffmpegCodec == FfmpegCodec.A_OPUS &&
					ffSubStreamInfoAudio.channelCount > DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX) {
				throw new ConfigInvalidException("Opus Audio sub-stream with more than " +
						DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX + " channels " + errMsgSuffix);
			}
		}
	}

	private void approximateFps() {
		final double orgFps = ffSubStreamInfoVideo.fps.toDouble();
		FrameRateEnum closestEn = FrameRateEnum.UNKNOWN;
		double closestDiff = Double.MAX_VALUE;
		for (FrameRateEnum tmpEn : FrameRateEnum.values()) {
			double curDiff = Math.abs(tmpEn.getFrDbl() - orgFps);
			if (Double.compare(curDiff, closestDiff) < 0) {
				closestDiff = curDiff;
				closestEn = tmpEn;
			}
		}
		if (closestEn != FrameRateEnum.UNKNOWN) {
			ffSubStreamInfoVideo.fps = RationalNumber.ofFps(closestEn.getFrDbl());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtpPacketType convertFfmpegVideoCodecToRtpPacketType(@NonNull FfmpegCodec ffmpegCodec)
			throws ConfigInvalidException {
		final String FNC_NAME = StreamsCfgVirtualEsMapper.class.getSimpleName() + ".convertFfmpegVideoCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case V_H264 -> RtpPacketType.V_H264;
				case V_H265 -> RtpPacketType.V_H265;
				case V_MJPEG -> RtpPacketType.V_MJPEG;
				case V_VP8 -> RtpPacketType.V_VP8;
				default -> throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

	private static @NonNull RtpPacketType convertFfmpegAudioCodecToRtpPacketType(
				@NonNull FfmpegCodec ffmpegCodec,
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) throws ConfigInvalidException {
		final String FNC_NAME = StreamsCfgVirtualEsMapper.class.getSimpleName() + ".convertFfmpegAudioCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case A_AAC -> RtpPacketType.A_AAC;
				case A_AC3 -> RtpPacketType.A_AC3;
				case A_OPUS -> RtpPacketType.A_OPUS;
				case A_PCM_ALAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMA_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMA_VAR;
					}
				case A_PCM_MULAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMU_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMU_VAR;
					}
				case A_PCM_U8 -> RtpPacketType.A_LINEAR_PCM_U08_VAR;
				case A_PCM_S16BE, A_PCM_S16LE -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
						}
						if (audioChannelCount == 2 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
						}
						yield RtpPacketType.A_LINEAR_PCM_S16_VAR;
					}
				default -> throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String generateVirtualDemuxedExternalEsId(@NonNull String extRealSsId, boolean isVideo) {
		return String.format("demuxed_s_#%s#-virtual_es_#%s#", extRealSsId, isVideo ? "v" : "a");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_video(
				int demuxerSubStreamIx,
				@NonNull RtpPacketType codec,
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull URI inputUri,
				double durationSecs,
				@NonNull FrameRateEnum videoFps,
				@NonNull ExtradataContainerSdp videoExtraB64Cfg
			) {
		ExtradataContainerSdp clonedVideoExtraB64Cfg = ExtradataContainerSdp.ofEmpty();
		clonedVideoExtraB64Cfg.copyFrom(videoExtraB64Cfg);

		return new RtspProtoEsSourceExpandedInfo (
				demuxerSubStreamIx,
				codec,
				esSourceType,
				inputUri,
				durationSecs,
				(byte)0,
				SampleRateEnum.UNKNOWN,
				0,
				false,
				ExtradataContainerHex.ofEmpty(),
				videoFps,
				clonedVideoExtraB64Cfg
			);
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_audio(
				int demuxerSubStreamIx,
				@NonNull RtpPacketType codec,
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull URI inputUri,
				double durationSecs,
				byte audioChannelCount,
				@NonNull SampleRateEnum audioSampleRate,
				int audioSamplesPerFrame,
				boolean isAudioPcmBigEndian,
				@NonNull ExtradataContainerHex audioAacHexCfg
			) {
		ExtradataContainerHex clonedAudioAacHexCfg = ExtradataContainerHex.ofEmpty();
		clonedAudioAacHexCfg.copyFrom(audioAacHexCfg);

		return new RtspProtoEsSourceExpandedInfo(
				demuxerSubStreamIx,
				codec,
				esSourceType,
				inputUri,
				durationSecs,
				audioChannelCount,
				audioSampleRate,
				audioSamplesPerFrame,
				isAudioPcmBigEndian,
				clonedAudioAacHexCfg,
				FrameRateEnum.UNKNOWN,
				ExtradataContainerSdp.ofEmpty()
			);
	}

}
