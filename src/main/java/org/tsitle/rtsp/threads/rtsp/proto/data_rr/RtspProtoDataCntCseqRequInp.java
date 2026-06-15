package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;

public final class RtspProtoDataCntCseqRequInp {

	private boolean isWriteProtected = false;

	/** Last received RTSP message Sequence Number in request */
	public RtspProtoCseqNr cseqNr_lastRcvd = new RtspProtoCseqNr();
	/** Expected RTSP message Sequence Number in request */
	public RtspProtoCseqNr cseqNr_expected = new RtspProtoCseqNr(0L);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		cseqNr_lastRcvd.clear();
		cseqNr_expected.clear();
		try {
			cseqNr_expected.setCseq32bit(0L);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
	}

	public void writeProtect() {
		isWriteProtected = true;

		cseqNr_lastRcvd.writeProtect();
		cseqNr_expected.writeProtect();
	}

}
