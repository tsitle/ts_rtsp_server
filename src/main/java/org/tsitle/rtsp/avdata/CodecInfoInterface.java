package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;

public interface CodecInfoInterface<I extends CodecInfoInterface<I>> {

	void reset();

	void copyOf(@NonNull CodecInfoInterface<I> src);

	String toString(boolean shortOutput);

	String hashSum();

}
