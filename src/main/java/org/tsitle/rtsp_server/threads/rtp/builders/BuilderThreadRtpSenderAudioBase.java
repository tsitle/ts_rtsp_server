package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAudioCommon;

public abstract class BuilderThreadRtpSenderAudioBase<
			B extends BuilderThreadRtpSenderAudioBase<B, T>,
			T
		>
		extends BuilderThreadRtpSenderBase<B, T> {

	// Common Audio thread fields
	protected final ParamsThreadDpAudioCommon threadParamsAudio = new ParamsThreadDpAudioCommon();

	// Fluent setters
	public @NonNull BuilderThreadRtpSenderAudioBase<B, T> audComRtpAudioSpf(int v) {
		this.threadParamsAudio.setAudioSpf(v);
		return this;
	}
	public @NonNull BuilderThreadRtpSenderAudioBase<B, T> audComSamplerate(@NonNull SampleRateEnum v) {
		this.threadParamsAudio.setAudioSamplerate(v);
		return this;
	}

	//
	protected void validateAudioCommon() {
		threadParamsAudio.validate();
	}

}
