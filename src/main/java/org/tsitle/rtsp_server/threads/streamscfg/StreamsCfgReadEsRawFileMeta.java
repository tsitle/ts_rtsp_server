package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_mpeg.FrameGrabberAudioMpegFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.codec_v_h26x.FrameGrabberVideoH26XFromEsRawFile;
import org.tsitle.lib_dataprov.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacParser;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegParser;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.*;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264SpsContext;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoClientCredentials;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.availstreams.RtspAsEdSdpHelper;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamInputEsRawFile;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.util.HashMap;
import java.util.Map;

final class StreamsCfgReadEsRawFileMeta {

	private StreamsCfgReadEsRawFileMeta() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static RtspProtoEsSourceExpandedInfo readMetaInfoOfEsRawFile(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		final String FNC_NAME = StreamsCfgReadEsRawFileMeta.class.getSimpleName() + ".readMetaInfoOfEsRawFile()";

		try {
			RtpPacketType codec = esSourceObj.getCodec().orElseThrow();
			return switch (codec) {
					case RtpPacketType.A_AAC -> StreamsCfgReadEsRawFileMeta.readMeta_aac(idEsSource, esSourceObj);
					case RtpPacketType.A_AC3 -> StreamsCfgReadEsRawFileMeta.readMeta_ac3(idEsSource, esSourceObj);
					case RtpPacketType.A_MPEG -> StreamsCfgReadEsRawFileMeta.readMeta_mpa(idEsSource, esSourceObj);
					case RtpPacketType.V_H264 -> StreamsCfgReadEsRawFileMeta.readMeta_h264Header(idEsSource, esSourceObj);
					case RtpPacketType.V_H265 -> StreamsCfgReadEsRawFileMeta.readMeta_h265Header(idEsSource, esSourceObj);
					default -> {
						if (codec.isVideo()) {
							yield createEsei_video(
									codec,
									esSourceObj.getInputUri(),
									esSourceObj.getVideoFps(),
									ExtradataContainerSdp.ofEmpty()
								);
						}
						yield createEsei_audio(
								codec,
								esSourceObj.getInputUri(),
								esSourceObj.getAudioChannelCount(),
								esSourceObj.getAudioSamplerate(),
								esSourceObj.getAudioSamplesPerFrame(),
								esSourceObj.getIsPcmAudioBigEndian(),
								ExtradataContainerHex.ofEmpty()
							);
					}
				};
		} catch (ConfigInvalidException e) {
			throw new ConfigInvalidException(FNC_NAME + ": " + e.getMessage() +
					" for Elementary-Stream Source ID '" + idEsSource.getIdStr().orElse("-unset-") + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoEsSourceExpandedInfo readMeta_aac(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsRawFile avStreamIncoming = new AvStreamIncomingFromEsRawFile(
					idEsSource,
					esSourceObj.getInputUri()
				)) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAacFromEsRawFile asoAac = new FrameGrabberAudioAacFromEsRawFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();
			asoAac.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAacInfo aacInfo = AudioAacParser.parseAdtsHeader(tmpBuf);

			if (aacInfo.audioObjectType != AudioAacInfo.AudioObjectType.AAC_LC) {
				throw new ConfigInvalidException("Unsupported AAC AudioObjectType " + aacInfo.audioObjectType);
			}
			if (aacInfo.samplerate == AudioAacInfo.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AAC Samplerate");
			}
			if (esSourceObj.getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(aacInfo.samplerate.getHz()) != esSourceObj.getAudioSamplerate()) {
				throw new ConfigInvalidException("AAC Samplerate mismatch (" +
						"config=" + esSourceObj.getAudioSamplerate().getSrHz() +
						", fileHeader=" + aacInfo.samplerate.getHz() + ")");
			}
			if (esSourceObj.getAudioChannelCount() > 0 &&
					aacInfo.channelConfiguration != esSourceObj.getAudioChannelCount()) {
				throw new ConfigInvalidException("AAC ChannelCount mismatch (" +
						"config=" + esSourceObj.getAudioChannelCount() +
						", fileHeader=" + aacInfo.channelConfiguration + ")");
			}

			return createEsei_audio(
					RtpPacketType.A_AAC,
					esSourceObj.getInputUri(),
					(byte)aacInfo.channelConfiguration,
					SampleRateEnum.of(aacInfo.samplerate.getHz()),
					esSourceObj.getAudioSamplesPerFrame(),
					false,
					aacInfo.sdpFmtpConfigHex
				);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AAC file: " + e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AAC header: " + e.getMessage());
		}
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo readMeta_ac3(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsRawFile avStreamIncoming = new AvStreamIncomingFromEsRawFile(
					idEsSource,
					esSourceObj.getInputUri()
				)) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAc3FromEsRawFile asoAc3 = new FrameGrabberAudioAc3FromEsRawFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();
			asoAc3.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAc3Info ac3Info = AudioAc3Parser.parseAc3Header(tmpBuf);

			if (ac3Info.bitrate == AudioAc3Info.Bitrate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Bitrate");
			}
			if (ac3Info.samplerate == AudioAc3Info.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Samplerate");
			}
			if (esSourceObj.getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(ac3Info.samplerate.getHz()) != esSourceObj.getAudioSamplerate()) {
				throw new ConfigInvalidException("AC-3 Samplerate mismatch (" +
						"config=" + esSourceObj.getAudioSamplerate().getSrHz() + ", fileHeader=" + ac3Info.samplerate.getHz() + ")");
			}
			if (esSourceObj.getAudioChannelCount() > 0 &&
					ac3Info.audioCodingMode.getChannelCount() != esSourceObj.getAudioChannelCount()) {
				throw new ConfigInvalidException("AC-3 ChannelCount mismatch (" +
						"config=" + esSourceObj.getAudioChannelCount() +
						", fileHeader=" + ac3Info.audioCodingMode.getChannelCount() + ")");
			}

			return createEsei_audio(
					RtpPacketType.A_AC3,
					esSourceObj.getInputUri(),
					(byte)ac3Info.audioCodingMode.getChannelCount(),
					SampleRateEnum.of(ac3Info.samplerate.getHz()),
					esSourceObj.getAudioSamplesPerFrame(),
					false,
					ExtradataContainerHex.ofEmpty()
				);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AC-3 file: " + e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AC-3 header: " + e.getMessage());
		}
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo readMeta_mpa(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsRawFile avStreamIncoming = new AvStreamIncomingFromEsRawFile(
					idEsSource,
					esSourceObj.getInputUri()
				)) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioMpegFromEsRawFile asoMpa = new FrameGrabberAudioMpegFromEsRawFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();
			asoMpa.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioMpegParser mpaParser = new AudioMpegParser();
			AudioMpegInfo mpaInfo = mpaParser.parseMpaData(new BufferView(tmpBuf));

			if (mpaInfo.getSampleRateHz() < 1) {
				throw new ConfigInvalidException("Could not parse MPEG Audio Samplerate");
			}
			if (esSourceObj.getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(mpaInfo.getSampleRateHz()) != esSourceObj.getAudioSamplerate()) {
				throw new ConfigInvalidException("MPEG Audio Samplerate mismatch (" +
						"config=" + esSourceObj.getAudioSamplerate().getSrHz() + ", fileHeader=" + mpaInfo.getSampleRateHz() + ")");
			}
			if (esSourceObj.getAudioChannelCount() > 0 &&
					mpaInfo.channelMode.getChannelCount() != esSourceObj.getAudioChannelCount()) {
				throw new ConfigInvalidException("MPEG Audio ChannelCount mismatch (" +
						"config=" + esSourceObj.getAudioChannelCount() +
						", fileHeader=" + mpaInfo.channelMode.getChannelCount() + ")");
			}

			return createEsei_audio(
					RtpPacketType.A_MPEG,
					esSourceObj.getInputUri(),
					(byte)mpaInfo.channelMode.getChannelCount(),
					SampleRateEnum.of(mpaInfo.getSampleRateHz()),
					esSourceObj.getAudioSamplesPerFrame(),
					false,
					ExtradataContainerHex.ofEmpty()
				);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from MPEG Audio file: " + e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse MPEG Audio header: " + e.getMessage());
		}
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo readMeta_h264Header(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsRawFile avStreamIncoming = new AvStreamIncomingFromEsRawFile(
					idEsSource,
					esSourceObj.getInputUri()
				)) {
			Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
			Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
			VideoH264Parser codecParser = new VideoH264Parser(mapSpsContext, mapPpsContext);

			BufferExt tmpBuf = new BufferExt();
			FrameGrabberVideoH26XFromEsRawFile asoH26x = new FrameGrabberVideoH26XFromEsRawFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();

			int tmpPktCounter = 0;
			boolean tmpHaveSps = false;
			boolean tmpHavePps = false;
			BufferExt tmpStoreSps = new BufferExt();
			BufferExt tmpStorePps = new BufferExt();
			while (++tmpPktCounter <= 100 && (! (tmpHaveSps && tmpHavePps))) {
				asoH26x.getNextFrame(tmpBuf, tmpStTimestamp);

				BufferView tmpBv = new BufferView(tmpBuf);
				int tmpMagicBytesLen = MagicBytesH26xHelper.findH26xMagicBytesLength(tmpBv);
				VideoH264Info codecInfo = codecParser.parseH264Data(
						0L,
						tmpMagicBytesLen,
						tmpBv,
						null
					);
				if (codecInfo.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_SPS) {
					tmpHaveSps = true;
					tmpStoreSps.copyOf(tmpBuf);
				} else if (codecInfo.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_PPS) {
					tmpHavePps = true;
					tmpStorePps.copyOf(tmpBuf);
				}
			}

			ExtradataContainerSdp videoExtraB64Cfg;
			if (tmpHaveSps && tmpHavePps) {
				String extradataHex = tmpStoreSps.toHexString() + tmpStorePps.toHexString();
				videoExtraB64Cfg = RtspAsEdSdpHelper.buildExtradataForSdp(RtpPacketType.V_H264, extradataHex);
			} else {
				videoExtraB64Cfg = ExtradataContainerSdp.ofEmpty();
			}

			return createEsei_video(
					RtpPacketType.V_H264,
					esSourceObj.getInputUri(),
					esSourceObj.getVideoFps(),
					videoExtraB64Cfg
				);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from H264 file: " + e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse H264 header: " + e.getMessage());
		}
	}

	private static @NonNull RtspProtoEsSourceExpandedInfo readMeta_h265Header(
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull RtspSrvConfigStreamInputEsRawFile esSourceObj
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsRawFile avStreamIncoming = new AvStreamIncomingFromEsRawFile(
					idEsSource,
					esSourceObj.getInputUri()
				)) {
			VideoH265Parser codecParser = new VideoH265Parser();

			BufferExt tmpBuf = new BufferExt();
			FrameGrabberVideoH26XFromEsRawFile asoH26x = new FrameGrabberVideoH26XFromEsRawFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();

			int tmpPktCounter = 0;
			boolean tmpHaveSps = false;
			boolean tmpHavePps = false;
			boolean tmpHaveVps = false;
			BufferExt tmpStoreSps = new BufferExt();
			BufferExt tmpStorePps = new BufferExt();
			BufferExt tmpStoreVps = new BufferExt();
			while (++tmpPktCounter <= 100 && (! (tmpHaveSps && tmpHavePps && tmpHaveVps))) {
				asoH26x.getNextFrame(tmpBuf, tmpStTimestamp);

				BufferView tmpBv = new BufferView(tmpBuf);
				int tmpMagicBytesLen = MagicBytesH26xHelper.findH26xMagicBytesLength(tmpBv);
				VideoH265Info codecInfo = codecParser.parseH265Data(
						0L,
						tmpMagicBytesLen,
						tmpBv
					);
				if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SPS) {
					tmpHaveSps = true;
					tmpStoreSps.copyOf(tmpBuf);
				} else if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_PPS) {
					tmpHavePps = true;
					tmpStorePps.copyOf(tmpBuf);
				} else if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_VPS) {
					tmpHaveVps = true;
					tmpStoreVps.copyOf(tmpBuf);
				}
			}

			ExtradataContainerSdp videoExtraB64Cfg;
			if (tmpHaveSps && tmpHavePps && tmpHaveVps) {
				String extradataHex = tmpStoreSps.toHexString() + tmpStorePps.toHexString() + tmpStoreVps.toHexString();
				videoExtraB64Cfg = RtspAsEdSdpHelper.buildExtradataForSdp(RtpPacketType.V_H265, extradataHex);
			} else {
				videoExtraB64Cfg = ExtradataContainerSdp.ofEmpty();
			}

			return createEsei_video(
					RtpPacketType.V_H265,
					esSourceObj.getInputUri(),
					esSourceObj.getVideoFps(),
					videoExtraB64Cfg
			);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from H265 file: " + e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse H265 header: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtspProtoEsSourceExpandedInfo createEsei_video(
				@NonNull RtpPacketType codec,
				@NonNull ProUri inputUri,
				@NonNull FrameRateEnum videoFps,
				@NonNull ExtradataContainerSdp videoExtraB64Cfg
			) {
		ExtradataContainerSdp clonedVideoExtraB64Cfg = ExtradataContainerSdp.ofEmpty();
		clonedVideoExtraB64Cfg.copyFrom(videoExtraB64Cfg);

		return new RtspProtoEsSourceExpandedInfo (
				-1,
				codec,
				RtspProtoEsSourceType.ST_ES_RAW_FILE,
				inputUri,
				RtspProtoClientCredentials.ofEmpty(),
				-1,
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
				@NonNull RtpPacketType codec,
				@NonNull ProUri inputUri,
				byte audioChannelCount,
				@NonNull SampleRateEnum audioSampleRate,
				int audioSamplesPerFrame,
				boolean isAudioPcmBigEndian,
				@NonNull ExtradataContainerHex audioAacHexCfg
			) {
		ExtradataContainerHex clonedAudioAacHexCfg = ExtradataContainerHex.ofEmpty();
		clonedAudioAacHexCfg.copyFrom(audioAacHexCfg);

		return new RtspProtoEsSourceExpandedInfo(
				-1,
				codec,
				RtspProtoEsSourceType.ST_ES_RAW_FILE,
				inputUri,
				RtspProtoClientCredentials.ofEmpty(),
				-1,
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
