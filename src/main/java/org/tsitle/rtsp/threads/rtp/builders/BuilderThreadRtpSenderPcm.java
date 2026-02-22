package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;
import org.tsitle.rtsp.threads.rtp.codec_a_pcm.ThreadRtpSenderPcm;

import java.io.FileNotFoundException;

public class BuilderThreadRtpSenderPcm {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderPcm> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderPcm threadParamsPcm = new ParamsThreadRtpSenderPcm();

		// Fluent setters
		public Builder audPcmRtpAudioSpf(int v) { this.threadParamsPcm.setRtpAudioSpf(v); return self(); }
		public Builder audPcmSampleRateHz(int v) { this.threadParamsPcm.setAudioSampleRateHz(v); return self(); }
		public Builder audPcmChannelCount(int v) { this.threadParamsPcm.setAudioChannelCount(v); return self(); }
		public Builder audPcmBitsPerSample(int v) { this.threadParamsPcm.setAudioBitsPerSample(v); return self(); }
		public Builder audPcmInputBigEndian(boolean v) { this.threadParamsPcm.setIsAudioInputBigEndian(v); return self(); }
		public Builder audPcmCodec(RtpPacketType v) { this.threadParamsPcm.setAudioCodec(v); return self(); }

		//
		@Override
		public ThreadRtpSenderPcm build() throws FileNotFoundException {
			validateCommon();
			validateAudioCommon();
			threadParamsPcm.validate();

			return new ThreadRtpSenderPcm(
					threadParamsCommon,
					threadParamsAudio,
					threadParamsPcm
				);
		}

	}

}
