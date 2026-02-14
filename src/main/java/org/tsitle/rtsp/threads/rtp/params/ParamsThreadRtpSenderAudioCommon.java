package org.tsitle.rtsp.threads.rtp.params;

import java.util.Optional;

public class ParamsThreadRtpSenderAudioCommon {

	/** Path to the audio file */
	private String audioFilePath;
	private boolean isSetAudioFilePath;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getAudioFilePath() { return Optional.ofNullable(audioFilePath); }
	public void setAudioFilePath(String audioFilePath) {
		this.audioFilePath = audioFilePath;
		this.isSetAudioFilePath = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtpSenderAudioCommon clone() {
		try {
			ParamsThreadRtpSenderAudioCommon clone = (ParamsThreadRtpSenderAudioCommon)super.clone();
			//
			if (audioFilePath != null) {
				//noinspection StringOperationCanBeSimplified
				clone.audioFilePath = new String(audioFilePath);
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetAudioFilePath, "audioFilePath");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(audioFilePath, "audioFilePath");
		if (audioFilePath.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "audioFilePath must not be empty");
		}
	}

	private static void requireIsSet(boolean v, @SuppressWarnings("SameParameterValue") String name) {
		final String errPrefix = ParamsThreadRtpSenderAudioCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtpSenderAudioCommon.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
