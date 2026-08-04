package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.codec_a_opus.FrameGrabberAudioOpusFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_opus.FrameGrabberAudioOpusFromEsMq;
import org.tsitle.rtsp_server.threads.rtp.codec_a_opus.ThreadRtpSenderOpus;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderOpus;

public final class BuilderThreadRtpSenderOpus {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderOpus<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderOpus threadParamsOpus = new ParamsThreadRtpSenderOpus();

		// Fluent setters
		/*
		 * Future Opus-only fields go here, e.g.,
		 * public Builder opsQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderOpus<?, ?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsOpus.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderOpus<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioOpusFromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsOpus
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderOpus<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsOpus
						);
					default -> new ThreadRtpSenderOpus<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberAudioOpusFromEsMq.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsOpus
						);
				};
		}

	}

}
