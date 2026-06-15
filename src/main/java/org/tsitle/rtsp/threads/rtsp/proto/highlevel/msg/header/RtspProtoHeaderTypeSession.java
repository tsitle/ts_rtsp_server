package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

public class RtspProtoHeaderTypeSession {

	public final @NonNull RtspProtoIdSession idSession = new RtspProtoIdSession();

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

	public Optional<Integer> getTimeout32bit() {
		return (timeout32bit < 0L ? Optional.empty() : Optional.of((int)timeout32bit));
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				"idSession='" + idSession.getIdStr() + "', " +
				optionalIntToStr("timeout", getTimeout32bit()) +
				"]";
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
		return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
	}

}
