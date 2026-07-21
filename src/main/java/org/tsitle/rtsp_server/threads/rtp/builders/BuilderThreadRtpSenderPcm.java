package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_pcm.FrameGrabberAudioPcmFromEsMq;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;
import org.tsitle.rtsp_server.threads.rtp.codec_a_pcm.ThreadRtpSenderPcm;

public final class BuilderThreadRtpSenderPcm {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderPcm<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderPcm threadParamsPcm = new ParamsThreadRtpSenderPcm();

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

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderPcm<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioPcmFromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsPcm
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderPcm<>(
							AvStreamIncomingFromEsFile.class,  // @TODO
							FrameGrabberAudioPcmFromEsFile.class,  // @TODO
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
