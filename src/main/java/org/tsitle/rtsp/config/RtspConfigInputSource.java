package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdInputSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Input Source for RTSP streams.
 */
public final class RtspConfigInputSource implements Cloneable {

	/** Input Source ID */
	@GsonAnnoExclude
	private @NonNull String id;
	/** Is this Input Source enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Does this Input Source need authentication? (default: true) */
	@Expose
	private @NonNull Boolean needsAuthentication;
	/** Allowed User Account Groups for this input source */
	@Expose
	private @NonNull Set<@NonNull String> allowedUserAccountGroups;
	/** Does this Input Source need encryption? (default: true) */
	@Expose
	private boolean needsEncryption;
	/** Stream Source IDs to be used by this input source */
	@Expose
	private @NonNull Set<@NonNull String> streamSourceIds;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Stream Source IDs to be used by this input source */
	@SuppressWarnings("FieldMayBeFinal")
	@GsonAnnoExclude
	private @NonNull Set<@NonNull Integer> internalStreamSourceIds;

	public RtspConfigInputSource() {
		this.id = "";
		this.enabled = true;
		this.needsAuthentication = true;
		this.allowedUserAccountGroups = new HashSet<>();
		this.needsEncryption = true;
		this.streamSourceIds = new HashSet<>();

		//noinspection DataFlowIssue
		this.internalStreamSourceIds = null;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getIdAsStr() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (id == null ? "" : id.strip());
	}
	public @NonNull RtspProtoIdInputSource getIdAsProtoId() {
		RtspProtoIdInputSource resObj = RtspProtoIdInputSource.of(getIdAsStr());
		resObj.writeProtect();
		return resObj;
	}
	public void setId(@NonNull String id) {
		//noinspection ConstantValue
		this.id = (id == null ? "" : id.strip());
	}

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

	public @NonNull Set<@NonNull Integer> getStreamSourceIds() {
		checkPostProcessed();
		return Set.copyOf(internalStreamSourceIds);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Input Source.
	 * @param mapStreamSourceIdExtToInt Map of Stream Source IDs (external) to their internal representation
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	public void postProcess(
				@NonNull Map<@NonNull String, @NonNull Integer> mapStreamSourceIdExtToInt
			) throws ConfigInvalidException {
		internalHasBeenPostProcessed = true;

		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}
		//
		//noinspection ConstantValue
		if (internalStreamSourceIds == null) {
			createInternalStreamSourcesMap(mapStreamSourceIdExtToInt);
		}

		//
		Set<@NonNull String> tmpNewUags = new HashSet<>();
		//noinspection ConstantValue
		if (allowedUserAccountGroups != null) {
			for (String allowedUag : allowedUserAccountGroups) {
				//noinspection ConstantValue
				if (allowedUag == null || allowedUag.isBlank()) {
					continue;
				}
				tmpNewUags.add(allowedUag.toLowerCase());
			}
		}
		allowedUserAccountGroups = tmpNewUags;
	}

	/**
	 * Validate the Input Source.
	 * @param streamSources Map of all Stream Sources
	 * @param mapStreamSourceIdIntToExt Map of Stream Source IDs (internal) to their external representation
	 * @param userAccountGroups Existing User Account Groups
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	public void validate(
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigStreamSource> streamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapStreamSourceIdIntToExt,
				@NonNull Set<@NonNull String> userAccountGroups
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		if (getIdAsStr().isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Input Source has no ID");
		}
		//noinspection ConstantValue
		if (streamSourceIds == null || internalStreamSourceIds.size() != streamSourceIds.size()) {
			throw new ConfigInvalidException(FNC_NAME + ": Not all Stream Source IDs could be mapped " +
					"for Input Source ID '" + id + "'");
		}
		Set<Integer> tmpIsSsIdSet = getStreamSourceIds();
		if (tmpIsSsIdSet.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Stream Sources found for Input Source ID '" + id + "'");
		}
		if (tmpIsSsIdSet.size() > 2) {
			throw new ConfigInvalidException(FNC_NAME + ": Input Source ID '" + id + "' contains more than two Stream Sources");
		}
		for (int tmpSsId : tmpIsSsIdSet) {
			if (! streamSources.containsKey(tmpSsId)) {
				String tmpExtSsId = mapStreamSourceIdIntToExt.get(tmpSsId);
				throw new ConfigInvalidException(FNC_NAME + ": Non-existing Stream Source ID '" + tmpExtSsId + "'" +
						" used for Input Source ID '" + id + "'");
			}
			if (! streamSources.get(tmpSsId).getEnabled()) {
				String tmpExtSsId = mapStreamSourceIdIntToExt.get(tmpSsId);
				throw new ConfigInvalidException(FNC_NAME + ": Disabled Stream Source ID '" + tmpExtSsId + "'" +
						" used for Input Source ID '" + id + "'");
			}
		}

		//
		if (needsAuthentication && allowedUserAccountGroups.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty allowed User Account Groups list" +
					" used in Input Source ID '" + id + "'");
		}
		for (String allowedUag : allowedUserAccountGroups) {
			if (! userAccountGroups.contains(allowedUag)) {
				throw new ConfigInvalidException(FNC_NAME + ": Non-existing User Account Group '" + allowedUag + "'" +
						" used in Input Source ID '" + id + "'");
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspConfigInputSource clone() {
		try {
			RtspConfigInputSource clone = (RtspConfigInputSource)super.clone();
			clone.allowedUserAccountGroups = new HashSet<>(allowedUserAccountGroups);
			clone.streamSourceIds = new HashSet<>(streamSourceIds);
			clone.internalStreamSourceIds = new HashSet<>(internalStreamSourceIds);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Input Source has not been post-processed yet");
		}
	}

	private void createInternalStreamSourcesMap(
				@NonNull Map<@NonNull String, @NonNull Integer> mapStreamSourceIdExtToInt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".createInternalStreamSourcesMap()";

		internalStreamSourceIds = new HashSet<>();
		//noinspection ConstantValue
		if (streamSourceIds == null) {
			return;
		}
		for (String entry : streamSourceIds) {
			//noinspection ConstantValue
			if (entry == null) {
				continue;
			}
			if (entry.isBlank()) {
				throw new ConfigInvalidException(FNC_NAME + ": Empty Stream Source ID for Input Source '" + getIdAsStr() + "'");
			}
			if (! mapStreamSourceIdExtToInt.containsKey(entry)) {
				throw new ConfigInvalidException(FNC_NAME + ": Could not map Stream Source ID '" + entry +
						"' for Input Source '" + getIdAsStr() + "'");
			}
			internalStreamSourceIds.add(
					mapStreamSourceIdExtToInt.get(entry)
				);
		}
	}

}
