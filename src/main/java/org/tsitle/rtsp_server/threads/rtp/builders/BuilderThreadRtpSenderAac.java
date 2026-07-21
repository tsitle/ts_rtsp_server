package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.rtsp_server.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsMq;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
import org.tsitle.rtsp_server.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAac;

public final class BuilderThreadRtpSenderAac {

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

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderAac<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioAacFromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAac
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderAac<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAac
						);
					default -> new ThreadRtpSenderAac<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberAudioAacFromEsMq.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAac
						);
				};
		}

	}

}
