package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.rtsp_server.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAac;

public class BuilderThreadRtpSenderAac {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAac<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderAac threadParamsAac = new ParamsThreadRtpSenderAac();

		// Fluent setters
		/*
		 * Future AAC-only fields go here, e.g.,
		 * public Builder aacQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderAac<?, ?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsAac.validate();

			if (threadParamsCommon.getIsStreamSourceFromFile()) {
				return new ThreadRtpSenderAac<>(
						AvStreamIncomingFromFile.class,
						AudioStreamOutgoingAacFromFile.class,
						threadParamsCommon,
						threadParamsAudio,
						threadParamsAac
					);
			}
			throw new RuntimeException("Not implemented");
		}

	}

}
