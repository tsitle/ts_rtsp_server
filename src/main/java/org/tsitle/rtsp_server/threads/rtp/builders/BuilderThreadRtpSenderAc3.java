package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_ac3.ThreadRtpSenderAc3;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAc3;

public final class BuilderThreadRtpSenderAc3 {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAc3<?>> {

		// Thread-specific fields
		private final ParamsThreadDpAc3 threadParamsAc3 = new ParamsThreadDpAc3();

		// Fluent setters
		/*
		 * Future AC3-only fields go here, e.g.,
		 * public @NonNull Builder ac3Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderAc3<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsAc3.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderAc3<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsAc3
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
