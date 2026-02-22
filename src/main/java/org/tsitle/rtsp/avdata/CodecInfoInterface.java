package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;

public interface CodecInfoInterface<I extends CodecInfoInterface<I>> {

	int getPayloadOffset();

	int getPayloadLength();

	void reset();

	void copyOf(@NonNull CodecInfoInterface<I> src);

	String toString(boolean shortOutput);

	@NonNull String hashSum();

}
