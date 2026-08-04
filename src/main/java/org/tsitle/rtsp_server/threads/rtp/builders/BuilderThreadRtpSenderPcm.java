package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsMq;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpPcm;
import org.tsitle.rtsp_server.threads.rtp.codec_a_pcm.ThreadRtpSenderPcm;

public final class BuilderThreadRtpSenderPcm {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderPcm<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadDpPcm threadParamsPcm = new ParamsThreadDpPcm();

		// Fluent setters
		public Builder audPcmChannelCount(int v) { this.threadParamsPcm.setAudioChannelCount(v); return self(); }
		public Builder audPcmBitsPerSample(int v) { this.threadParamsPcm.setAudioBitsPerSample(v); return self(); }
		public Builder audPcmInputBigEndian(boolean v) { this.threadParamsPcm.setIsAudioInputBigEndian(v); return self(); }
		public Builder audPcmCodec(RtpPacketType v) { this.threadParamsPcm.setAudioCodec(v); return self(); }

		//
		@Override
		public ThreadRtpSenderPcm<?, ?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsPcm.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderPcm<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioPcmFromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsPcm
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderPcm<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsPcm
						);
					default -> new ThreadRtpSenderPcm<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberAudioPcmFromEsMq.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsPcm
						);
				};
		}

	}

}
