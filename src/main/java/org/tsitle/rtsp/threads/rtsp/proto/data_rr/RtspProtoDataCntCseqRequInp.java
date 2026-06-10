package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

public final class RtspProtoDataCntCseqRequInp {

	private boolean isWriteProtected = false;

	/** Last received RTSP message Sequence Number in request */
	private long cseqNr_lastRcvd = -1L;
	/** Expected RTSP message Sequence Number in request */
	private long cseqNr_expected = 0L;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public long getCseqNrLastRcvd() {
		return cseqNr_lastRcvd;
	}
	public void setCseqNrLastRcvd(long seqNr_requRem_lastRcvd) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.cseqNr_lastRcvd = seqNr_requRem_lastRcvd;
	}

	public long getCseqNrExpected() {
		return cseqNr_expected;
	}
	public void setCseqNrExpected(long seqNr_requRem_expected) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.cseqNr_expected = seqNr_requRem_expected;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		cseqNr_lastRcvd = -1L;
		cseqNr_expected = 0L;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

}
