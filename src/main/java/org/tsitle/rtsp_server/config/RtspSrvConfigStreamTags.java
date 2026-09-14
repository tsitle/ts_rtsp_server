package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * RTSP Stream Tags.
 */
public final class RtspSrvConfigStreamTags implements Cloneable {

	private static final int TAG_MAX_LENGTH = 255;

	/** Name of the RTSP stream */
	@Expose
	private @NonNull String streamName;
	/** Description of the RTSP stream */
	@Expose
	private @NonNull String streamDesc;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamTags() {
		this.streamName = "";
		this.streamDesc = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getStreamName() {
		checkPostProcessed();
		return streamName;
	}

	public @NonNull String getStreamDesc() {
		checkPostProcessed();
		return streamDesc;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamTags clone() {
		checkPostProcessed();
		try {
			return (RtspSrvConfigStreamTags)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(streamName.getBytes());
			baos.write(streamDesc.getBytes());
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamTags that)) {
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
		if (streamName == null || streamName.isBlank()) {
			streamName = "";
		}
		//noinspection ConstantValue
		if (streamDesc == null || streamDesc.isBlank()) {
			streamDesc = "";
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String extIdStr) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		streamName = streamName.strip();
		validateTag(FNC_NAME, extIdStr, "Name", streamName);
		streamDesc = streamDesc.strip();
		validateTag(FNC_NAME, extIdStr, "Description", streamDesc);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateTag(
				@NonNull String fncName,
				@NonNull String extIdStr,
				@NonNull String desc,
				@NonNull String value
			) throws ConfigInvalidException {
		final String errMsgSuffix = " in Stream ID '" + extIdStr + "'";
		if (value.length() > TAG_MAX_LENGTH) {
			throw new ConfigInvalidException(fncName + ": Stream Tag '" + desc + "' too long " +
					"(is=" + value.length() + ", max=" + TAG_MAX_LENGTH + ") " + errMsgSuffix);
		}
		String sanitized = value.replaceAll("[^\\x20-\\x7E]", "");
		if (! sanitized.equals(value)) {
			throw new ConfigInvalidException(fncName + ": Stream Tag '" + desc + "' contains invalid characters " +
					errMsgSuffix);
		}
	}

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
