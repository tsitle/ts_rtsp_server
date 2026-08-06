package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMjpeg;
import org.tsitle.rtsp_server.threads.rtp.codec_v_mjpeg.ThreadRtpSenderMjpeg;

public final class BuilderThreadRtpSenderMjpeg {

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderMjpeg<?>> {

		// Thread-specific fields
		private final ParamsThreadDpMjpeg threadParamsMjpeg = new ParamsThreadDpMjpeg();

		// Fluent setters
		/*
		 * Future MJPEG-only fields go here, e.g.,
		 * public @NonNull Builder mjpegQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderMjpeg<?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsMjpeg.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderMjpeg<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsVideo,
					threadParamsMjpeg
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
