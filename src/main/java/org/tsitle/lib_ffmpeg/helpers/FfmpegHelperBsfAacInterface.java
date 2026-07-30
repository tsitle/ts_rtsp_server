package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

public interface FfmpegHelperBsfAacInterface {

	/**
	 * Process the AAC Access Unit (AU).
	 * @param inputAu Input packet
	 * @param outputAu Output packet
	 */
	void processPkt(@NonNull AVPacket inputAu, @NonNull BufferExt outputAu);

}
