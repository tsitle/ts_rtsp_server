package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

/**
 * Container for an Elementary-Stream Source within an Input Source for RTSP streams.
 */
public final class RtspProtoElementaryStreamSource implements Cloneable {

	private boolean isWriteProtected = false;

	/** Elementary-Stream Source ID */
	private final @NonNull RtspProtoIdEsSource id = RtspProtoIdEsSource.ofEmpty();
	/** Is this Elementary-Stream Source enabled? (default: true) */
	private boolean enabled = true;

	public RtspProtoElementaryStreamSource() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoIdEsSource getIdEsSource() {
		return id.clone();
	}
	public void setIdEsSource(@NonNull RtspProtoIdEsSource id) {
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

	// -----------------------------------------------------------------------------------------------------------------

	public void writeProtect() {
		isWriteProtected = true;

		id.writeProtect();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull RtspProtoElementaryStreamSource clone() {
		RtspProtoElementaryStreamSource clone = new RtspProtoElementaryStreamSource();
		clone.id.copyFrom(id);
		clone.enabled = enabled;
		return clone;
	}

}
