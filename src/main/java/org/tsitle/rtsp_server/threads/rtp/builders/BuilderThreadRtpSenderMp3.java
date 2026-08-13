package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMp3;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_mp3.ThreadRtpSenderMp3;

public final class BuilderThreadRtpSenderMp3 {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderMp3<?>> {

		// Thread-specific fields
		private final ParamsThreadDpMp3 threadParamsMp3 = new ParamsThreadDpMp3();

		// Fluent setters
		/*
		 * Future MP3-only fields go here, e.g.,
		 * public @NonNull Builder mp3Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public @NonNull ThreadRtpSenderMp3<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsMp3.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderMp3<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsMp3
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
