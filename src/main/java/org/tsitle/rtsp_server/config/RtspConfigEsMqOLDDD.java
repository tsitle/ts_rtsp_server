package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.constants.MqConstants;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.net.URI;

/**
 * Message Queue settings within an Elementary-Stream Source.
 */
public final class RtspConfigEsMqOLDDD implements Cloneable {

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

	public RtspConfigEsMqOLDDD() {
		this.userAndPassword = "";
		this.hostAndPort = "";
		this.resourceGroupAndChannel = "";

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		String tmpRscGrpAndChan = resourceGroupAndChannel.replace(":", "/");
		return URI.create("https://" + hostAndPort + "/" +
				MqConstants.MQ_URL_PATH_PREFIX + tmpRscGrpAndChan + MqConstants.MQ_URL_PATH_SUFFIX);
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
	public @NonNull RtspConfigEsMqOLDDD clone() {
		try {
			RtspConfigEsMqOLDDD clone = (RtspConfigEsMqOLDDD)super.clone();
			//
			//noinspection StringOperationCanBeSimplified
			clone.userAndPassword = new String(userAndPassword);
			//noinspection StringOperationCanBeSimplified
			clone.hostAndPort = new String(hostAndPort);
			//noinspection StringOperationCanBeSimplified
			clone.resourceGroupAndChannel = new String(resourceGroupAndChannel);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Message Queue settings.
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
	 * Validate the Message Queue settings.
	 * @throws ConfigInvalidException If the settings are invalid
	 */
	void validate(@NonNull String extEsSrcId) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		final String errMsgPrefix = FNC_NAME + ": Invalid MQ settings for Elementary-Stream Source ID '" + extEsSrcId + "' - ";

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
		if (! resourceGroupAndChannel.contains(":")) {
			throw new ConfigInvalidException(errMsgPrefix + "'resourceGroupAndChannel' must contain rsc group and channel separated by a colon");
		}
		if (resourceGroupAndChannel.split(":").length != 2) {
			throw new ConfigInvalidException(errMsgPrefix + "'resourceGroupAndChannel' must contain only one colon");
		}
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
			throw new IllegalStateException("MQ settings have not been post-processed yet");
		}
	}

}
