package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * (Muxed) File Container Stream Source.
 */
public final class RtspSrvConfigStreamInputDmxFc implements Cloneable {

	/** Path to the media file */
	@Expose
	private @NonNull String filePath;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputDmxFc() {
		this.filePath = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspSrvConfigStreamInputDmxFc of(@NonNull ProUri uri) {
		RtspSrvConfigStreamInputDmxFc resObj = new RtspSrvConfigStreamInputDmxFc();
		resObj.filePath = uri.getPath().orElse("");
		resObj.postProcess();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull String getFilePath() {
		checkPostProcessed();
		return filePath;
	}

	public @NonNull ProUri getInputUri() {
		checkPostProcessed();
		if (! filePath.isBlank()) {
			try {
				return ProUri.ofFile(filePath);
			} catch (ProUriInvalidUriException e) {
				throw new IllegalStateException("could not create ProUri from filePath: '" + filePath + "'");
			}
		}
		throw new IllegalStateException("filePath is blank");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputDmxFc clone() {
		checkPostProcessed();
		try {
			return (RtspSrvConfigStreamInputDmxFc)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(filePath.getBytes());
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamInputDmxFc that)) {
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
		if (filePath == null || filePath.isBlank()) {
			filePath = "";
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
		final String errMsgSuffix = " for DMX FC Source ID '" + extIdStr + "'";
		if (filePath.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No File Path found" + errMsgSuffix);
		}
		if (! dataDirPath.resolve(filePath).toFile().exists()) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid File Path '" + filePath + "'" + errMsgSuffix +
					" - file not found");
		}
		filePath = dataDirPath.resolve(filePath).toString();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
