package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;

public interface FfmpegReceiveDemuxerStatsInterface {

	void cbReceiveDemuxerStats(@NonNull FfmpegDemuxerStats statsPtr);

}
