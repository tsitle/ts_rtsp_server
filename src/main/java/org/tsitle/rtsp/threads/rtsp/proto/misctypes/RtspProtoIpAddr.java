package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;

public final class RtspProtoIpAddr implements Cloneable {

	private boolean isWriteProtected = false;

	private @Nullable InetAddress ipAddrObj = null;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<InetAddress> getIpAddrObj() {
		if (ipAddrObj == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(InetAddress.getByAddress(ipAddrObj.getAddress()));
		} catch (UnknownHostException e) {
			return Optional.empty();
		}
	}
	public Optional<String> getIpAddrStr() {
		if (ipAddrObj == null) {
			return Optional.empty();
		}
		return Optional.of(ipAddrObj.getHostAddress());
	}
	public void setIpAddr(@NonNull InetAddress value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		try {
			ipAddrObj = InetAddress.getByAddress(value.getAddress());
		} catch (UnknownHostException e) {
			// silently fail
			ipAddrObj = null;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		ipAddrObj = null;
	}

	public boolean isEmpty() {
		return (ipAddrObj == null);
	}

	public void copyFrom(@NonNull RtspProtoIpAddr other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		if (other.ipAddrObj == null) {
			ipAddrObj = null;
		} else {
			try {
				ipAddrObj = InetAddress.getByAddress(other.ipAddrObj.getAddress());
			} catch (UnknownHostException e) {
				// silently fail
				ipAddrObj = null;
			}
		}
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoIpAddr that)) {
			return false;
		}
		if (ipAddrObj == null && that.ipAddrObj == null) {
			return true;
		}
		if (ipAddrObj == null || that.ipAddrObj == null) {
			return false;
		}
		return getIpAddrStr().orElseThrow().equals(that.getIpAddrStr().orElseThrow());
	}

	@Override
	public int hashCode() {
		if (ipAddrObj == null) {
			return 0;
		}
		return Objects.hashCode(getIpAddrStr().orElseThrow());
	}

	@Override
	public RtspProtoIpAddr clone() {
		try {
			RtspProtoIpAddr cloned = (RtspProtoIpAddr)super.clone();
			if (ipAddrObj != null) {
				try {
					cloned.ipAddrObj = InetAddress.getByAddress(ipAddrObj.getAddress());
				} catch (UnknownHostException e) {
					// silently fail
					cloned.ipAddrObj = null;
				}
			}
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
