package org.tsitle.rtsp_server.threads.rtp.builders;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpPcm;
import org.tsitle.rtsp_server.threads.rtp.codec_a_pcm.ThreadRtpSenderPcm;

public final class BuilderThreadRtpSenderPcm {

	public static final class Builder extends BuilderThreadRtpSenderAudioBase<Builder, ThreadRtpSenderPcm<?>> {

		// Thread-specific fields
		private final ParamsThreadDpPcm threadParamsPcm = new ParamsThreadDpPcm();

		// Fluent setters
		public @NonNull Builder audPcmChannelCount(int v) { this.threadParamsPcm.setAudioChannelCount(v); return this; }
		public @NonNull Builder audPcmBitsPerSample(int v) { this.threadParamsPcm.setAudioBitsPerSample(v); return this; }
		public @NonNull Builder audPcmInputBigEndian(boolean v) { this.threadParamsPcm.setIsAudioInputBigEndian(v); return this; }
		@SuppressWarnings("UnusedReturnValue")
		public @NonNull Builder audPcmCodec(RtpPacketType v) { this.threadParamsPcm.setAudioCodec(v); return this; }

		//
		@Override
		public @NonNull ThreadRtpSenderPcm<?> build() {
			validateCommon();
			validateAudioCommon();
			threadParamsPcm.validate();

			RtspProtoEsSourceType esSourceType = threadParamsCommon.getEsSourceType().orElseThrow();
			return new ThreadRtpSenderPcm<>(
					BuilderThreadRtpSenderHelper.getAvStreamIncomingType(esSourceType),
					threadParamsCommon,
					threadParamsAudio,
					threadParamsPcm
				);
		}

	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull Builder builder() { return new Builder(); }

}
