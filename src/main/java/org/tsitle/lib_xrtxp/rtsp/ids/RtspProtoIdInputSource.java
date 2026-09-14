package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Input Source ID
 */
public final class RtspProtoIdInputSource extends RtspProtoBaseIdString implements Cloneable {

	private static final char[] INVALID_CHARS = new char[] {'@', ':', '/', '\\', '\'', '"', '<', '>', '?', '#', '&'};

	private RtspProtoIdInputSource() {
		super();
	}

	private RtspProtoIdInputSource(@NonNull String idStr) {
		super(idStr);

		validateCharSet();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdInputSource ofEmpty() {
		return new RtspProtoIdInputSource();
	}

	public static RtspProtoIdInputSource of(@NonNull String idStr) {
		return new RtspProtoIdInputSource(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoIdInputSource other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		setIdStr(other.getIdStr().orElse(""));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoIdInputSource clone() {
		return (RtspProtoIdInputSource)super.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void validateCharSet() {
		final String FNC_NAME = getClass().getSimpleName() + ".validateCharSet()";

		Optional<String> tmpOptId = getIdStr();
		if (tmpOptId.isEmpty()) {
			return;
		}
		String orgId = tmpOptId.get();
		for (char c : INVALID_CHARS) {
			if (orgId.indexOf(c) >= 0) {
				throw new IllegalArgumentException(FNC_NAME + ": ID '" + orgId + "' contains invalid characters");
			}
		}
	}

}
