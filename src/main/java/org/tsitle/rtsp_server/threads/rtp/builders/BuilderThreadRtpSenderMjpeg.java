package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoMjpegFromFile;
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

			if (threadParamsCommon.getIsEsSourceFromFile()) {
				return new ThreadRtpSenderMjpeg<>(
						AvStreamIncomingFromFile.class,
						FrameGrabberVideoMjpegFromFile.class,
						threadParamsCommon,
						threadParamsVideo,
						threadParamsMjpeg
					);
			}
			throw new RuntimeException("Not implemented");
		}

	}

}
