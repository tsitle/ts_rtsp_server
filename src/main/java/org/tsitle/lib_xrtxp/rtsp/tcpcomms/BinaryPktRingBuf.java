package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.types.RingBufferBase;

final class BinaryPktRingBuf extends RingBufferBase<BufferExt> {

	BinaryPktRingBuf(int initialCacheSize, int maxCacheSize) {
		super(initialCacheSize, maxCacheSize, (_) -> new BufferExt(), BufferExt::clone);
	}

}
