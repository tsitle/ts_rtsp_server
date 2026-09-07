package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

public record RtspProtoEsSourceExpandedInfo(
			int demuxerSubStreamIx,
			@NonNull RtpPacketType codec,
			@NonNull RtspProtoEsSourceType esSourceType,
			@NonNull ProUri inputUri,
			@NonNull RtspProtoClientCredentials credentials,
			double durationSecs,
			byte audioChannelCount,
			@NonNull SampleRateEnum audioSampleRate,
			int audioSamplesPerFrame,
			boolean isAudioPcmBigEndian,
			@NonNull ExtradataContainerHex audioAacHexCfg,
			@NonNull FrameRateEnum videoFps,
			@NonNull ExtradataContainerSdp videoExtraB64Cfg,
			@Nullable TcSettingsAudio tcSettingsAudio
		) implements Cloneable {

	public record TcSettingsAudio(
				@NonNull String codecStr,
				byte audioChannelCount,
				@NonNull SampleRateEnum audioSampleRate,
				int audioBitRateKbps
			) implements Cloneable {
		@Override
		public @NonNull TcSettingsAudio clone() {
			try {
				return (TcSettingsAudio)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new RuntimeException();
			}
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull RtspProtoEsSourceExpandedInfo clone() {
		ExtradataContainerHex tmpAudioAacHexCfg = ExtradataContainerHex.ofEmpty();
		tmpAudioAacHexCfg.copyFrom(audioAacHexCfg);

		ExtradataContainerSdp tmpVideoExtraB64Cfg = ExtradataContainerSdp.ofEmpty();
		tmpVideoExtraB64Cfg.copyFrom(videoExtraB64Cfg);

		return new RtspProtoEsSourceExpandedInfo(
				demuxerSubStreamIx,
				codec,
				esSourceType,
				inputUri.clone(),
				credentials.clone(),
				durationSecs,
				audioChannelCount,
				audioSampleRate,
				audioSamplesPerFrame,
				isAudioPcmBigEndian,
				tmpAudioAacHexCfg,
				videoFps,
				tmpVideoExtraB64Cfg,
				tcSettingsAudio == null ? null : tcSettingsAudio.clone()
			);
	}

}
