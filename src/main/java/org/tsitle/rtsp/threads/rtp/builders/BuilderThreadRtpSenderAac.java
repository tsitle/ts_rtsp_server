package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAac;

import java.io.FileNotFoundException;

public class BuilderThreadRtpSenderAac {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAac> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderAac threadParamsAac = new ParamsThreadRtpSenderAac();

		// Fluent setters
		public BuilderThreadRtpSenderAac.Builder audAacSampleRateHz(int v) { this.threadParamsAac.setAudioSampleRateHz(v); return self(); }

		//
		@Override
		public ThreadRtpSenderAac build() throws FileNotFoundException {
			validateCommon();
			validateAudioCommon();
			threadParamsAac.validate();

			return new ThreadRtpSenderAac(
					threadParamsCommon,
					threadParamsAudio,
					threadParamsAac
				);
		}

	}

}
