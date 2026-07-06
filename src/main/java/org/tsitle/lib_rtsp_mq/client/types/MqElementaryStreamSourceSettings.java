package org.tsitle.lib_rtsp_mq.client.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_rtsp_mq.common.constants.MqConstants;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;

import java.net.URI;

/**
 * Message Queue settings for an Elementary-Stream Source.
 */
public final class MqElementaryStreamSourceSettings implements Cloneable {

	/** Username for the Message Queue */
	private final @NonNull String userName;
	/** Password for the Message Queue */
	private final @NonNull String userPassword;
	/** Hostname of the Message Queue */
	private final @NonNull String hostname;
	/** Port of the Message Queue */
	private @NonNull RtspProtoSocketPortNr port = RtspProtoSocketPortNr.ofEmpty();
	/** Resource Group of the Message Queue */
	private final @NonNull String resourceGroup;
	/** Resource Channel of the Message Queue */
	private final @NonNull String resourceChannel;

	public MqElementaryStreamSourceSettings(
				@NonNull String userName,
				@NonNull String userPassword,
				@NonNull String hostname,
				@NonNull RtspProtoSocketPortNr port,
				@NonNull String resourceGroup,
				@NonNull String resourceChannel
			) {
		this.userName = userName;
		this.userPassword = userPassword;
		this.hostname = hostname;
		this.port.copyFrom(port);
		this.resourceGroup = resourceGroup;
		this.resourceChannel = resourceChannel;

		validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull URI getInputUri() {
		String tmpRscGrpAndChan = resourceGroup + "/" + getRscChannelForUrl();
		return URI.create("https://" + hostname + ":" + Integer.toUnsignedString(port.getPort16bit().orElseThrow()) + "/" +
				MqConstants.MQ_URL_PATH_PREFIX + tmpRscGrpAndChan + MqConstants.MQ_URL_PATH_SUFFIX);
	}

	public @NonNull String getHostname() {
		return hostname;
	}

	public @NonNull RtspProtoSocketPortNr getPort() {
		return port.clone();
	}

	public @NonNull String getUsername() {
		return userName;
	}

	public @NonNull String getPassword() {
		return userPassword;
	}

	public @NonNull String getRscGroup() {
		return resourceGroup;
	}

	public @NonNull String getRscChannel() {
		return resourceChannel
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_LONG, MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_SHORT)
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_LONG, MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_SHORT);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull MqElementaryStreamSourceSettings clone() {
		try {
			MqElementaryStreamSourceSettings clone = (MqElementaryStreamSourceSettings)super.clone();
			clone.port = port.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String getRscChannelForUrl() {
		return resourceChannel
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_SHORT, MqConstants.MQ_RSC_CHANNEL_RECV_VIDEO_LONG)
				.replace(MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_SHORT, MqConstants.MQ_RSC_CHANNEL_RECV_AUDIO_LONG);
	}

	/**
	 * Validate the Message Queue settings.
	 * @throws IllegalArgumentException If the settings are invalid
	 */
	private void validate() throws IllegalArgumentException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		final String errMsgPrefix = FNC_NAME + ": Invalid MQ settings - ";

		if (hostname.isBlank()) {
			throw new IllegalArgumentException(errMsgPrefix + "host must not be empty");
		}
		if (port.isEmpty()) {
			throw new IllegalArgumentException(errMsgPrefix + "port must not be empty");
		}
		if (userName.isBlank()) {
			throw new IllegalArgumentException(errMsgPrefix + "username must not be empty");
		}
		if (userPassword.isBlank()) {
			throw new IllegalArgumentException(errMsgPrefix + "password must not be empty");
		}
		if (resourceGroup.isBlank()) {
			throw new IllegalArgumentException(errMsgPrefix + "resource group must not be empty");
		}
		if (resourceChannel.isBlank()) {
			throw new IllegalArgumentException(errMsgPrefix + "resource channel must not be empty");
		}
	}

}
