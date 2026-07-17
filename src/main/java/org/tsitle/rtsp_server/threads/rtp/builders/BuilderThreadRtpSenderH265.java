package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp_server.threads.rtp.codec_v_h26x.ThreadRtpSenderH265;

public final class BuilderThreadRtpSenderH265 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH265<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderH265 threadParamsH265 = new ParamsThreadRtpSenderH265();

		// Fluent setters
		/*
		 * Future H265-only fields go here, e.g.,
		 * public Builder h265Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderH265<?, ?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsH265.validate();

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderH265<>(
							AvStreamIncomingFromFile.class,
							FrameGrabberVideoH26xFromFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH265
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderH265<>(
							AvStreamIncomingFromFile.class,  // @TODO
							FrameGrabberVideoH26xFromFile.class,  // @TODO
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH265
						);
					default -> new ThreadRtpSenderH265<>(
							AvStreamIncomingFromMq.class,
							FrameGrabberVideoH26xFromMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH265
						);
				};
		}

	}

}
