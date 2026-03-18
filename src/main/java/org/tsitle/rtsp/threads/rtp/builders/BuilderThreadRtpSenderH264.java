package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.threads.rtp.codec_v_h26x.ThreadRtpSenderH264;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH264;

public class BuilderThreadRtpSenderH264 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH264<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderH264 threadParamsH264 = new ParamsThreadRtpSenderH264();

		// Fluent setters
		/*
		 * Future H264-only fields go here, e.g.,
		 * public Builder h264Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderH264<?, ?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsH264.validate();

			if (threadParamsCommon.getIsStreamSourceFromFile()) {
				return new ThreadRtpSenderH264<>(
						AvStreamIncomingFromFile.class,
						VideoStreamOutgoingH26xFromFile.class,
						threadParamsCommon,
						threadParamsVideo,
						threadParamsH264
					);
			}
			return new ThreadRtpSenderH264<>(
					AvStreamIncomingFromMq.class,
					VideoStreamOutgoingH26xFromMq.class,
					threadParamsCommon,
					threadParamsVideo,
					threadParamsH264
				);
		}

	}

}
