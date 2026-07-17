package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAc3FromMq;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_ac3.ThreadRtpSenderAc3;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAc3;

public final class BuilderThreadRtpSenderAc3 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAc3<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderAc3 threadParamsAc3 = new ParamsThreadRtpSenderAc3();

		// Fluent setters
		/*
		 * Future AC3-only fields go here, e.g.,
		 * public Builder ac3Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderAc3<?, ?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsAc3.validate();

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromFile.class,
							FrameGrabberAudioAc3FromFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromFile.class,  // @TODO
							FrameGrabberAudioAc3FromFile.class,  // @TODO
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
					default -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromMq.class,
							FrameGrabberAudioAc3FromMq.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
				};
		}

	}

}
