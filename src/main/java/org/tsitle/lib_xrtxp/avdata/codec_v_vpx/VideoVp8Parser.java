package org.tsitle.lib_xrtxp.avdata.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class VideoVp8Parser {

	public static final byte[] VP8_CUSTOM_FRAME_START_MAGICBYTES = {
			(byte)'V', (byte)'P', (byte)'8', (byte)'_', (byte)'C', (byte)'U', (byte)'S', (byte)'T', (byte)'O', (byte)'M'
		};
	public static final int VP8_CUSTOM_HEADER_SIZE = VP8_CUSTOM_FRAME_START_MAGICBYTES.length + 4;

	public static final int VP8_HEADER_SIZE = 3;

	private boolean isFirstFrame = true;
	private boolean isCustomFileFmt = false;

	/**
	 * Constructor.
	 */
	public VideoVp8Parser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the VP8 payload length from the given VP8 frame and calculates the remaining payload length to read.
	 * @param vp8Frame VP8 frame
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If VP8 data size is invalid
	 */
	public static int getRemainingVp8PayloadLengthToRead(@NonNull BufferExt vp8Frame) throws AvInvalidCodecDataException {
		VideoVp8Parser vp8Parser = new VideoVp8Parser();
		VideoVp8Info vp8Info = vp8Parser.parseVp8Data(0L, new BufferView(vp8Frame));
		if (! vp8Parser.isCustomFileFmt) {
			throw new IllegalArgumentException("Invalid VP8 data - must be custom file format");
		}

		return vp8Info.payloadLength - VP8_HEADER_SIZE;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the VP8 data and returns an vp8Info object with the parsed information.
	 * @param debugStreamOffset Offset of the VP8 data in the VP8 stream (used for error messages)
	 * @param inputBv VP8 data
	 * @return Parsed VP8 information
	 */
	public @NonNull VideoVp8Info parseVp8Data(
				@SuppressWarnings("unused") long debugStreamOffset,
				@NonNull BufferView inputBv
			) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseVp8Data()";

		if (inputBv.getLength() < VP8_HEADER_SIZE) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid VP8 data size");
		}

		VideoVp8Info resObj = new VideoVp8Info();

		resObj.payloadOffs = 0;
		resObj.payloadLength = inputBv.getLength() - resObj.payloadOffs;

		// -------------------------------------------------

		if (inputBv.getLength() >= VP8_CUSTOM_HEADER_SIZE + VP8_HEADER_SIZE) {
			if (isFirstFrame) {
				isCustomFileFmt = true;
				for (int x = 0; x < VP8_CUSTOM_FRAME_START_MAGICBYTES.length; x++) {
					if (inputBv.getByte(x) != VP8_CUSTOM_FRAME_START_MAGICBYTES[x]) {
						isCustomFileFmt = false;
						break;
					}
				}
			}
			if (isCustomFileFmt) {
				int tmpOffs = VP8_CUSTOM_FRAME_START_MAGICBYTES.length;
				resObj.payloadLength = (inputBv.getByte(tmpOffs) & 0xFF) |
						((inputBv.getByte(tmpOffs + 1) << 8) & 0xFF00) |
						((inputBv.getByte(tmpOffs + 2) << 16) & 0xFF0000) |
						((inputBv.getByte(tmpOffs + 3) << 24) & 0xFF000000);
				resObj.payloadOffs += tmpOffs + 4;
			}
		}
		isFirstFrame = false;

		// -------------------------------------------------

		int tmpOffs = resObj.payloadOffs;
		resObj.isKeyFrame = ((inputBv.getByte(tmpOffs) & 0x01) == 0);
		/* the remaining info is not of interest:
		byte hdVersionNb = (byte)((inputBv.getByte(tmpOffs) >>> 1) & 0x07);
		boolean hdShowFrame = (((inputBv.getByte(tmpOffs) >>> 4) & 0x01) == 1);
		int hdFirstPartitionSz = (((inputBv.getByte(tmpOffs) >>> 5) & 0x07) |  // 0000 0111
				((inputBv.getByte(tmpOffs + 1) << 3) & 0x7_F8) |  // 0111 1111 1000
				((inputBv.getByte(tmpOffs + 2) << 11) & 0x7_F8_00));  // 0111 1111 1000 0000 0000
		*/

		return resObj;
	}

}
