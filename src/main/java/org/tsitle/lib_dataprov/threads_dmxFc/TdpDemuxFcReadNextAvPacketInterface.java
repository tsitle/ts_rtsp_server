package org.tsitle.lib_dataprov.threads_dmxFc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;

public interface TdpDemuxFcReadNextAvPacketInterface {

	void readNextPacketVideo(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException;

	void readNextPacketAudio(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException;

}
