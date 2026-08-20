package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;

import java.util.Iterator;

public final class FfmpegTcAvPacketBacklog implements Iterable<@NonNull FfmpegAvPktBasics> {

	private final FfmpegTcAvPacketList backlog = new FfmpegTcAvPacketList();

	public boolean isEmpty() {
		return backlog.isEmpty();
	}

	public void clear() {
		backlog.clear();
	}

	public void addAll(@NonNull FfmpegTcAvPacketList pkts) {
		for (FfmpegAvPktBasics pkt : pkts) {
			backlog.addPkt(pkt);
		}
	}

	@Override
	public @NonNull Iterator<@NonNull FfmpegAvPktBasics> iterator() {
		return backlog.iterator();
	}

}
