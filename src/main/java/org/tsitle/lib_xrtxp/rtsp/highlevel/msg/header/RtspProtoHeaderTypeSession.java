package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;

import java.util.Optional;

public final class RtspProtoHeaderTypeSession {

	public final @NonNull RtspProtoIdSession idSession = RtspProtoIdSession.ofEmpty();

	/** Session timeout in seconds. -1 means no timeout. */
	private long timeout32bit = -1L;

	public void setTimeout32bit(long timeout32bit) throws RtspProtoNumberRangeException {
		if (timeout32bit < 0L || timeout32bit > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException("timeout32bit must be non-negative and within 32-bit range");
		}
		this.timeout32bit = timeout32bit;
	}

	public void clearTimeout() {
		timeout32bit = -1L;
	}

	public Optional<Long> getTimeout32bit() {
		return (timeout32bit < 0L ? Optional.empty() : Optional.of(timeout32bit));
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				"idSession=" + idSession +
				", " + optionalLongToStr("timeout", getTimeout32bit()) +
				"]";
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalLongToStr(@NonNull String desc, @NonNull Optional<Long> value) {
		return desc + "=" + value.map(Long::toUnsignedString).orElse("unset");
	}

}
