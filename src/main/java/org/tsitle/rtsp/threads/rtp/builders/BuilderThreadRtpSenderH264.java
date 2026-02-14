package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.codec_h264.ThreadRtpSenderH264;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH264;

import java.io.FileNotFoundException;

public class BuilderThreadRtpSenderH264 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH264> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderH264 threadParamsH264 = new ParamsThreadRtpSenderH264();

		// Fluent setters
		/*
		 * Future H264-only fields go here, e.g.,
		 * public Builder h264Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderH264 build() throws FileNotFoundException {
			validateCommon();
			validateVideoCommon();
			threadParamsH264.validate();

			return new ThreadRtpSenderH264(threadParamsCommon, threadParamsVideo, threadParamsH264);
		}

	}

}
