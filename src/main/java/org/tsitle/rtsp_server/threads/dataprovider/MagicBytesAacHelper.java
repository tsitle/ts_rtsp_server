package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAacFromEsFile;

final class MagicBytesAacHelper {

	private MagicBytesAacHelper() { }

	/**
	 * Find the next AAC frame start magic bytes in the Buffer View.
	 * @param inputBv Input Buffer View
	 * @return Offset of the next AAC frame start within the Buffer View, or -1 if not found
	 */
	static int findNextFrame(final @NonNull BufferView inputBv) {
		final byte[] magicBytes = FrameGrabberAudioAacFromEsFile.AAC_FRAME_START_MAGICBYTES;  // 12 bits long
		final int startOffset = 2;

		for (int bufIx = startOffset; bufIx + magicBytes.length <= inputBv.getLength(); bufIx++) {
			boolean found = true;
			if (inputBv.getByte(bufIx) != magicBytes[0]) {
				found = false;
			} else if ((byte)(inputBv.getByte(bufIx + 1) & 0xF0) != (byte)(magicBytes[1] & 0xF0)) {
				found = false;
			}
			if (found) {
				return bufIx;
			}
		}
		return -1;
	}

}
