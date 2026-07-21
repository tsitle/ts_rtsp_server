package org.tsitle.rtsp_server.threads.dataprovider.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.rtsp_server.avstreams.codec_v_h26x.FrameGrabberVideoH26xFromEsFile;

final class MagicBytesH26xHelper {

	private MagicBytesH26xHelper() { }

	static int findH26xMagicBytesLength(final @NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final int MB_3_LEN = FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3.length;
		final int MB_4_LEN = FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4.length;

		int resI = -1;
		int tmpOffs = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3,
				inputBv,
				0,
				MB_4_LEN  // check the first 4 bytes
			);
		if (tmpOffs == 0 || tmpOffs == 1) {
			resI = MB_3_LEN;
		}
		if (tmpOffs == 1) {
			tmpOffs = findH26xMagicBytesOffset(
					FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4,
					inputBv,
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

	/**
	 * Find the next H26x NAL Unit start magic bytes in the Buffer View.
	 * @param inputBv Input Buffer View
	 * @return Offset of the next H26x NAL Unit start within the Buffer View, or -1 if not found
	 */
	static int findH26xNextNalUnit(final @NonNull BufferView inputBv) {
		int resI = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_3,
				inputBv,
				3,
				inputBv.getLength()
			);
		if (resI < 0) {
			return -1;
		}
		int tmpOffset = findH26xMagicBytesOffset(
				FrameGrabberVideoH26xFromEsFile.H26X_FRAME_START_MAGICBYTES_4,
				inputBv,
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
				final @NonNull BufferView inputBv,
				int startOffset,
				int maxCheckLen
			) {
		for (
					int bufIx = startOffset;
					bufIx + magicBytes.length <= inputBv.getLength() && bufIx - startOffset < maxCheckLen;
					bufIx++
				) {
			boolean found = true;
			for (int i = 0; i < magicBytes.length; i++) {
				if (inputBv.getByte(bufIx + i) != magicBytes[i]) {
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
