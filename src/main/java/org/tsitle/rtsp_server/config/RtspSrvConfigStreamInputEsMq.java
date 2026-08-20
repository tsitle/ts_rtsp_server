package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.constants.MqConstants;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Message Queue Elementary-Stream Source.
 */
public final class RtspSrvConfigStreamInputEsMq implements Cloneable {

	/** Username and password for the Message Queue, separated by a colon */
	@Expose
	private @NonNull String userAndPassword;
	/** Host and port of the Message Queue, separated by a colon */
	@Expose
	private @NonNull String hostAndPort;
	/** Resource Group and Channel of the Message Queue, separated by a colon */
	@Expose
	private @NonNull String resourceGroupAndChannel;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputEsMq() {
		this.userAndPassword = "";
		this.hostAndPort = "";
		this.resourceGroupAndChannel = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull ProUri getInputUri() {
		checkPostProcessed();
		try {
			String tmpRscGrpAndChan = resourceGroupAndChannel.replace(":", "/");
			return ProUri.of("https://" + hostAndPort + "/" +
					MqConstants.MQ_URL_PATH_PREFIX + tmpRscGrpAndChan + MqConstants.MQ_URL_PATH_SUFFIX);
		} catch (ProUriInvalidUriException e) {
			throw new IllegalStateException("could not create ProUri for MQ");
		}
	}

	public @NonNull String getHost() {
		checkPostProcessed();
		return hostAndPort.split(":")[0];
	}

	public int getPort() {
		checkPostProcessed();
		try {
			int resI = Integer.parseInt(hostAndPort.split(":")[1]);
			if (resI < 1 || resI > 65535) {
				return -1;
			}
			return resI;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public @NonNull String getUsername() {
		checkPostProcessed();
		return userAndPassword.split(":")[0];
	}

	public @NonNull String getPassword() {
		checkPostProcessed();
		return userAndPassword.split(":")[1];
	}

	public @NonNull String getRscGroup() {
		checkPostProcessed();
		return resourceGroupAndChannel.split(":")[0];
	}

	public @NonNull String getRscChannel() {
		checkPostProcessed();
		return resourceGroupAndChannel.split(":")[1]
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_LONG, MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_SHORT)
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_LONG, MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_SHORT);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputEsMq clone() {
		checkPostProcessed();
		try {
			return (RtspSrvConfigStreamInputEsMq)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(userAndPassword.getBytes());
			baos.write(hostAndPort.getBytes());
			baos.write(resourceGroupAndChannel.getBytes());
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamInputEsMq that)) {
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
		if (userAndPassword == null) {
			userAndPassword = "";
		}
		//noinspection ConstantValue
		if (hostAndPort == null) {
			hostAndPort = "";
		}
		//noinspection ConstantValue
		if (resourceGroupAndChannel == null) {
			resourceGroupAndChannel = "";
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String extIdStr) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		final String errMsgPrefix = FNC_NAME + ": Invalid MQ settings for Sub-Stream Source ID '" + extIdStr + "' - ";

		if (! userAndPassword.contains(":")) {
			throw new ConfigInvalidException(errMsgPrefix + "'userAndPassword' must contain username and password separated by a colon");
		}
		if (userAndPassword.split(":").length != 2) {
			throw new ConfigInvalidException(errMsgPrefix + "'userAndPassword' must contain only one colon");
		}
		if (! hostAndPort.contains(":")) {
			hostAndPort = hostAndPort + ":443";  // default HTTPS port
		}
		if (hostAndPort.split(":").length != 2) {
			throw new ConfigInvalidException(errMsgPrefix + "'hostAndPort' must contain only one colon");
		}
		hostAndPort = hostAndPort.toLowerCase();
		if (! resourceGroupAndChannel.contains(":")) {
			throw new ConfigInvalidException(errMsgPrefix + "'resourceGroupAndChannel' must contain rsc group and channel separated by a colon");
		}
		if (resourceGroupAndChannel.split(":").length != 2) {
			throw new ConfigInvalidException(errMsgPrefix + "'resourceGroupAndChannel' must contain only one colon");
		}
		resourceGroupAndChannel = resourceGroupAndChannel.toLowerCase();
		//
		if (getHost().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "host must not be empty");
		}
		if (getPort() < 0) {
			throw new ConfigInvalidException(errMsgPrefix + "port must not be in range 1-65535");
		}
		if (getUsername().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "username must not be empty");
		}
		if (getPassword().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "password must not be empty");
		}
		if (getRscGroup().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "resource group must not be empty");
		}
		if (getRscChannel().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "resource channel must not be empty");
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
