package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

public final class RtspProtoDataCntCseqRespInp {

	private boolean isWriteProtected = false;

	/** Expected RTSP message Sequence Number in response */
	private long cseqNr_expected = 0L;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
		cseqNr_expected = 0L;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

}
