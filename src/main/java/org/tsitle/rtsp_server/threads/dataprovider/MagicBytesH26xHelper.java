package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromEsFile;

final class MagicBytesH26xHelper {

	private MagicBytesH26xHelper() { }

	static int findH26xMagicBytesLength(final @NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		final int MB_3_LEN = FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3.length;
		final int MB_4_LEN = FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4.length;

		int resI = -1;
		int tmpOffs = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3,
				inputBuf,
				0,
				MB_4_LEN  // check the first 4 bytes
			);
		if (tmpOffs == 0 || tmpOffs == 1) {
			resI = MB_3_LEN;
		}
		if (tmpOffs == 1) {
			tmpOffs = findH26xMagicBytesOffset(
					FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4,
					inputBuf,
					0,
					MB_4_LEN
				);
			if (tmpOffs == 0) {
				resI = MB_4_LEN;
			} else {
				throw new AvInvalidCodecDataException("could not determine Magic Bytes (match 3 @ offset 1)");
			}
		}
		if (resI < 1) {
			throw new AvInvalidCodecDataException("could not determine Magic Bytes (no match)");
		}
		return resI;
	}

	static int findH26xNextNalUnit(final @NonNull BufferExt inputBuf) {
		int resI = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3,
				inputBuf,
				3,
				inputBuf.getUsed()
			);
		if (resI < 0) {
			return -1;
		}
		int tmpOffset = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4,
				inputBuf,
				resI - 1,
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4.length
			);
		if (tmpOffset == resI - 1) {
			resI = tmpOffset;
		}
		return resI;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static int findH26xMagicBytesOffset(
				final byte[] magicBytes,
				final @NonNull BufferExt inputBuf,
				int startOffset,
				int maxCheckLen
			) {
		for (int bufIx = startOffset; bufIx + magicBytes.length <= inputBuf.getUsed() && bufIx + magicBytes.length <= startOffset + maxCheckLen; bufIx++) {
			boolean found = true;
			for (int i = 0; i < magicBytes.length; i++) {
				if (inputBuf.get(bufIx + i) != magicBytes[i]) {
					found = false;
					break;
				}
			}
			if (found) {
				return bufIx;
			}
		}
		return -1;
	}

}
