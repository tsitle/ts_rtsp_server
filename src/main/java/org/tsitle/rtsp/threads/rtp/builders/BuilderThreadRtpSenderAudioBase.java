package org.tsitle.rtsp.threads.rtp.builders;

import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;

public abstract class BuilderThreadRtpSenderAudioBase<
			B extends BuilderThreadRtpSenderAudioBase<B, T>,
			T
		>
		extends BuilderThreadRtpSenderBase<B, T> {

	// Common Audio thread fields
	protected final ParamsThreadRtpSenderAudioCommon threadParamsAudio = new ParamsThreadRtpSenderAudioCommon();

	// Fluent setters
	public B audComRtpAudioSpf(int v) { this.threadParamsAudio.setRtpAudioSpf(v); return self(); }
	public B audComSamplerateHz(int v) { this.threadParamsAudio.setAudioSamplerateHz(v); return self(); }

	//
	protected void validateAudioCommon() {
		threadParamsAudio.validate();
	}

}
