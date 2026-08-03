package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

/**
 * Helper for removing ADTS headers from AAC packets.
 */
public final class FfmpegHelperBsfAacNoAdts implements FfmpegHelperBsfAacInterface {

	public FfmpegHelperBsfAacNoAdts() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static boolean hasAdtsHeader(@NonNull AVPacket pktAv) {
		if (pktAv.size() < 3) {
			return false;
		}
		return (pktAv.data().get(0) == (byte)0xFF && (pktAv.data().get(1) & (byte)0xF0) == (byte)0xF0);
	}

	@SuppressWarnings("unused")
	public static boolean hasAdtsHeader(@NonNull BufferExt pktBe) {
		if (pktBe.getUsed() < 3) {
			return false;
		}
		return (pktBe.get(0) == (byte)0xFF && (pktBe.get(1) & (byte)0xF0) == (byte)0xF0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Remove the ADTS header from the AAC Access Unit (AU).
	 * @param inputAu Input packet
	 * @param outputAu Output packet
	 */
	public void processPkt(@NonNull AVPacket inputAu, @NonNull BufferExt outputAu) {
		if (inputAu.size() < 3) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".processPkt(): " +
					"AAC AU is empty");
		}

		outputAu.clear();

		byte[] inBa = new byte[2];
		inputAu.data().get(inBa, 0, inBa.length);

		boolean hasCrc = ((inBa[1] & (byte)0x01) == 0);

		int startOffset = 0;
		int dataLength = inputAu.size();

		if (inBa[0] == (byte)0xFF && (inBa[1] & (byte)0xF0) == (byte)0xF0) {
			int delta = 7 + (hasCrc ? 2 : 0);
			startOffset += delta;
			dataLength -= delta;
		}

		outputAu.increaseSize(dataLength);
		inputAu.data().get(outputAu.getBaPtr(), startOffset, dataLength);
		outputAu.setUsed(dataLength);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Remove the ADTS header from the AAC Access Unit (AU).
	 * @param ioPkt In- and output buffer view of the packet
	 */
	@SuppressWarnings("unused")
	public static void removeAdts(@NonNull BufferView ioPkt) {
		if (ioPkt.getLength() < 3) {
			throw new IllegalArgumentException(FfmpegHelperBsfAacNoAdts.class.getSimpleName() + ".removeAdts(): " +
					"AAC AU is empty");
		}
		if (ioPkt.getByte(0) != (byte)0xFF || (ioPkt.getByte(1) & (byte)0xF0) != (byte)0xF0) {
			return;
		}
		boolean hasCrc = ((ioPkt.getByte(1) & (byte)0x01) == 0);
		int delta = 7 + (hasCrc ? 2 : 0);
		ioPkt.increaseOffset(delta);
		ioPkt.increaseLength(-delta);
	}

}
