package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

public final class RtspProtoDataCntStreamTpMain {

	private boolean isWriteProtected = false;

	/** Are we using an RTSPS connection (with SSL/TLS)? */
	private boolean isRtspsConnection = false;
	/** Is RTP/RTCP encryption required? */
	private boolean isRtpRtcpEncryptionRequired = false;
	/** Force RTP/RTCP encryption (aka SRTP/SRTCP)? */
	private boolean forceRtpRtcpEncryption = false;

	/** Are we using UDP transport? */
	private boolean isTransportUdp = false;
	/** Are we using RTP/RTCP encryption? */
	private boolean isTransportSrtpSrtcp = false;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean getIsRtspsConnection() {
		return isRtspsConnection;
	}
	public void setIsRtspsConnection(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.isRtspsConnection = value;
	}

	public boolean getRtpRtcpEncryptionRequired() {
		return isRtpRtcpEncryptionRequired;
	}
	public void setRtpRtcpEncryptionRequired(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.isRtpRtcpEncryptionRequired = value;
	}

	public boolean getForceRtpRtcpEncryption() {
		return forceRtpRtcpEncryption;
	}
	public void setForceRtpRtcpEncryption(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.forceRtpRtcpEncryption = value;
	}

	public boolean getIsTransportUdp() {
		return isTransportUdp;
	}
	public void setIsTransportUdp(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.isTransportUdp = value;
	}

	public boolean getIsTransportSrtpSrtcp() {
		return isTransportSrtpSrtcp;
	}
	public void setIsTransportSrtpSrtcp(boolean value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.isTransportSrtpSrtcp = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isSrtpRequired() {
		return (
				(! isRtspsConnection && isRtpRtcpEncryptionRequired) ||
				forceRtpRtcpEncryption
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		isRtspsConnection = false;
		isRtpRtcpEncryptionRequired = false;
		forceRtpRtcpEncryption = false;

		isTransportUdp = false;
		isTransportSrtpSrtcp = false;
	}

	public void copyFrom(@NonNull RtspProtoDataCntStreamTpMain other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		isRtspsConnection = other.isRtspsConnection;
		isRtpRtcpEncryptionRequired = other.isRtpRtcpEncryptionRequired;
		forceRtpRtcpEncryption = other.forceRtpRtcpEncryption;
		isTransportUdp = other.isTransportUdp;
		isTransportSrtpSrtcp = other.isTransportSrtpSrtcp;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"isRtspsConnection=" + (isRtspsConnection ? "T" : "F") +
				", isRtpRtcpEncryptionRequired=" + (isRtpRtcpEncryptionRequired ? "T" : "F") +
				", forceRtpRtcpEncryption=" + (forceRtpRtcpEncryption ? "T" : "F") +
				", isTransportUdp=" + (isTransportUdp ? "T" : "F") +
				", isTransportSrtpSrtcp=" + (isTransportSrtpSrtcp ? "T" : "F") +
				"]";
	}

}
