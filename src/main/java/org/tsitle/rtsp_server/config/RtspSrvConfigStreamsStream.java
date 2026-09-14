package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Input Source for RTSP streams.
 */
public final class RtspSrvConfigStreamsStream implements Cloneable {

	/** Is this Stream enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Does this Stream need authentication? (default: true) */
	@Expose
	private @NonNull Boolean needsAuthentication;
	/** Allowed User Account Groups for this Stream */
	@Expose
	private @NonNull Set<@NonNull String> allowedUserAccountGroups;
	/** Does this Stream need encryption? (default: true) */
	@Expose
	private @NonNull Boolean needsEncryption;
	/** Sub-Stream IDs to be used by this Stream */
	@Expose
	private @NonNull Set<@NonNull String> subStreamIds;
	/** Tags */
	@Expose
	private @NonNull RtspSrvConfigStreamTags tags;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamsStream() {
		this.enabled = true;
		this.needsAuthentication = true;
		this.allowedUserAccountGroups = new HashSet<>();
		this.needsEncryption = true;
		this.subStreamIds = new HashSet<>();
		this.tags = new RtspSrvConfigStreamTags();

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspSrvConfigStreamsStream createVirtual(
				@NonNull RtspSrvConfigStreamsStream realStreamCfgObj,
				@NonNull Set<@NonNull String> virtExternalEsIds
			) {
		RtspSrvConfigStreamsStream resObj = new RtspSrvConfigStreamsStream();
		resObj.enabled = realStreamCfgObj.enabled;
		resObj.needsAuthentication = realStreamCfgObj.needsAuthentication;
		resObj.allowedUserAccountGroups.addAll(realStreamCfgObj.allowedUserAccountGroups);
		resObj.needsEncryption = realStreamCfgObj.needsEncryption;
		resObj.subStreamIds.addAll(virtExternalEsIds);
		resObj.internalHasBeenPostProcessed = true;
		resObj.tags = realStreamCfgObj.tags.clone();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean getEnabled() {
		checkPostProcessed();
		return enabled;
	}

	public boolean getNeedsAuthentication() {
		checkPostProcessed();
		return needsAuthentication;
	}

	public @NonNull Set<@NonNull String> getAllowedUserAccountGroups() {
		checkPostProcessed();
		return Set.copyOf(allowedUserAccountGroups);
	}

	public boolean getNeedsEncryption() {
		checkPostProcessed();
		return needsEncryption;
	}

	public @NonNull Set<@NonNull String> getSubStreamIds() {
		checkPostProcessed();
		return Set.copyOf(subStreamIds);
	}

	public @NonNull RtspSrvConfigStreamTags getTags() {
		checkPostProcessed();
		return tags.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamsStream clone() {
		checkPostProcessed();
		try {
			RtspSrvConfigStreamsStream clone = (RtspSrvConfigStreamsStream)super.clone();
			//noinspection ConstantValue
			clone.enabled = (enabled != null && enabled);
			//noinspection ConstantValue
			clone.needsAuthentication = (needsAuthentication != null && needsAuthentication);
			clone.allowedUserAccountGroups = new HashSet<>();
			//noinspection ConstantValue
			if (allowedUserAccountGroups != null && ! allowedUserAccountGroups.isEmpty()) {
				clone.allowedUserAccountGroups.addAll(allowedUserAccountGroups);
			}
			//noinspection ConstantValue
			clone.needsEncryption = (needsEncryption != null && needsEncryption);
			clone.subStreamIds = new HashSet<>();
			//noinspection ConstantValue
			if (subStreamIds != null && ! subStreamIds.isEmpty()) {
				clone.subStreamIds.addAll(subStreamIds);
			}
			//noinspection ConstantValue
			if (tags != null) {
				clone.tags = tags.clone();
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(enabled ? 1 : 0);
			baos.write(needsAuthentication ? 1 : 0);
			baos.write(String.join(",", allowedUserAccountGroups).getBytes());
			baos.write(String.join(",", subStreamIds).getBytes());
			baos.write(needsEncryption ? 1 : 0);
			//noinspection ConstantValue
			if (tags != null) { baos.write(tags.hashSum().getBytes()); }
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamsStream that)) {
			return false;
		}
		return hashSum().equals(that.hashSum());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void postProcess() throws ConfigInvalidException {
		internalHasBeenPostProcessed = true;

		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}

		//
		allowedUserAccountGroups = RewriteSetStringHelper.removeNullAndBlank(allowedUserAccountGroups, true);

		//
		subStreamIds = RewriteSetStringHelper.removeNullAndBlank(subStreamIds, true);

		//noinspection ConstantValue
		if (tags == null) {
			tags = new RtspSrvConfigStreamTags();
		}
		tags.postProcess();
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(
				@NonNull String extIdStr,
				@NonNull Set<@NonNull String> userAccountGroupIds
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		// ---------------------------------------------

		if (needsAuthentication && allowedUserAccountGroups.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty Allowed User Account Groups list" +
					" used in Stream ID '" + extIdStr + "'");
		}
		for (String allowedUag : allowedUserAccountGroups) {
			if (! userAccountGroupIds.contains(allowedUag)) {
				throw new ConfigInvalidException(FNC_NAME + ": Non-existing User Account Group '" + allowedUag + "'" +
						" used in Stream ID '" + extIdStr + "'");
			}
		}

		// ---------------------------------------------

		if (subStreamIds.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty Sub-Stream IDs list" +
					" used in Stream ID '" + extIdStr + "'");
		}

		// ---------------------------------------------

		tags.validate(extIdStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}
