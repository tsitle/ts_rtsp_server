package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_opus.ThreadRtpSenderOpus;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpOpus;

public final class BuilderThreadRtpSenderOpus {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderOpus<?>> {

		// Thread-specific fields
		private final ParamsThreadDpOpus threadParamsOpus = new ParamsThreadDpOpus();

		// Fluent setters
		/*
		 * Future Opus-only fields go here, e.g.,
		 * public @NonNull Builder opusQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderOpus<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsOpus.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderOpus<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsOpus
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
