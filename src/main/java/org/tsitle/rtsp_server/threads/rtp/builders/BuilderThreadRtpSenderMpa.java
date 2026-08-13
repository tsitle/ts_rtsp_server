package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMpa;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_mpeg.ThreadRtpSenderMpa;

public final class BuilderThreadRtpSenderMpa {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderMpa<?>> {

		// Thread-specific fields
		private final ParamsThreadDpMpa threadParamsMpa = new ParamsThreadDpMpa();

		// Fluent setters
		/*
		 * Future MPEG Audio fields go here, e.g.,
		 * public @NonNull Builder mpaQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderMpa<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsMpa.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderMpa<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsMpa
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
