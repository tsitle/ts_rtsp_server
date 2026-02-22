package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp.threads.rtp.codec_v_mjpeg.ThreadRtpSenderMjpeg;

import java.io.FileNotFoundException;

public class BuilderThreadRtpSenderMjpeg {

	public static Builder builder() { return new Builder(); }

	public static final class Builder extends BuilderThreadRtpSenderVideoBase<Builder, ThreadRtpSenderMjpeg> {

		// Thread-specific fields
		private final ParamsThreadRtpSenderMjpeg threadParamsMjpeg = new ParamsThreadRtpSenderMjpeg();

		// Fluent setters
		/*
		 * Future MJPEG-only fields go here, e.g.,
		 * public Builder mjpegQuality(int q) { ...; return self(); }
		 */

		//
		@Override
		public ThreadRtpSenderMjpeg build() throws FileNotFoundException {
			validateCommon();
			validateVideoCommon();
			threadParamsMjpeg.validate();

			return new ThreadRtpSenderMjpeg(threadParamsCommon, threadParamsVideo, threadParamsMjpeg);
		}

	}

}
