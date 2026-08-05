package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_v_vpx.ThreadRtpSenderVp8;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVp8;

public final class BuilderThreadRtpSenderVp8 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderVp8<?>> {

		// Thread-specific fields
		private final ParamsThreadDpVp8 threadParamsVp8 = new ParamsThreadDpVp8();

		// Fluent setters
		/*
		 * Future MJPEG-only fields go here, e.g.,
		 * public Builder mjpegQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderVp8<?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsVp8.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderVp8<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsVideo,
					threadParamsVp8
				);
		}

	}

}
