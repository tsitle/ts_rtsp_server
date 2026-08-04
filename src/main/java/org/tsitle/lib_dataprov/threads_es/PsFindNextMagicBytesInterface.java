package org.tsitle.lib_dataprov.threads_es;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

interface PsFindNextMagicBytesInterface {

	/**
	 * Find the next frame start in the Buffer View.
	 * @param inputBv Input Buffer View
	 * @return Offset of the next frame start within the Buffer View, or -1 if not found
	 */
	int findNextMagicBytes(final @NonNull BufferView inputBv);

}
