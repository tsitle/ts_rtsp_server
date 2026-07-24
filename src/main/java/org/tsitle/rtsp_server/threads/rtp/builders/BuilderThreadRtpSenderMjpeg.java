package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsMq;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp_server.threads.rtp.codec_v_mjpeg.ThreadRtpSenderMjpeg;

public final class BuilderThreadRtpSenderMjpeg {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderMjpeg<?, ?>> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderMjpeg threadParamsMjpeg = new ParamsThreadRtpSenderMjpeg();

		// Fluent setters
		/*
		 * Future MJPEG-only fields go here, e.g.,
		 * public Builder mjpegQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderMjpeg<?, ?> build() {
			validateCommon();
			validateVideoCommon();
			threadParamsMjpeg.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromEsFile.class,
							FrameGrabberVideoMjpegFromEsFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
					case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromDemuxMs.class,
							FrameGrabberAvFromDemuxMs.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
					default -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromEsMq.class,
							FrameGrabberVideoMjpegFromEsMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
				};
		}

	}

}
