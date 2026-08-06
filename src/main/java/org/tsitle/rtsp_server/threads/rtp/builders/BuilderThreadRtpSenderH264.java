package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_v_h26x.ThreadRtpSenderH264;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpH264;

public final class BuilderThreadRtpSenderH264 {

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH264<?>> {

		// Thread-specific fields
		private final ParamsThreadDpH264 threadParamsH264 = new ParamsThreadDpH264();

		// Fluent setters
		/*
		 * Future H264-only fields go here, e.g.,
		 * public @NonNull Builder h264Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderH264<?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsH264.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderH264<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsVideo,
					threadParamsH264
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
