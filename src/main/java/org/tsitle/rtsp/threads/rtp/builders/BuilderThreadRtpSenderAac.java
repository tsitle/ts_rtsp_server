package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAac;

public class BuilderThreadRtpSenderAac {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAac<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderAac threadParamsAac = new ParamsThreadRtpSenderAac();

		// Fluent setters
		public BuilderThreadRtpSenderAac.Builder audAacSampleRateHz(int v) { this.threadParamsAac.setAudioSampleRateHz(v); return self(); }

		//
		@Override
		public ThreadRtpSenderAac<?, ?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsAac.validate();

			if (threadParamsCommon.getIsStreamSourceFromFile()) {
				return new ThreadRtpSenderAac<>(
						AvStreamIncomingFromFile.class,
						AudioStreamOutgoingAacFromFile.class,
						threadParamsCommon,
						threadParamsAudio,
						threadParamsAac
					);
			}
			throw new RuntimeException("Not implemented");
		}

	}

}
