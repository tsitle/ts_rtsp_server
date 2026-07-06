package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
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

			if (threadParamsCommon.getIsEsSourceFromFile()) {
				return new ThreadRtpSenderPcm<>(
						AvStreamIncomingFromFile.class,
						AudioStreamOutgoingPcmFromFile.class,
						threadParamsCommon,
						threadParamsAudio,
						threadParamsPcm
					);
			}
			return new ThreadRtpSenderPcm<>(
					AvStreamIncomingFromMq.class,
					AudioStreamOutgoingPcmFromMq.class,
					threadParamsCommon,
					threadParamsAudio,
					threadParamsPcm
				);
		}

	}

}
