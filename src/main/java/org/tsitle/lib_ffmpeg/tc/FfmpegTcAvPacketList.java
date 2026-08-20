package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;

import java.util.*;

public final class FfmpegTcAvPacketList implements Iterable<@NonNull FfmpegAvPktBasics> {

	static final int LIST_SIZE = 250;

	private final List<@NonNull FfmpegAvPktBasics> pktList = new ArrayList<>(LIST_SIZE);
	private int count = 0;
	private int writeIx = 0;

	public FfmpegTcAvPacketList() {
		for (int i = 0; i < LIST_SIZE; i++) {
			pktList.add(new FfmpegAvPktBasics());
		}
	}

	public boolean isEmpty() {
		return (count <= 0);
	}

	void clear() {
		count = 0;
		writeIx = 0;
	}

	void addPkt(@NonNull FfmpegAvPktBasics pkt) {
		if (count >= LIST_SIZE) {
			throw new IllegalStateException(getClass().getSimpleName() + ".addPkt(): " +
					"Packet list is full (tried to add another " + (pkt.isVideo ? "video" : "audio") + " packet)");
		}
		pktList.get(writeIx).copyFrom(pkt);
		writeIx = (writeIx + 1) % LIST_SIZE;
		++count;
	}

	@Override
	public @NonNull Iterator<@NonNull FfmpegAvPktBasics> iterator() {
		if (count <= 0) {
			return Collections.emptyIterator();
		}
		return new Iterator<>() {
				private int iterCount = 0;
				@Override
				public boolean hasNext() {
					return (iterCount < count);
				}
				@Override
				public @NonNull FfmpegAvPktBasics next() {
					if (! hasNext()) {
						throw new NoSuchElementException(FfmpegTcAvPacketList.class.getSimpleName() + ".iter.next(): " +
								"No more packets available");
					}
					int ix = (iterCount++) % LIST_SIZE;
					return pktList.get(ix);
				}
			};
	}

}
