package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public abstract class BuilderThreadRtpSenderVideoBase<
			B extends BuilderThreadRtpSenderVideoBase<B, T>,
			T
		>
		extends BuilderThreadRtpSenderBase<B, T> {

	// Common Video thread fields
	protected final ParamsThreadRtpSenderVideoCommon threadParamsVideo = new ParamsThreadRtpSenderVideoCommon();

	// Fluent setters
	public B vidVideoFilePath(String v) { this.threadParamsVideo.setVideoFilePath(v); return self(); }

	//
	protected void validateVideoCommon() {
		threadParamsVideo.validate();
	}

}
