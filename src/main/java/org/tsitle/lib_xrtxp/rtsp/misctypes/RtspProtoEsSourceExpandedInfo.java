package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

import java.net.URI;

public record RtspProtoEsSourceExpandedInfo(
			int demuxerSubStreamIx,
			@NonNull RtpPacketType codec,
			@NonNull RtspProtoEsSourceType esSourceType,
			@NonNull URI inputUri,
			@NonNull RtspProtoClientCredentials credentials,
			double durationSecs,
			byte audioChannelCount,
			@NonNull SampleRateEnum audioSampleRate,
			int audioSamplesPerFrame,
			boolean isAudioPcmBigEndian,
			@NonNull ExtradataContainerHex audioAacHexCfg,
			@NonNull FrameRateEnum videoFps,
			@NonNull ExtradataContainerSdp videoExtraB64Cfg
		) implements Cloneable {

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
				URI.create(inputUri.toString()),
				credentials.clone(),
				durationSecs,
				audioChannelCount,
				audioSampleRate,
				audioSamplesPerFrame,
				isAudioPcmBigEndian,
				tmpAudioAacHexCfg,
				videoFps,
				tmpVideoExtraB64Cfg
			);
	}

}
