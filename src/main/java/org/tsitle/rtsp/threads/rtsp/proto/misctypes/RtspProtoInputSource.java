package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Input Source for RTSP streams.
 */
public final class RtspProtoInputSource {

	private boolean writeProtected = false;

	/** Input Source ID */
	private final @NonNull RtspProtoIdInputSource id = new RtspProtoIdInputSource();
	/** Is this Input Source enabled? (default: true) */
	private boolean enabled = true;
	/** Does this Input Source need authentication? (default: true) */
	private boolean needsAuthentication = true;
	/** Allowed User Account Groups for this input source */
	private final @NonNull Set<@NonNull String> allowedUserAccountGroups = new HashSet<>();
	/** Does this Input Source need encryption? (default: true) */
	private boolean needsEncryption = true;
	/** Stream Source IDs to be used by this input source */
	private final @NonNull Set<@NonNull RtspProtoIdStreamSource> streamSourceIds = new HashSet<>();

	public RtspProtoInputSource() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdInputSource getIdInputSource() {
		RtspProtoIdInputSource resObj = id.clone();
		resObj.writeProtect();
		return resObj;
	}
	public void setIdInputSource(@NonNull RtspProtoIdInputSource id) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.id.copyFrom(id);
	}

	public boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.enabled = enabled;
	}

	public boolean getNeedsAuthentication() {
		return needsAuthentication;
	}
	public void setNeedsAuthentication(boolean needsAuthentication) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.needsAuthentication = needsAuthentication;
	}

	@SuppressWarnings("unused")
	public @NonNull Set<@NonNull String> getAllowedUserAccountGroups() {
		return Set.copyOf(allowedUserAccountGroups);
	}
	public void setAllowedUserAccountGroups(@NonNull Set<@NonNull String> allowedUserAccountGroups) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.allowedUserAccountGroups.clear();
		this.allowedUserAccountGroups.addAll(allowedUserAccountGroups);
	}

	public boolean getNeedsEncryption() {
		return needsEncryption;
	}
	public void setNeedsEncryption(boolean needsEncryption) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.needsEncryption = needsEncryption;
	}

	public @NonNull Set<@NonNull RtspProtoIdStreamSource> getStreamSourceIds() {
		Set<@NonNull RtspProtoIdStreamSource> resSet = new HashSet<>();
		for (RtspProtoIdStreamSource tmpInp : streamSourceIds) {
			RtspProtoIdStreamSource tmpOut = tmpInp.clone();
			tmpOut.writeProtect();
			resSet.add(tmpOut);
		}
		return resSet;
	}
	public void putIdStreamSource(@NonNull RtspProtoIdStreamSource streamSourceId) {
		if (writeProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		RtspProtoIdStreamSource tmpOut = streamSourceId.clone();
		tmpOut.writeProtect();
		this.streamSourceIds.add(tmpOut);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		writeProtected = true;

		id.writeProtect();
	}

}
