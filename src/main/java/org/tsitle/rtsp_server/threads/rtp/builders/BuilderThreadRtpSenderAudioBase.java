package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;

public abstract class BuilderThreadRtpSenderAudioBase<
			B extends BuilderThreadRtpSenderAudioBase<B, T>,
			T
		>
		extends BuilderThreadRtpSenderBase<B, T> {

	// Common Audio thread fields
	protected final ParamsThreadRtpSenderAudioCommon threadParamsAudio = new ParamsThreadRtpSenderAudioCommon();

	// Fluent setters
	public B audComRtpAudioSpf(int v) { this.threadParamsAudio.setRtpAudioSpf(v); return self(); }
	public B audComSamplerate(@NonNull SampleRateEnum v) { this.threadParamsAudio.setAudioSamplerate(v); return self(); }

	//
	protected void validateAudioCommon() {
		threadParamsAudio.validate();
	}

}
