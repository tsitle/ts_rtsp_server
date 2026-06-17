package org.tsitle.lib.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoCseqNr;

public final class RtspProtoDataCntCseqRespInp {

	private boolean isWriteProtected = false;

	/** Expected RTSP message Sequence Number in response */
	public @NonNull RtspProtoCseqNr cseqNr_expected = RtspProtoCseqNr.ofZero();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		cseqNr_expected.clear();
	}

	public void writeProtect() {
		isWriteProtected = true;

		cseqNr_expected.writeProtect();
	}

}
