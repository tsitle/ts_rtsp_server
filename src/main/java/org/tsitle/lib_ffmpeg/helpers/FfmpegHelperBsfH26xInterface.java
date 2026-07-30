package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;

public interface FfmpegHelperBsfH26xInterface extends AutoCloseable {

	/**
	 * Set the input packet.
	 */
	void setInputPacket(@NonNull AVPacket inputPkt) throws FfmpegGenericException;

	/**
	 * Receive 0..N converted packets - one per call.
	 */
	boolean receiveOneConvertedPacket(@NonNull AVPacket outputPkt) throws FfmpegGenericException;

	@Override
	void close();

}
