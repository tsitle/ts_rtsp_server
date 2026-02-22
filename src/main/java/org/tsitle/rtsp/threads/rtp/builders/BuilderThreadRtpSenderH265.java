package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp.threads.rtp.codec_v_h26x.ThreadRtpSenderH265;

import java.io.FileNotFoundException;

public class BuilderThreadRtpSenderH265 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH265> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderH265 threadParamsH265 = new ParamsThreadRtpSenderH265();

		// Fluent setters
		/*
		 * Future H265-only fields go here, e.g.,
		 * public Builder h265Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderH265 build() throws FileNotFoundException {
			validateCommon();
			validateVideoCommon();
			threadParamsH265.validate();

			return new ThreadRtpSenderH265(threadParamsCommon, threadParamsVideo, threadParamsH265);
		}

	}

}
