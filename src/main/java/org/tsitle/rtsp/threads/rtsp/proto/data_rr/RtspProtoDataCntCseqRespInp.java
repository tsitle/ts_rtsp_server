package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;

public final class RtspProtoDataCntCseqRespInp {

	private boolean isWriteProtected = false;

	/** Expected RTSP message Sequence Number in response */
	public @NonNull RtspProtoCseqNr cseqNr_expected = new RtspProtoCseqNr(0L);

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
