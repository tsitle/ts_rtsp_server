package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class RtspProtoDataCntSdp {

	/** Content language(s) (e.g. 'de') */
	public @NonNull String contentLang = "";

	/** Content base (e.g. 'rtsp://some.com/camera.stream/') */
	public @NonNull String contentBase = "";

	/** Session Description Protocol (SDP) data */
	public final @NonNull List<@NonNull String> sdpLinesAllRaw = new ArrayList<>();

	public void clear() {
		contentLang = "";
		contentBase = "";
		sdpLinesAllRaw.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntSdp other) {
		if (other == this) {
			return;
		}
		contentLang = other.contentLang;
		contentBase = other.contentBase;
		sdpLinesAllRaw.clear();
		sdpLinesAllRaw.addAll(other.sdpLinesAllRaw);
	}

}
