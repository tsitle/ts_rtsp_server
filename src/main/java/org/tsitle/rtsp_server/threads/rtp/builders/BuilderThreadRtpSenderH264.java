package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_v_h26x.ThreadRtpSenderH264;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderH264;

public final class BuilderThreadRtpSenderH264 {

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

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromFile.class,
							FrameGrabberVideoH26xFromFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromFile.class,  // @TODO
							FrameGrabberVideoH26xFromFile.class,  // @TODO
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
					default -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromMq.class,
							FrameGrabberVideoH26xFromMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
				};
		}

	}

}
