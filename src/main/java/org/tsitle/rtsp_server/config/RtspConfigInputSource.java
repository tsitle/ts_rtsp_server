package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

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
	/** Allowed User Account Groups for this Input Source */
	@Expose
	private @NonNull Set<@NonNull String> allowedUserAccountGroups;
	/** Does this Input Source need encryption? (default: true) */
	@Expose
	private boolean needsEncryption;
	/** Elementary-Stream Source IDs to be used by this Input Source */
	@Expose
	private @NonNull Set<@NonNull String> elementaryStreamSourceIds;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Elementary-Stream Source IDs to be used by this Input Source */
	@SuppressWarnings("FieldMayBeFinal")
	@GsonAnnoExclude
	private @NonNull Set<@NonNull Integer> internalEsSourceIds;

	public RtspConfigInputSource() {
		this.id = "";
		this.enabled = true;
		this.needsAuthentication = true;
		this.allowedUserAccountGroups = new HashSet<>();
		this.needsEncryption = true;
		this.elementaryStreamSourceIds = new HashSet<>();

		//noinspection DataFlowIssue
		this.internalEsSourceIds = null;
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

	public @NonNull Set<@NonNull Integer> getElementaryStreamSourceIds() {
		checkPostProcessed();
		return Set.copyOf(internalEsSourceIds);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Input Source.
	 * @param mapEsSourceIdExtToInt Map of Elementary-Stream Source IDs (external) to their internal representation
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	public void postProcess(
				@NonNull Map<@NonNull String, @NonNull Integer> mapEsSourceIdExtToInt
			) throws ConfigInvalidException {
		internalHasBeenPostProcessed = true;

		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}
		//
		//noinspection ConstantValue
		if (internalEsSourceIds == null) {
			createInternalEsSourcesMap(mapEsSourceIdExtToInt);
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
	 * @param elementaryStreamSources Map of all Elementary-Stream Sources
	 * @param mapEsSourceIdIntToExt Map of Elementary-Stream Source IDs (internal) to their external representation
	 * @param userAccountGroups Existing User Account Groups
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	public void validate(
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigElementaryStreamSource> elementaryStreamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapEsSourceIdIntToExt,
				@NonNull Set<@NonNull String> userAccountGroups
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		if (getIdAsStr().isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Input Source has no ID");
		}
		//noinspection ConstantValue
		if (elementaryStreamSourceIds == null || internalEsSourceIds.size() != elementaryStreamSourceIds.size()) {
			throw new ConfigInvalidException(FNC_NAME + ": Not all Elementary-Stream Source IDs could be mapped " +
					"for Input Source ID '" + id + "'");
		}
		Set<Integer> tmpIsSsIdSet = getElementaryStreamSourceIds();
		if (tmpIsSsIdSet.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": No Elementary-Stream Sources found for Input Source ID '" + id + "'");
		}
		if (tmpIsSsIdSet.size() > 2) {
			throw new ConfigInvalidException(FNC_NAME + ": Input Source ID '" + id + "' contains more than two " +
					"Elementary-Stream Sources");
		}
		for (int tmpSsId : tmpIsSsIdSet) {
			if (! elementaryStreamSources.containsKey(tmpSsId)) {
				String tmpExtSsId = mapEsSourceIdIntToExt.get(tmpSsId);
				throw new ConfigInvalidException(FNC_NAME + ": Non-existing Elementary-Stream Source ID '" + tmpExtSsId + "'" +
						" used for Input Source ID '" + id + "'");
			}
			if (! elementaryStreamSources.get(tmpSsId).getEnabled()) {
				String tmpExtSsId = mapEsSourceIdIntToExt.get(tmpSsId);
				throw new ConfigInvalidException(FNC_NAME + ": Disabled Elementary-Stream Source ID '" + tmpExtSsId + "'" +
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
			clone.elementaryStreamSourceIds = new HashSet<>(elementaryStreamSourceIds);
			clone.internalEsSourceIds = new HashSet<>(internalEsSourceIds);
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

	private void createInternalEsSourcesMap(
				@NonNull Map<@NonNull String, @NonNull Integer> mapEsSourceIdExtToInt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".createInternalEsSourcesMap()";

		internalEsSourceIds = new HashSet<>();
		//noinspection ConstantValue
		if (elementaryStreamSourceIds == null) {
			return;
		}
		for (String entry : elementaryStreamSourceIds) {
			//noinspection ConstantValue
			if (entry == null) {
				continue;
			}
			if (entry.isBlank()) {
				throw new ConfigInvalidException(FNC_NAME + ": Empty Elementary-Stream Source ID for Input Source '" +
						getIdAsStr() + "'");
			}
			if (! mapEsSourceIdExtToInt.containsKey(entry)) {
				throw new ConfigInvalidException(FNC_NAME + ": Could not map Elementary-Stream Source ID '" + entry +
						"' for Input Source '" + getIdAsStr() + "'");
			}
			internalEsSourceIds.add(
					mapEsSourceIdExtToInt.get(entry)
				);
		}
	}

}
