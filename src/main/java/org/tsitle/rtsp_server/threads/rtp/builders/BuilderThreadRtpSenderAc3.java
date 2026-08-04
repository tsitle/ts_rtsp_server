package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsFile;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_dataprov.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsMq;
import org.tsitle.rtsp_server.threads.rtp.codec_a_ac3.ThreadRtpSenderAc3;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAc3;

public final class BuilderThreadRtpSenderAc3 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderAc3<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadDpAc3 threadParamsAc3 = new ParamsThreadDpAc3();

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

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberAudioAc3FromEsFile.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
					default -> new ThreadRtpSenderAc3<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberAudioAc3FromEsMq.class,
							threadParamsCommon,
							threadParamsAudio,
							threadParamsAc3
						);
				};
		}

	}

}
