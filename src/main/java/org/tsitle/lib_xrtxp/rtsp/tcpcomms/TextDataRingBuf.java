package org.tsitle.lib_xrtxp.rtsp.tcpcomms;

import org.tsitle.lib_xrtxp.common.types.RingBufferBase;

final class TextDataRingBuf extends RingBufferBase<TextRbEntry> {

	TextDataRingBuf(int initialCacheSize, int maxCacheSize) {
		super(initialCacheSize, maxCacheSize, (_) -> new TextRbEntry(), TextRbEntry::clone);
	}

}
