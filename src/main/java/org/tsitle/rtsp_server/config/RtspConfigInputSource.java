package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
	/** Muxed-Stream Source ID to be used by this Input Source */
	@Expose
	private @NonNull String muxedStreamSourceId;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Elementary-Stream Source IDs to be used by this Input Source */
	@GsonAnnoExclude
	private @NonNull Set<@NonNull Integer> internalEsSourceIds;
	/** Internal use: Muxed-Stream Source ID to be used by this Input Source */
	@GsonAnnoExclude
	private @NonNull Integer internalMsSourceId;

	public RtspConfigInputSource() {
		this.id = "";
		this.enabled = true;
		this.needsAuthentication = true;
		this.allowedUserAccountGroups = new HashSet<>();
		this.needsEncryption = true;
		this.elementaryStreamSourceIds = new HashSet<>();
		this.muxedStreamSourceId = "";

		//noinspection DataFlowIssue
		this.internalEsSourceIds = null;
		//noinspection DataFlowIssue
		this.internalMsSourceId = null;
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

	public Optional<Integer> getMuxedStreamSourceId() {
		checkPostProcessed();
		//noinspection ConstantValue
		if (muxedStreamSourceId == null || muxedStreamSourceId.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(internalMsSourceId);
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

	void setId(@NonNull String id) {
		//noinspection ConstantValue
		this.id = (id == null ? "" : id.strip());
	}

	/**
	 * Post-process the Input Source.
	 * @param mapEsSourceIdExtToInt Map of Elementary-Stream Source IDs (external) to their internal representation
	 * @param mapMsSourceIdExtToInt Map of Muxed-Stream Source IDs (external) to their internal representation
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	void postProcess(
				@NonNull Map<@NonNull String, @NonNull Integer> mapEsSourceIdExtToInt,
				@NonNull Map<@NonNull String, @NonNull Integer> mapMsSourceIdExtToInt
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
		//noinspection ConstantValue
		if (internalMsSourceId == null) {
			createInternalMsSourcesMap(mapMsSourceIdExtToInt);
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
	 * @param muxedStreamSources Map of all Muxed-Stream Sources
	 * @param mapMsSourceIdIntToExt Map of Muxed-Stream Source IDs (internal) to their external representation
	 * @param userAccountGroups Existing User Account Groups
	 * @throws ConfigInvalidException If the Input Source is invalid
	 */
	void validate(
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigElementaryStreamSource> elementaryStreamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapEsSourceIdIntToExt,
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigMuxedStreamSource> muxedStreamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapMsSourceIdIntToExt,
				@NonNull Set<@NonNull String> userAccountGroups
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		if (getIdAsStr().isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Input Source has no ID");
		}

		// ---------------------------------------------

		if (getElementaryStreamSourceIds().isEmpty() && getMuxedStreamSourceId().isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Neither Elementary- nor Muxed-Stream Sources found " +
					"for Input Source ID '" + id + "'");
		}
		if (! (getElementaryStreamSourceIds().isEmpty() || getMuxedStreamSourceId().isEmpty())) {
			throw new ConfigInvalidException(FNC_NAME + ": Both Elementary- and Muxed-Stream Sources are set " +
					"for Input Source ID '" + id + "'");
		}
		validate_elementaryStreamSourceIds(FNC_NAME, elementaryStreamSources, mapEsSourceIdIntToExt);
		validate_muxedStreamSourceIds(FNC_NAME, muxedStreamSources, mapMsSourceIdIntToExt);

		// ---------------------------------------------

		if (needsAuthentication && allowedUserAccountGroups.isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Empty Allowed User Account Groups list" +
					" used in Input Source ID '" + id + "'");
		}
		for (String allowedUag : allowedUserAccountGroups) {
			if (! userAccountGroups.contains(allowedUag)) {
				throw new ConfigInvalidException(FNC_NAME + ": Non-existing User Account Group '" + allowedUag + "'" +
						" used in Input Source ID '" + id + "'");
			}
		}
	}

	void addVirtualEsSource(
				@NonNull String virtualExternalEsId,
				@NonNull RtspConfigElementaryStreamSource esSrcObj
			) {
		internalEsSourceIds.add(esSrcObj.getIdAsInt());
		elementaryStreamSourceIds.add(virtualExternalEsId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Input Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void createInternalEsSourcesMap(
				@NonNull Map<@NonNull String, @NonNull Integer> mapEsSourceIdExtToInt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".createInternalEsSourcesMap()";

		internalEsSourceIds = new HashSet<>();

		//noinspection ConstantValue
		if (elementaryStreamSourceIds == null || elementaryStreamSourceIds.isEmpty()) {
			return;
		}

		createInternalXxSourcesMap(
				FNC_NAME,
				"Elementary",
				elementaryStreamSourceIds,
				mapEsSourceIdExtToInt,
				internalEsSourceIds
			);
	}

	private void createInternalMsSourcesMap(
				@NonNull Map<@NonNull String, @NonNull Integer> mapMsSourceIdExtToInt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".createInternalMsSourcesMap()";

		//noinspection ConstantValue
		if (muxedStreamSourceId == null || muxedStreamSourceId.isBlank()) {
			internalMsSourceId = -1;
			return;
		}

		Set<String> tmpInputExtMsSourceIds = new HashSet<>();
		tmpInputExtMsSourceIds.add(muxedStreamSourceId);
		Set<Integer> tmpOutputMsSourceIds = new HashSet<>();
		createInternalXxSourcesMap(
				FNC_NAME,
				"Muxed",
				tmpInputExtMsSourceIds,
				mapMsSourceIdExtToInt,
				tmpOutputMsSourceIds
			);
		internalMsSourceId = tmpOutputMsSourceIds.iterator().next();
	}

	private void createInternalXxSourcesMap(
				@NonNull String fncName,
				@NonNull String desc,
				@Nullable Set<@Nullable String> externalXxSourceIds,
				@NonNull Map<@NonNull String, @NonNull Integer> mapXxSourceIdExtToInt,
				@NonNull Set<@NonNull Integer> outputIds
			) throws ConfigInvalidException {
		if (externalXxSourceIds == null) {
			return;
		}
		for (String entry : externalXxSourceIds) {
			if (entry == null) {
				continue;
			}
			if (entry.isBlank()) {
				throw new ConfigInvalidException(fncName + ": Empty " + desc + "-Stream Source ID for Input Source '" +
						getIdAsStr() + "'");
			}
			if (! mapXxSourceIdExtToInt.containsKey(entry)) {
				throw new ConfigInvalidException(fncName + ": Could not map " + desc + "-Stream Source ID '" + entry +
						"' for Input Source '" + getIdAsStr() + "'");
			}
			outputIds.add(
					mapXxSourceIdExtToInt.get(entry)
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void validate_elementaryStreamSourceIds(
				@NonNull String fncName,
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigElementaryStreamSource> elementaryStreamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapEsSourceIdIntToExt
			) throws ConfigInvalidException {
		//noinspection ConstantValue
		if (elementaryStreamSourceIds == null || internalEsSourceIds.size() != elementaryStreamSourceIds.size()) {
			throw new ConfigInvalidException(fncName + ": Not all Elementary-Stream Source IDs could be mapped " +
					"for Input Source ID '" + id + "'");
		}
		Set<Integer> tmpIntEsIds = getElementaryStreamSourceIds();
		if (tmpIntEsIds.size() > 2) {
			throw new ConfigInvalidException(fncName + ": Input Source ID '" + id + "' contains more than two " +
					"Elementary-Stream Sources");
		}
		for (int tmpIntEsId : tmpIntEsIds) {
			if (! elementaryStreamSources.containsKey(tmpIntEsId)) {
				String tmpExtSsId = mapEsSourceIdIntToExt.get(tmpIntEsId);
				throw new ConfigInvalidException(fncName + ": Non-existing Elementary-Stream Source ID '" + tmpExtSsId + "'" +
						" used for Input Source ID '" + id + "'");
			}
			if (! elementaryStreamSources.get(tmpIntEsId).getEnabled()) {
				String tmpExtSsId = mapEsSourceIdIntToExt.get(tmpIntEsId);
				throw new ConfigInvalidException(fncName + ": Disabled Elementary-Stream Source ID '" + tmpExtSsId + "'" +
						" used for Input Source ID '" + id + "'");
			}
		}
	}

	private void validate_muxedStreamSourceIds(
				@NonNull String fncName,
				@NonNull Map<@NonNull Integer, @NonNull RtspConfigMuxedStreamSource> muxedStreamSources,
				@NonNull Map<@NonNull Integer, @NonNull String> mapMsSourceIdIntToExt
			) throws ConfigInvalidException {
		if (getMuxedStreamSourceId().isEmpty()) {
			return;
		}
		if (! muxedStreamSources.containsKey(internalMsSourceId)) {
			String tmpExtMsId = mapMsSourceIdIntToExt.get(internalMsSourceId);
			throw new ConfigInvalidException(fncName + ": Non-existing Muxed-Stream Source ID '" + tmpExtMsId + "'" +
					" used for Input Source ID '" + id + "'");
		}
		if (! muxedStreamSources.get(internalMsSourceId).getEnabled()) {
			String tmpExtSsId = mapMsSourceIdIntToExt.get(internalMsSourceId);
			throw new ConfigInvalidException(fncName + ": Disabled Muxed-Stream Source ID '" + tmpExtSsId + "'" +
					" used for Input Source ID '" + id + "'");
		}
	}

}
