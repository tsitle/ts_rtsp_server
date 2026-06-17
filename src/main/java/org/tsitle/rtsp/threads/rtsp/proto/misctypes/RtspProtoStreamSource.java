package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;

/**
 * Container for a Stream Source within an Input Source for RTSP streams.
 */
public final class RtspProtoStreamSource {

	private boolean writeProtected = false;

	/** Stream Source ID */
	private final @NonNull RtspProtoIdStreamSource id = RtspProtoIdStreamSource.ofEmpty();
	/** Is this Stream Source enabled? (default: true) */
	private boolean enabled = true;

	public RtspProtoStreamSource() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdStreamSource getIdStreamSource() {
		return id.clone();
	}
	public void setIdStreamSource(@NonNull RtspProtoIdStreamSource id) {
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

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		writeProtected = true;

		id.writeProtect();
	}

}
