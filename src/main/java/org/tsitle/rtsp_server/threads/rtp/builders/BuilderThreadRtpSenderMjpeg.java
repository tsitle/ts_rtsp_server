package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoMjpegFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoMjpegFromMq;
import org.tsitle.rtsp_server.config.RtspConfigEsSourceType;
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

			RtspConfigEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return switch (esSourceType) {
					case ST_ES_FILE -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromFile.class,
							FrameGrabberVideoMjpegFromFile.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
					case ST_DEMUX_MS_FILE -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromFile.class,  // @TODO
							FrameGrabberVideoMjpegFromFile.class,  // @TODO
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
					default -> new ThreadRtpSenderMjpeg<>(
							AvStreamIncomingFromMq.class,
							FrameGrabberVideoMjpegFromMq.class,
							threadParamsCommon,
							threadParamsVideo,
							threadParamsMjpeg
						);
				};
		}

	}

}
