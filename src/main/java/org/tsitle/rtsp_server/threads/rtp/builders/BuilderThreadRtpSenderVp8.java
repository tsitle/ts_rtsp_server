package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.codec_v_vpx.FrameGrabberVideoVp8FromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_v_vpx.FrameGrabberVideoVp8FromEsMq;
import org.tsitle.rtsp_server.threads.rtp.codec_v_vpx.ThreadRtpSenderVp8;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVp8;

public final class BuilderThreadRtpSenderVp8 {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderVp8<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderVp8 threadParamsVp8 = new ParamsThreadRtpSenderVp8();

		// Fluent setters
		/*
		 * Future MJPEG-only fields go here, e.g.,
		 * public Builder mjpegQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderVp8<?, ?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsVp8.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderVp8<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberVideoVp8FromEsFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsVp8
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderVp8<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsVp8
						);
					default -> new ThreadRtpSenderVp8<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberVideoVp8FromEsMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsVp8
						);
				};
		}

	}

}
