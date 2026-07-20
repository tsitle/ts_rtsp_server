package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

interface PsFindNextMagicBytesInterface {

	int findNextMagicBytes(final @NonNull BufferExt inputBuf);

}
