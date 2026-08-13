package org.tsitle.lib_dataprov.avstreams.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsRawFileBase;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;

public final class FrameGrabberAudioMp3FromEsRawFile extends FrameGrabberAvFromEsRawFileBase {

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioMp3FromEsRawFile(
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
			) {
		super(
				null,
				avStreamIncoming,
				AudioMp3Parser.MP3_FRAME_START_MAGICBYTES,
				AudioMp3Parser.MP3_LENGTH_BITS_FRAME_START_MAGICBYTES + 1  // +1 to get mod 4 == 0
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioMp3FromEsRawFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				AudioMp3Parser.MP3_FRAME_START_MAGICBYTES,
				AudioMp3Parser.MP3_LENGTH_BITS_FRAME_START_MAGICBYTES + 1  // +1 to get mod 4 == 0
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next audio frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 * @param stTimestamp Output for sample-time timestamp
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		stTimestamp.clear();

		internalGetNextFrameWithStartCode(
				FNC_NAME,
				frameBuf,
				false,
				null,
				null,
				AudioMp3Parser.MP3_HEADER_SIZE
			);
		//
		int remainingPayloadLength = AudioMp3Parser.getRemainingMp3PayloadLengthToRead(frameBuf);
		//
		internalReadRemainingFrameForFrameWithStartCode(frameBuf, remainingPayloadLength);
	}

}
