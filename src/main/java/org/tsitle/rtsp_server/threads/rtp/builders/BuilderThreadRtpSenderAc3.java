package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_ac3.ThreadRtpSenderAc3;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAc3;

public final class BuilderThreadRtpSenderAc3 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAc3<?>> {

		// Thread-specific fields
		private final ParamsThreadDpAc3 threadParamsAc3 = new ParamsThreadDpAc3();

		// Fluent setters
		/*
		 * Future AC3-only fields go here, e.g.,
		 * public Builder ac3Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderAc3<?> build() {
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

}
