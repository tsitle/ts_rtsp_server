package org.tsitle.rtsp.threads.rtp;

import java.util.Optional;

public class ParamsThreadRtpSenderVideoCommon {

	/** Path to the video file */
	private String videoFilePath;
	private boolean isSetVideoFilePath;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getVideoFilePath() { return Optional.ofNullable(videoFilePath); }
	public void setVideoFilePath(String videoFilePath) {
		this.videoFilePath = videoFilePath;
		this.isSetVideoFilePath = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtpSenderVideoCommon clone() {
		try {
			ParamsThreadRtpSenderVideoCommon clone = (ParamsThreadRtpSenderVideoCommon)super.clone();
			//
			if (videoFilePath != null) {
				//noinspection StringOperationCanBeSimplified
				clone.videoFilePath = new String(videoFilePath);
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetVideoFilePath, "videoFilePath");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		requireNonNull(videoFilePath, "videoFilePath");
		if (videoFilePath.isEmpty()) {
			throw new IllegalArgumentException(errPrefix + "videoFilePath must not be empty");
		}
	}

	private static void requireIsSet(boolean v, @SuppressWarnings("SameParameterValue") String name) {
		final String errPrefix = ParamsThreadRtpSenderVideoCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtpSenderVideoCommon.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}
