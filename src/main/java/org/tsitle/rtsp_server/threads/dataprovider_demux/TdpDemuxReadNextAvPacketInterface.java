package org.tsitle.rtsp_server.threads.dataprovider_demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;

public interface TdpDemuxReadNextAvPacketInterface {

	void readNextPacketVideo(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException;

	void readNextPacketAudio(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException;

}
