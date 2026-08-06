package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpH265;
import org.tsitle.rtsp_server.threads.rtp.codec_v_h26x.ThreadRtpSenderH265;

public final class BuilderThreadRtpSenderH265 {

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH265<?>> {

		// Thread-specific fields
		private final ParamsThreadDpH265 threadParamsH265 = new ParamsThreadDpH265();

		// Fluent setters
		/*
		 * Future H265-only fields go here, e.g.,
		 * public @NonNull Builder h265Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderH265<?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsH265.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderH265<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsVideo,
					threadParamsH265
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
