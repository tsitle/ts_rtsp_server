package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class RtspProtoDataCntSdp {

	private boolean isWriteProtected = false;

	/** Content language(s) (e.g. 'de') */
	private @NonNull String contentLang = "";

	/** Content base (e.g. 'rtsp://some.com/camera.stream/') */
	private @NonNull String contentBase = "";

	/** Session Description Protocol (SDP) data */
	private final @NonNull List<@NonNull String> sdpLinesAllRaw = new ArrayList<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getContentLang() {
		return contentLang;
	}
	public void setContentLang(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.contentLang = value;
	}

	public @NonNull String getContentBase() {
		return contentBase;
	}
	public void setContentBase(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.contentBase = value;
	}

	public boolean isSdpLinesAllRawEmpty() {
		return sdpLinesAllRaw.isEmpty();
	}
	public @NonNull List<@NonNull String> getSdpLinesAllRaw() {
		return new ArrayList<>(sdpLinesAllRaw);
	}
	public void addAllSdpLinesAllRaw(@NonNull List<@NonNull String> value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		sdpLinesAllRaw.addAll(value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		contentLang = "";
		contentBase = "";
		sdpLinesAllRaw.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntSdp other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		contentLang = other.contentLang;
		contentBase = other.contentBase;
		sdpLinesAllRaw.clear();
		sdpLinesAllRaw.addAll(other.sdpLinesAllRaw);
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	private static @NonNull String cleanUpBodyString(@NonNull String input) {
		return input.replaceAll("\\r\\n", "<CRLF>")
					.replaceAll("\\r", "<CR>")
					.replaceAll("\\n", "<LF>")
					.replaceAll("\\t", "<TAB>")
					.replace("'", "\\'");
	}

	private static @NonNull String listToString(@NonNull List<@NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (String tmpEntry : input) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(cleanUpBodyString(tmpEntry)).append("'");
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"contentLang='" + contentLang + "'" +
				", contentBase='" + contentBase + "'" +
				", sdpLinesAllRaw=" + listToString(sdpLinesAllRaw) +
				"]";
	}

}
