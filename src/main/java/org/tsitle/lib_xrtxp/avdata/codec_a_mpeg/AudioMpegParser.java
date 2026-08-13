package org.tsitle.lib_xrtxp.avdata.codec_a_mpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;

public final class AudioMpegParser {

	/** MPEG Audio Magic Bytes (11 bits long) */
	public static final byte[] MPEG_AUDIO_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xE0};
	public static final int MPEG_AUDIO_LENGTH_BITS_FRAME_START_MAGICBYTES = 11;

	public static final int MPEG_AUDIO_HEADER_SIZE_MIN = 4;
	@SuppressWarnings("unused")
	public static final int MPEG_AUDIO_HEADER_SIZE_MAX = MPEG_AUDIO_HEADER_SIZE_MIN + 2;

	/**
	 * Constructor.
	 */
	public AudioMpegParser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the MPEG Audio payload length from the given frame and calculates the remaining payload length to read.
	 * @param mpaFrame MPEG Audio frame
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If MPEG Audio data size is invalid
	 */
	public static int getRemainingMpaPayloadLengthToRead(@NonNull BufferExt mpaFrame) throws AvInvalidCodecDataException {
		AudioMpegParser mpaParser = new AudioMpegParser();
		AudioMpegInfo mpaInfo = mpaParser.parseMpaData(new BufferView(mpaFrame));

		return mpaInfo.samplesLength;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the MPEG Audio data and returns an MpaInfo object with the parsed information.
	 * @param inputBv MPEG Audio data
	 * @return Parsed MPEG Audio information
	 */
	public @NonNull AudioMpegInfo parseMpaData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMpaData()";

		if (inputBv.getLength() < MPEG_AUDIO_HEADER_SIZE_MIN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MPEG Audio data size");
		}

		/*
		 * See
		 *   http://www.mp3-tech.org/programmer/frame_header.html
		 */

		//
		AudioMpegInfo resObj = new AudioMpegInfo();

		resObj.samplesOffset = MPEG_AUDIO_HEADER_SIZE_MIN;

		// -------------------------------------------------

		BitReaderHelper brh = new BitReaderHelper(inputBv);

		boolean hdHaveCrc;
		boolean hdHavePadd;
		try {
			// verify syncword 0xFFE (11 bits)
			if (brh.readBits(8) != 0xFF || brh.readBits(3) != 0x07) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid syncword in MPEG Audio header");
			}
			byte tmpMpegVersion = (byte)brh.readBits(2);
			resObj.mpegAudioVersion = AudioMpegInfo.MpegAudioVersion.of(tmpMpegVersion);
			byte tmpMpegLayer = (byte)brh.readBits(2);
			if (tmpMpegLayer == 0x00) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MPEG Audio layer in header");
			}
			resObj.mpegLayer = AudioMpegInfo.MpegLayer.of(tmpMpegLayer);
			hdHaveCrc = (brh.readBits(1) == 0);  // protection bit (16bit CRC follows header)
			resObj.bitRateIx = (byte)brh.readBits(4);  // bitrate index
			if (resObj.bitRateIx == 0x00) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Cannot handle 'free' bitrate in MPEG Audio header");
			}
			if (resObj.bitRateIx == 0x0F) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid bitrate index in MPEG Audio header");
			}
			resObj.sampleRateIx = (byte)brh.readBits(2);
			if (resObj.sampleRateIx == 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid sample rate index in MPEG Audio header");
			}
			hdHavePadd = (brh.readBits(1) == 1);
			brh.readBit();  // skip private bit
			byte tmpChannMode = (byte)brh.readBits(2);
			resObj.channelMode = AudioMpegInfo.ChannelMode.of(tmpChannMode);
			brh.readBits(2);  // skip mode extension
			brh.readBit();  // skip copyright bit
			brh.readBit();  // skip 'original' bit
			brh.readBits(2);  // skip emphasis
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Could not read all bits from MPEG Audio header");
		}

		// -------------------------------------------------

		resObj.samplesOffset += (hdHaveCrc ? 2 : 0);
		resObj.frameLength = (int)(144.0 * (double)(resObj.getBitRateKbps() * 1000) / (double)resObj.getSampleRateHz()) +
				(hdHavePadd ? 1 : 0);
		resObj.samplesLength = resObj.frameLength - resObj.samplesOffset;
		if (inputBv.getLength() < resObj.frameLength) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MPEG Audio data size");
		}

		return resObj;
	}

}
