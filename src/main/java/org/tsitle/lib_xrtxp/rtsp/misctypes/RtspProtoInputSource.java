package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Container for an Input Source for RTSP streams.
 */
public final class RtspProtoInputSource implements Cloneable {

	private boolean isWriteProtected = false;

	/** Input Source ID */
	private final @NonNull RtspProtoIdInputSource id = RtspProtoIdInputSource.ofEmpty();
	/** Is this Input Source enabled? (default: true) */
	private boolean enabled = true;
	/** Does this Input Source need authentication? (default: true) */
	private boolean needsAuthentication = true;
	/** Allowed User Account Groups for this input source */
	private final @NonNull Set<@NonNull String> allowedUserAccountGroups = new HashSet<>();
	/** Does this Input Source need encryption? (default: true) */
	private boolean needsEncryption = true;
	/** Elementary-Stream Source IDs to be used by this input source */
	private final @NonNull Set<@NonNull RtspProtoIdEsSource> elementaryStreamSourceIds = new HashSet<>();
	/** Tags: Stream name */
	private @NonNull String tagsStreamName = "";
	/** Tags: Stream description */
	private @NonNull String tagsStreamDesc = "";

	public RtspProtoInputSource() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdInputSource getIdInputSource() {
		RtspProtoIdInputSource resObj = id.clone();
		resObj.writeProtect();
		return resObj;
	}
	public void setIdInputSource(@NonNull RtspProtoIdInputSource id) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.id.copyFrom(id);
	}

	public boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.enabled = enabled;
	}

	public boolean getNeedsAuthentication() {
		return needsAuthentication;
	}
	public void setNeedsAuthentication(boolean needsAuthentication) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.needsAuthentication = needsAuthentication;
	}

	@SuppressWarnings("unused")
	public @NonNull Set<@NonNull String> getAllowedUserAccountGroups() {
		return Set.copyOf(allowedUserAccountGroups);
	}
	public void setAllowedUserAccountGroups(@NonNull Set<@NonNull String> allowedUserAccountGroups) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.allowedUserAccountGroups.clear();
		this.allowedUserAccountGroups.addAll(allowedUserAccountGroups);
	}

	public boolean getNeedsEncryption() {
		return needsEncryption;
	}
	public void setNeedsEncryption(boolean needsEncryption) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.needsEncryption = needsEncryption;
	}

	public @NonNull Set<@NonNull RtspProtoIdEsSource> getEsSourceIds() {
		Set<@NonNull RtspProtoIdEsSource> resSet = new HashSet<>();
		for (RtspProtoIdEsSource tmpInp : elementaryStreamSourceIds) {
			RtspProtoIdEsSource tmpOut = tmpInp.clone();
			tmpOut.writeProtect();
			resSet.add(tmpOut);
		}
		return resSet;
	}
	public void putIdEsSource(@NonNull RtspProtoIdEsSource id) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		RtspProtoIdEsSource tmpOut = id.clone();
		tmpOut.writeProtect();
		this.elementaryStreamSourceIds.add(tmpOut);
	}

	public @NonNull String getTagsStreamName() {
		return tagsStreamName;
	}
	public void setTagsStreamName(@NonNull String tagsStreamName) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tagsStreamName = tagsStreamName;
	}

	public @NonNull String getTagsStreamDesc() {
		return tagsStreamDesc;
	}
	public void setTagsStreamDesc(@NonNull String tagsStreamDesc) {
		if (isWriteProtected) {
			throw new IllegalStateException("Cannot modify write protected object");
		}
		this.tagsStreamDesc = tagsStreamDesc;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		isWriteProtected = true;

		id.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull RtspProtoInputSource clone() {
		RtspProtoInputSource res = new RtspProtoInputSource();
		res.id.copyFrom(id);
		res.enabled = enabled;
		res.needsAuthentication = needsAuthentication;
		res.allowedUserAccountGroups.addAll(allowedUserAccountGroups);
		res.needsEncryption = needsEncryption;
		res.elementaryStreamSourceIds.addAll(elementaryStreamSourceIds);
		res.tagsStreamName = tagsStreamName;
		res.tagsStreamDesc = tagsStreamDesc;
		return res;
	}

}
