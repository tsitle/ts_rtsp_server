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
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoClientCredentials;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.availstreams.RtspAsEdSdpHelper;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamInputDmxAf;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSs;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.helpers.FfCodecToRtpPacketTypeHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class StreamsCfgVirtualEsMapper {

	record VirtualEsObjs(
			@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSs> mapVirtInternalIdEsToEsCfgObj,
			@NonNull Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal,
			@NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei
		) { }

	private final FfmpegDmxSubStreamInfoVideo ffSubStreamInfoVideo = new FfmpegDmxSubStreamInfoVideo();
	private final FfmpegDmxSubStreamInfoAudio ffSubStreamInfoAudio = new FfmpegDmxSubStreamInfoAudio();

	private StreamsCfgVirtualEsMapper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull VirtualEsObjs createVirtualEsesFromDemuxedSource_fcOrRtsp(
				@NonNull RtspSrvConfigStreamsSs ssCfgObj,
				@NonNull String extRealSsId
			) throws ConfigInvalidException {
		final String FNC_NAME = StreamsCfgVirtualEsMapper.class.getSimpleName() + ".createVirtualEsesFromDemuxedSource_fcOrRtsp()";

		StreamsCfgVirtualEsMapper vem = new StreamsCfgVirtualEsMapper();

		final String errMsgSuffix = "for DMX FC/RTSP Source ID '" + extRealSsId + "'";

		final ProUri internalUri;
		final RtspProtoEsSourceType virtualEsSourceType;
		if (ssCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_FC) {
			internalUri = ssCfgObj.getSsSourceDmxFc().orElseThrow().getInputUri();
			virtualEsSourceType = RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_FC;
		} else if (ssCfgObj.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_RTSP) {
			internalUri = ssCfgObj.getSsSourceDmxRtsp().orElseThrow().getInputUri();
			virtualEsSourceType = RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP;
		} else {
			throw new ConfigInvalidException(FNC_NAME + ": Unsupported ES source type '" + ssCfgObj.getEsSourceType() + "' " +
					errMsgSuffix);
		}

		final String errMsgUri = RtspSrvConfigStreamsSs.buildDmxSourceUriForErrorMsgs(internalUri);

		//
		vem.readDmxSubStreamInfos(internalUri, errMsgSuffix);

		//
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSs> mapVirtualInternalIdToCfgObj = new HashMap<>();
		Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal = new HashMap<>();
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei = new HashMap<>();

		//
		FfmpegDmxSubStreamInfoVideo tmpFfSsInfoVid = vem.ffSubStreamInfoVideo;
		if (tmpFfSsInfoVid.ffmpegCodec.isVideo()) {
			RtspSrvConfigStreamsSs tmpEsSrcObj = RtspSrvConfigStreamsSs.createVirtualSsFromDemuxedSubStream(
					virtualEsSourceType,
					internalUri
				);
			String tmpExternalId = generateVirtualDemuxedExternalEsId(extRealSsId, true);
			RtspProtoIdEsSource tmpInternalId = StreamsCfgIdMapperHelper.computeInternalEsId(tmpExternalId);
			mapVirtExternalEsIdToInternal.put(tmpExternalId, tmpInternalId);
			mapVirtualInternalIdToCfgObj.put(tmpInternalId, tmpEsSrcObj);

			//
			Optional<RtpPacketType> tmpOptVideoCodec =
					FfCodecToRtpPacketTypeHelper.convertFfmpegVideoCodecToRtpPacketType(tmpFfSsInfoVid.ffmpegCodec);
			if (tmpOptVideoCodec.isEmpty()) {
				throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + tmpFfSsInfoVid.ffmpegCodec);
			}
			RtpPacketType videoCodec = tmpOptVideoCodec.get();
			FrameRateEnum videoFps = FrameRateEnum.of(tmpFfSsInfoVid.fps.toDouble());
			if (videoFps == FrameRateEnum.UNKNOWN) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle FPS value " + tmpFfSsInfoVid.fps + " " +
						"for uri='" + errMsgUri + "'");
			}
			RtspProtoEsSourceExpandedInfo eseiVideo = createEsei_video(
					tmpFfSsInfoVid.subStreamIx,
					videoCodec,
					virtualEsSourceType,
					internalUri,
					tmpFfSsInfoVid.durationSecs,
					videoFps,
					RtspAsEdSdpHelper.buildExtradataForSdp(videoCodec, tmpFfSsInfoVid.extradataHex)
				);
			mapVirtEsIdToEsei.put(tmpInternalId, eseiVideo);
		}

		//
		FfmpegDmxSubStreamInfoAudio tmpFfSsInfoAud = vem.ffSubStreamInfoAudio;
		if (tmpFfSsInfoAud.ffmpegCodec.isAudio()) {
			RtspSrvConfigStreamsSs tmpEsSrcObj = RtspSrvConfigStreamsSs.createVirtualSsFromDemuxedSubStream(
					virtualEsSourceType,
					internalUri
				);
			String tmpExternalId = generateVirtualDemuxedExternalEsId(extRealSsId, false);
			RtspProtoIdEsSource tmpInternalId = StreamsCfgIdMapperHelper.computeInternalEsId(tmpExternalId);
			mapVirtExternalEsIdToInternal.put(tmpExternalId, tmpInternalId);
			mapVirtualInternalIdToCfgObj.put(tmpInternalId, tmpEsSrcObj);

			//
			Optional<RtpPacketType> tmpOptAudioCodec = FfCodecToRtpPacketTypeHelper.convertFfmpegAudioCodecToRtpPacketType(
					tmpFfSsInfoAud.ffmpegCodec,
					tmpFfSsInfoAud.sampleRate,
					(byte)tmpFfSsInfoAud.channelCount
				);
			if (tmpOptAudioCodec.isEmpty()) {
				throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + tmpFfSsInfoAud.ffmpegCodec);
			}
			RtpPacketType audioCodec = tmpOptAudioCodec.get();
			SampleRateEnum audioSr = SampleRateEnum.of(tmpFfSsInfoAud.sampleRate.getSrHz());
			if (audioSr == SampleRateEnum.UNKNOWN) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle SampleRate value " + tmpFfSsInfoAud.sampleRate + " " +
						"for uri='" + errMsgUri + "'");
			}
			if (audioCodec.isPcmAudio() &&
					(tmpFfSsInfoAud.channelCount < 1 ||
							tmpFfSsInfoAud.channelCount > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX)) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle PCM Audio ChannelCount value " +
						tmpFfSsInfoAud.channelCount + " " + "for uri='" + errMsgUri + "'");
			}
			if (audioCodec == RtpPacketType.A_OPUS &&
					(tmpFfSsInfoAud.channelCount < 1 ||
							tmpFfSsInfoAud.channelCount > DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX)) {  // just in case
				throw new ConfigInvalidException(FNC_NAME + ": cannot handle Opus Audio ChannelCount value " +
						tmpFfSsInfoAud.channelCount + " " + "for uri='" + errMsgUri + "'");
			}
			ExtradataContainerHex audioExtradataHex = ExtradataContainerHex.ofEmpty();
			if (tmpFfSsInfoAud.ffmpegCodec == FfmpegCodec.A_AAC) {
				audioExtradataHex.copyFrom(tmpFfSsInfoAud.extradataHex);
			}
			RtspProtoEsSourceExpandedInfo eseiAudio = createEsei_audio_default(
					tmpFfSsInfoAud.subStreamIx,
					audioCodec,
					virtualEsSourceType,
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

	static @NonNull VirtualEsObjs createVirtualEsesFromDemuxedSource_af(
				@NonNull RtspSrvConfigStreamsSs ssCfgObj,
				@NonNull String extRealSsId
			) throws ConfigInvalidException {
		final RtspSrvConfigStreamInputDmxAf ssCfgDmxAfObj = ssCfgObj.getSsSourceDmxAf().orElseThrow();
		final RtspProtoEsSourceType virtualEsSourceType = RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_AF;

		//
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSs> mapVirtualInternalIdToCfgObj = new HashMap<>();
		Map<@NonNull String, @NonNull RtspProtoIdEsSource> mapVirtExternalEsIdToInternal = new HashMap<>();
		Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapVirtEsIdToEsei = new HashMap<>();

		//
		RtspSrvConfigStreamsSs tmpEsSrcObj = RtspSrvConfigStreamsSs.createVirtualSsFromDemuxedSubStream(
				virtualEsSourceType,
				ssCfgDmxAfObj.getInputUri()
			);
		String tmpExternalId = generateVirtualDemuxedExternalEsId(extRealSsId, false);
		RtspProtoIdEsSource tmpInternalId = StreamsCfgIdMapperHelper.computeInternalEsId(tmpExternalId);
		mapVirtExternalEsIdToInternal.put(tmpExternalId, tmpInternalId);
		mapVirtualInternalIdToCfgObj.put(tmpInternalId, tmpEsSrcObj);

		//
		RtspProtoEsSourceExpandedInfo.TcSettingsAudio tcSettingsAudio = new RtspProtoEsSourceExpandedInfo.TcSettingsAudio(
				ssCfgDmxAfObj.getTcCodec(),
				ssCfgDmxAfObj.getTcAudioChannelCount(),
				ssCfgDmxAfObj.getTcAudioSampleRate(),
				ssCfgDmxAfObj.getTcAudioBitrateKbps()
			);
		RtspProtoEsSourceExpandedInfo eseiAudio = createEsei_audio_withTc(
				virtualEsSourceType,
				ssCfgDmxAfObj.getInputUri(),
				tcSettingsAudio
			);
		mapVirtEsIdToEsei.put(tmpInternalId, eseiAudio);

		//
		return new VirtualEsObjs(mapVirtualInternalIdToCfgObj, mapVirtExternalEsIdToInternal, mapVirtEsIdToEsei);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void readDmxSubStreamInfos(@NonNull ProUri internalUri, @NonNull String errMsgSuffix) throws ConfigInvalidException {
		final String errMsgUri = RtspSrvConfigStreamsSs.buildDmxSourceUriForErrorMsgs(internalUri);

		//
		FfmpegDmxSettingsRsi dmxSettingsRsi = new FfmpegDmxSettingsRsi();
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsRsi.cfgAllowedCodecsVideo.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsRsi.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsRsi.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);

		try {
			FfmpegDemuxer.readStreamInfos(
					null,
					internalUri.getUriString().orElse(""),
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
				 * FFmpeg reports the Time Base as the Frame Rate for RTSP streams if the SDP doesn't define the actual FPS.
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

	private static @NonNull String generateVirtualDemuxedExternalEsId(@NonNull String extRealSsId, boolean isVideo) {
		return String.format("demuxed_ss_#%s#-virtual_es_#%s#", extRealSsId, isVideo ? "v" : "a");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_video(
				int demuxerSubStreamIx,
				@NonNull RtpPacketType codec,
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull ProUri inputUri,
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
				RtspProtoClientCredentials.ofEmpty(),
				durationSecs,
				(byte)0,
				SampleRateEnum.UNKNOWN,
				0,
				false,
				ExtradataContainerHex.ofEmpty(),
				videoFps,
				clonedVideoExtraB64Cfg,
				null
			);
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_audio_default(
				int demuxerSubStreamIx,
				@NonNull RtpPacketType codec,
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull ProUri inputUri,
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
				RtspProtoClientCredentials.ofEmpty(),
				durationSecs,
				audioChannelCount,
				audioSampleRate,
				audioSamplesPerFrame,
				isAudioPcmBigEndian,
				clonedAudioAacHexCfg,
				FrameRateEnum.UNKNOWN,
				ExtradataContainerSdp.ofEmpty(),
				null
			);
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_audio_withTc(
				@NonNull RtspProtoEsSourceType esSourceType,
				@NonNull ProUri inputUri,
				RtspProtoEsSourceExpandedInfo.@NonNull TcSettingsAudio tcSettingsAudio
			) {
		return new RtspProtoEsSourceExpandedInfo(
				-1,
				RtpPacketType.UNKNOWN,
				esSourceType,
				inputUri,
				RtspProtoClientCredentials.ofEmpty(),
				-1.0,
				(byte)0,
				SampleRateEnum.UNKNOWN,
				-1,
				true,
				ExtradataContainerHex.ofEmpty(),
				FrameRateEnum.UNKNOWN,
				ExtradataContainerSdp.ofEmpty(),
				tcSettingsAudio
			);
	}

}
