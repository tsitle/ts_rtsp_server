package org.tsitle.rtsp_server.threads.rtp.builders;

import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;

public abstract class BuilderThreadRtpSenderVideoBase<
			B extends BuilderThreadRtpSenderVideoBase<B, T>,
			T
		>
		extends BuilderThreadRtpSenderBase<B, T> {

	// Common Video thread fields
	protected final ParamsThreadDpVideoCommon threadParamsVideo = new ParamsThreadDpVideoCommon();

	// Fluent setters
	/*
	 * Future Video-only fields go here, e.g.,
	 * public Builder videoQuality(int q) { ...; return self(); }
	 */

	//
	protected void validateVideoCommon() {
		threadParamsVideo.validate();
	}

}
