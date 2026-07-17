package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromFile;

final class MagicBytesH26xHelper {

	private MagicBytesH26xHelper() { }

	static boolean isMagicBytesLong(@NonNull BufferExt inputBuf) {
		return (inputBuf.getUsed() >= FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length &&
				inputBuf.get(0) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[0] &&
				inputBuf.get(1) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[1] &&
				inputBuf.get(2) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[2] &&
				inputBuf.get(3) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[3]);
	}

}
