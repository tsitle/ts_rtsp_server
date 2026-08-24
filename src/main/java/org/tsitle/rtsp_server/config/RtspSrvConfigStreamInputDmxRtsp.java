package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * (Muxed) RTSP Stream Source.
 */
public final class RtspSrvConfigStreamInputDmxRtsp implements Cloneable {

	/** URL to the RTSP stream */
	@Expose
	private @NonNull String url;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputDmxRtsp() {
		this.url = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspSrvConfigStreamInputDmxRtsp of(@NonNull ProUri uri) {
		RtspSrvConfigStreamInputDmxRtsp resObj = new RtspSrvConfigStreamInputDmxRtsp();
		resObj.url = uri.getUriString().orElse("");
		resObj.postProcess();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getUrl() {
		checkPostProcessed();
		return url;
	}

	public @NonNull ProUri getInputUri() {
		checkPostProcessed();
		try {
			ProUri resObj = ProUri.of(url);
			if (resObj.getScheme().orElse(ProUri.Scheme.NONE) != ProUri.Scheme.RTSP &&
					resObj.getScheme().orElse(ProUri.Scheme.NONE) != ProUri.Scheme.RTSPS) {
				throw new IllegalStateException("URL is not for RTSP/RTSPS: '" + url + "'");
			}
			return resObj;
		} catch (ProUriInvalidUriException e) {
			throw new IllegalStateException("could not create ProUri from URL: '" + url + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputDmxRtsp clone() {
		checkPostProcessed();
		try {
			return (RtspSrvConfigStreamInputDmxRtsp)super.clone();
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
		if (! (o instanceof RtspSrvConfigStreamInputDmxRtsp that)) {
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
		final String errMsgSuffix = " for DMX RTSP Source ID '" + extIdStr + "'";
		if (url.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No URL found" + errMsgSuffix);
		}
		try {
			ProUri tmpProUri = ProUri.of(url);
			if (tmpProUri.getScheme().orElse(ProUri.Scheme.NONE) != ProUri.Scheme.RTSP &&
					tmpProUri.getScheme().orElse(ProUri.Scheme.NONE) != ProUri.Scheme.RTSPS) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP URL '" + url + "'" + errMsgSuffix +
						" - unsupported protocol");
			}
		} catch (ProUriInvalidUriException e) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid RTSP URL '" + url + "'" + errMsgSuffix +
					" - " + e.getMessage());
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
