package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

/**
 * (Muxed) RTSP Stream Source.
 */
public final class RtspSrvConfigStreamInputMuxRtsp implements Cloneable {

	/** URL to the RTSP stream */
	@Expose
	private @NonNull String url;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputMuxRtsp() {
		this.url = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspSrvConfigStreamInputMuxRtsp of(@NonNull String url) {
		RtspSrvConfigStreamInputMuxRtsp resObj = new RtspSrvConfigStreamInputMuxRtsp();
		resObj.url = url.
				replace("http://", "rtsp://").
				replace("https://", "rtsps://");
		resObj.postProcess();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUrl() {
		checkPostProcessed();
		return url;
	}

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		if (url.startsWith("rtsp://")) {
			return URI.create(url.replace("rtsp://", "http://"));
		}
		if (url.startsWith("rtsps://")) {
			return URI.create(url.replace("rtsps://", "https://"));
		}
		throw new IllegalStateException("url is blank");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputMuxRtsp clone() {
		checkPostProcessed();
		try {
			return (RtspSrvConfigStreamInputMuxRtsp)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(url.getBytes());
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamInputMuxRtsp that)) {
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
		if (url == null || url.isBlank()) {
			url = "";
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
		final String errMsgSuffix = " for Muxed-Stream Source ID '" + extIdStr + "'";
		if (url.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No URL found" + errMsgSuffix);
		}
		if (! (url.startsWith("rtsp://") || url.startsWith("rtsps://"))) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP URL '" + url + "'" + errMsgSuffix +
					" - unsupported protocol");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
