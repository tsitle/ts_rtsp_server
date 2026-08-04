package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_dataprov.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsMq;
import org.tsitle.rtsp_server.threads.rtp.codec_a_aac.ThreadRtpSenderAac;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAac;

public final class BuilderThreadRtpSenderAac {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAac<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadDpAac threadParamsAac = new ParamsThreadDpAac();

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

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderAac<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioAacFromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAac
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderAac<>(
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
