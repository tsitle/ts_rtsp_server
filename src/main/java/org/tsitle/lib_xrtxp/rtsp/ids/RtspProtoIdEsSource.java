package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

/**
 * Elementary-Stream Source ID
 */
public final class RtspProtoIdEsSource extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdEsSource() {
		super();
	}

	private RtspProtoIdEsSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdEsSource ofEmpty() {
		return new RtspProtoIdEsSource();
	}

	public static RtspProtoIdEsSource of(@NonNull String idStr) {
		return new RtspProtoIdEsSource(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoIdEsSource other) {
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
	public @NonNull RtspProtoIdEsSource clone() {
		return (RtspProtoIdEsSource)super.clone();
	}

}
