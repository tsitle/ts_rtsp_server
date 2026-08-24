package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * (Muxed) File Container Stream Source.
 */
public final class RtspSrvConfigStreamInputDmxAf implements Cloneable {

	public static class SectionTranscodeAudio implements Cloneable {
		public static final int AUDIO_BITRATE_KBPS_MIN = 8;
		public static final int AUDIO_BITRATE_KBPS_MAX = 224;  // higher bitrates cause problems with Opus @48kHz and 2 channels

		/** Codec used for the stream (default is AAC) */
		@Expose
		private @NonNull ConfigTcCodec codec;
		/** Audio samplerate in Hz (default is 48000) */
		@Expose
		private @NonNull Integer audioSamplerateHz;
		/** Audio channel count (default is 2) */
		@Expose
		private @NonNull Integer audioChannelCount;
		/** Audio bitrate in kilobits per second (default is 192) */
		@Expose
		private @NonNull Integer audioBitrateKbps;

		public SectionTranscodeAudio() {
			//noinspection DataFlowIssue
			this.codec = null;
			//noinspection DataFlowIssue
			this.audioSamplerateHz = null;
			//noinspection DataFlowIssue
			this.audioChannelCount = null;
			//noinspection DataFlowIssue
			this.audioBitrateKbps = null;

			postProcess();
		}

		public @NonNull ConfigTcCodec getCodec() {
			return codec;
		}

		public @NonNull SampleRateEnum getAudioSampleRate() {
			return SampleRateEnum.of(audioSamplerateHz);
		}

		public byte getAudioChannelCount() {
			return (byte)((int)audioChannelCount);
		}

		public int getAudioBitrateKbps() {
			return audioBitrateKbps;
		}

		void postProcess() {
			//noinspection ConstantValue
			if (codec == null) {
				codec = ConfigTcCodec.AACLC;
			}
			//noinspection ConstantValue
			if (audioSamplerateHz == null || audioSamplerateHz < 1) {
				audioSamplerateHz = 48000;
			}
			//noinspection ConstantValue
			if (audioChannelCount == null || audioChannelCount < 1) {
				audioChannelCount = 2;
			}
			//noinspection ConstantValue
			if (audioBitrateKbps == null || audioBitrateKbps < 1) {
				audioBitrateKbps = 192;
			}
		}

		void validate(@NonNull String errMsgSuffix) throws ConfigInvalidException {
			final String FNC_NAME = getClass().getSimpleName() + ".validate()";

			if (! getCodec().isAudio()) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Codec" + errMsgSuffix);
			}
			if (getAudioSampleRate() == SampleRateEnum.UNKNOWN) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Sample Rate" + errMsgSuffix);
			}
			if (getAudioChannelCount() < 1 || getAudioChannelCount() > 2) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Channel Count (min=1, max=2)" + errMsgSuffix);
			}
			if (getAudioBitrateKbps() < AUDIO_BITRATE_KBPS_MIN || getAudioBitrateKbps() > AUDIO_BITRATE_KBPS_MAX) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Bitrate (min=" + AUDIO_BITRATE_KBPS_MIN +
						", max=" + AUDIO_BITRATE_KBPS_MAX + ")" + errMsgSuffix);
			}
		}

		@Override
		public @NonNull SectionTranscodeAudio clone() {
			try {
				return (SectionTranscodeAudio)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(codec.ordinal());
			baos.write(audioSamplerateHz);
			baos.write(audioChannelCount);
			baos.write(audioBitrateKbps);
			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	/** Path to the media files */
	@Expose
	private @NonNull String folder;
	/** Transcoding settings */
	@Expose
	private @NonNull SectionTranscodeAudio transcode;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputDmxAf() {
		this.folder = "";
		this.transcode = new SectionTranscodeAudio();

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspSrvConfigStreamInputDmxAf of(@NonNull ProUri uri) {
		RtspSrvConfigStreamInputDmxAf resObj = new RtspSrvConfigStreamInputDmxAf();
		resObj.folder = uri.getPath().orElse("");
		resObj.postProcess();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull String getFolderPath() {
		checkPostProcessed();
		return folder;
	}

	public @NonNull ProUri getInputUri() {
		checkPostProcessed();
		if (! folder.isBlank()) {
			try {
				return ProUri.ofFile(folder);
			} catch (ProUriInvalidUriException e) {
				throw new IllegalStateException("could not create ProUri from folder: '" + folder + "'");
			}
		}
		throw new IllegalStateException("folder is blank");
	}

	public @NonNull ConfigTcCodec getTcCodec() {
		checkPostProcessed();
		return transcode.getCodec();
	}

	public @NonNull SampleRateEnum getTcAudioSampleRate() {
		checkPostProcessed();
		return transcode.getAudioSampleRate();
	}

	public byte getTcAudioChannelCount() {
		checkPostProcessed();
		return transcode.getAudioChannelCount();
	}

	public int getTcAudioBitrateKbps() {
		checkPostProcessed();
		return transcode.getAudioBitrateKbps();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputDmxAf clone() {
		checkPostProcessed();
		try {
			RtspSrvConfigStreamInputDmxAf cloned = (RtspSrvConfigStreamInputDmxAf)super.clone();
			cloned.transcode = transcode.clone();
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(folder.getBytes());
			baos.write(transcode.hashSum().getBytes());
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamInputDmxAf that)) {
			return false;
		}
		return hashSum().equals(that.hashSum());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration.
	 */
	void postProcess() {
		internalHasBeenPostProcessed = true;

		//
		//noinspection ConstantValue
		if (folder == null || folder.isBlank()) {
			folder = "";
		}
		//
		//noinspection ConstantValue
		if (transcode == null) {
			transcode = new SectionTranscodeAudio();
		} else {
			transcode.postProcess();
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String extIdStr, @NonNull Path dataDirPath) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		final String errMsgSuffix = " for DMX AF Source ID '" + extIdStr + "'";
		if (folder.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Folder Path found" + errMsgSuffix);
		}
		if (! (dataDirPath.resolve(folder).toFile().exists() && dataDirPath.resolve(folder).toFile().isDirectory())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Folder Path '" + folder + "'" + errMsgSuffix +
					" - not found");
		}
		folder = dataDirPath.resolve(folder).toString();

		//
		transcode.validate(errMsgSuffix);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
