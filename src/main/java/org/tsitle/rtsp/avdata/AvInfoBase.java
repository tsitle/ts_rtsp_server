package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;

public abstract class AvInfoBase<T extends AvInfoBase<T>> {

	public abstract void reset();

	public abstract void copyOf(@NonNull T src);

}
