package org.tsitle.rtsp_server.config;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacParser;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.*;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataForSdpHelper;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264SpsContext;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_v_h26x.FrameGrabberVideoH26xFromEsFile;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;

import java.util.HashMap;
import java.util.Map;

final class ReadEsFileMeta {

	private ReadEsFileMeta() { }

	static void readEsFileMeta(
				@NonNull String extEsId,
				@NonNull RtspConfigElementaryStreamSource ioEsSource
			) throws ConfigInvalidException {
		if (! ioEsSource.getEnabled()) {
			return;
		}
		switch (ioEsSource.getCodec()) {
			case RtpPacketType.A_AAC -> ReadEsFileMeta.readEsFileMeta_aac(extEsId, ioEsSource);
			case RtpPacketType.A_AC3 -> ReadEsFileMeta.readEsFileMeta_ac3(extEsId, ioEsSource);
			case RtpPacketType.V_H264 -> ReadEsFileMeta.readEsFileMeta_h264Header(extEsId, ioEsSource);
			case RtpPacketType.V_H265 -> ReadEsFileMeta.readEsFileMeta_h265Header(extEsId, ioEsSource);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void readEsFileMeta_aac(
				@NonNull String extEsId,
				@NonNull RtspConfigElementaryStreamSource ioEsSource
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(
					ioEsSource.getIdAsProtoId(),
					ioEsSource.getInputUri()
				)) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAacFromEsFile asoAac = new FrameGrabberAudioAacFromEsFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();
			asoAac.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAacInfo aacInfo = AudioAacParser.parseAdtsHeader(tmpBuf);

			if (aacInfo.audioObjectType != AudioAacInfo.AudioObjectType.AAC_LC) {
				throw new ConfigInvalidException("Unsupported AAC AudioObjectType " + aacInfo.audioObjectType +
						" for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (aacInfo.samplerate == AudioAacInfo.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AAC Samplerate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (ioEsSource.getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(aacInfo.samplerate.getHz()) != ioEsSource.getAudioSamplerate()) {
				throw new ConfigInvalidException("AAC Samplerate mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + ioEsSource.getAudioSamplerate().getSrHz() + ", fileHeader=" + aacInfo.samplerate.getHz() + ")");
			}
			ioEsSource.internalAudioSampleRate = SampleRateEnum.of(aacInfo.samplerate.getHz());
			if (ioEsSource.getAudioChannelCount() > 0 && aacInfo.channelConfiguration != ioEsSource.getAudioChannelCount()) {
				throw new ConfigInvalidException("AAC ChannelCount mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + ioEsSource.getAudioChannelCount() + ", fileHeader=" + aacInfo.channelConfiguration + ")");
			}
			ioEsSource.internalAudioChannelCount = (byte)aacInfo.channelConfiguration;

			ioEsSource.internalAacAudioSpecificConfigHex.copyFrom(aacInfo.sdpFmtpConfigHex);
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AAC file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AAC header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

	private static void readEsFileMeta_ac3(
				@NonNull String extEsId,
				@NonNull RtspConfigElementaryStreamSource ioEsSource
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(
					ioEsSource.getIdAsProtoId(),
					ioEsSource.getInputUri()
				)) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAc3FromEsFile asoAc3 = new FrameGrabberAudioAc3FromEsFile(avStreamIncoming);
			TimestampMonotonic tmpStTimestamp = TimestampMonotonic.ofEmpty();
			asoAc3.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAc3Info ac3Info = AudioAc3Parser.parseAc3Header(tmpBuf);

			if (ac3Info.bitrate == AudioAc3Info.Bitrate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Bitrate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (ac3Info.samplerate == AudioAc3Info.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Samplerate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (ioEsSource.getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(ac3Info.samplerate.getHz()) != ioEsSource.getAudioSamplerate()) {
				throw new ConfigInvalidException("AC-3 Samplerate mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + ioEsSource.getAudioSamplerate().getSrHz() + ", fileHeader=" + ac3Info.samplerate.getHz() + ")");
			}
			ioEsSource.internalAudioSampleRate = SampleRateEnum.of(ac3Info.samplerate.getHz());
			if (ioEsSource.getAudioChannelCount() > 0 && ac3Info.audioCodingMode.getChannelCount() != ioEsSource.getAudioChannelCount()) {
				throw new ConfigInvalidException("AC-3 ChannelCount mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + ioEsSource.getAudioChannelCount() + ", fileHeader=" + ac3Info.audioCodingMode.getChannelCount() + ")");
			}
			ioEsSource.internalAudioChannelCount = (byte)ac3Info.audioCodingMode.getChannelCount();
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AC-3 file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AC-3 header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

	private static void readEsFileMeta_h264Header(
				@NonNull String extEsId,
				@NonNull RtspConfigElementaryStreamSource ioEsSource
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(
					ioEsSource.getIdAsProtoId(),
					ioEsSource.getInputUri()
				)) {
			Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
			Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
			VideoH264Parser codecParser = new VideoH264Parser(mapSpsContext, mapPpsContext);

			BufferExt tmpBuf = new BufferExt();
			FrameGrabberVideoH26xFromEsFile asoH26x = new FrameGrabberVideoH26xFromEsFile(avStreamIncoming);
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

			if (tmpHaveSps && tmpHavePps) {
				String extradataHex = tmpStoreSps.toHexString() + tmpStorePps.toHexString();
				ioEsSource.internalVideoExtradataB64.copyFrom(
						ExtradataForSdpHelper.buildExtradataForSdp(RtpPacketType.V_H264, extradataHex)
					);
			}
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from H264 file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse H264 header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

	private static void readEsFileMeta_h265Header(
				@NonNull String extEsId,
				@NonNull RtspConfigElementaryStreamSource ioEsSource
			) throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(
					ioEsSource.getIdAsProtoId(),
					ioEsSource.getInputUri()
				)) {
			VideoH265Parser codecParser = new VideoH265Parser();

			BufferExt tmpBuf = new BufferExt();
			FrameGrabberVideoH26xFromEsFile asoH26x = new FrameGrabberVideoH26xFromEsFile(avStreamIncoming);
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

			if (tmpHaveSps && tmpHavePps && tmpHaveVps) {
				String extradataHex = tmpStoreSps.toHexString() + tmpStorePps.toHexString() + tmpStoreVps.toHexString();
				ioEsSource.internalVideoExtradataB64.copyFrom(
						ExtradataForSdpHelper.buildExtradataForSdp(RtpPacketType.V_H265, extradataHex)
					);
			}
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from H265 file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse H265 header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

}
