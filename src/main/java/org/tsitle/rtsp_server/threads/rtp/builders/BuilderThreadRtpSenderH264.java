package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_dataprov.avstreams.codec_v_h26x.FrameGrabberVideoH26xFromEsFile;
import org.tsitle.lib_dataprov.avstreams.codec_v_h26x.FrameGrabberVideoH26xFromEsMq;
import org.tsitle.rtsp_server.threads.rtp.codec_v_h26x.ThreadRtpSenderH264;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpH264;

public final class BuilderThreadRtpSenderH264 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderH264<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadDpH264 threadParamsH264 = new ParamsThreadDpH264();

		// Fluent setters
		/*
		 * Future H264-only fields go here, e.g.,
		 * public Builder h264Quality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderH264<?, ?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsH264.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberVideoH26xFromEsFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
					default -> new ThreadRtpSenderH264<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberVideoH26xFromEsMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsH264
						);
				};
		}

	}

}
