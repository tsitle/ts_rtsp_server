package org.tsitle.rtsp.threads.rtp.codec_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;

import java.util.ArrayList;
import java.util.List;

final class RtpH26xPayloadBuffer {

	private final List<@NonNull BufferExt> buffers = new ArrayList<>();
	private final List<@NonNull Boolean> used = new ArrayList<>();
	private int markedForDiscard = -1;

	public @NonNull BufferExt getBufferObjPtr() {
		if (markedForDiscard >= 0) {
			discardBuffer();
		}
		boolean haveUnused = used.stream().anyMatch(b -> ! b);
		if (! haveUnused) {
			buffers.add(new BufferExt());
			used.add(true);
			return buffers.getLast();
		}
		int firstUnusedIx = used.indexOf(false);
		used.set(firstUnusedIx, true);
		return buffers.get(firstUnusedIx);
	}

	public void markForDiscard(@NonNull BufferExt bufferPtr) {
		//noinspection ConstantValue
		if (bufferPtr == null) {
			throw new IllegalArgumentException("bufferPtr is null");
		}
		if (markedForDiscard >= 0) {
			discardBuffer();
		}
		markedForDiscard = buffers.indexOf(bufferPtr);
		if (markedForDiscard < 0) {
			throw new IllegalArgumentException("bufferPtr is not in buffers list");
		}
	}

	private void discardBuffer() {
		buffers.get(markedForDiscard).clear();
		used.set(markedForDiscard, false);
		markedForDiscard = -1;
	}

}
