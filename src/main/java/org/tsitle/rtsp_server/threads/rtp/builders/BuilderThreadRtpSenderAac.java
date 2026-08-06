package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAac;

public final class BuilderThreadRtpSenderAac {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAac<?>> {

		// Thread-specific fields
		private final ParamsThreadDpAac threadParamsAac = new ParamsThreadDpAac();

		// Fluent setters
		/*
		 * Future AAC-only fields go here, e.g.,
		 * public @NonNull Builder aacQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderAac<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsAac.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderAac<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsAac
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
