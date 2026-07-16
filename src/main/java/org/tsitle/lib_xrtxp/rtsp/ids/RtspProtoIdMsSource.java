package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

/**
 * Muxed-Stream Source ID
 */
public final class RtspProtoIdMsSource extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdMsSource() {
		super();
	}

	private RtspProtoIdMsSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdMsSource ofEmpty() {
		return new RtspProtoIdMsSource();
	}

	public static RtspProtoIdMsSource of(@NonNull String idStr) {
		return new RtspProtoIdMsSource(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoIdMsSource other) {
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
	public RtspProtoIdMsSource clone() {
		return (RtspProtoIdMsSource)super.clone();
	}

}
