package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class VideoVp8Info implements CodecInfoInterface<VideoVp8Info>, Cloneable {

	public int payloadOffs;
	public int payloadLength;
	public boolean isKeyFrame;

	public VideoVp8Info() {
		reset();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public @NonNull String getValidationErrorMsg() {
		return "";
	}

	@Override
	public int getPayloadOffset() {
		return payloadOffs;
	}

	@Override
	public int getPayloadLength() {
		return payloadLength;
	}

	@Override
	public void reset() {
		payloadOffs = 0;
		payloadLength = 0;
		isKeyFrame = false;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<VideoVp8Info> src) {
		reset();

		VideoVp8Info tmpSrc = (VideoVp8Info)src;
		payloadOffs = tmpSrc.payloadOffs;
		payloadLength = tmpSrc.payloadLength;
		isKeyFrame = tmpSrc.isKeyFrame;
	}

	@Override
	public @NonNull VideoVp8Info clone() {
		try {
			return (VideoVp8Info)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"payloadOffs=" + payloadOffs +
				", payloadLength=" + payloadLength +
				", isKeyFrame=" + (isKeyFrame ? "T" : "F") +
				"]";
	}

	@Override
	public @NonNull String toString(boolean shortOutput) {
		if (! shortOutput) {
			return toString();
		}
		return getClass().getSimpleName() + " [" +
				"isKeyFrame=" + (isKeyFrame ? "T" : "F") +
				"]";
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(payloadOffs);
		baos.write(payloadLength);
		baos.write(isKeyFrame ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}
