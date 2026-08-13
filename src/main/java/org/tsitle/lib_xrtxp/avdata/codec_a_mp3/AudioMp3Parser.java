package org.tsitle.lib_xrtxp.avdata.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;

public final class AudioMp3Parser {

	/** MP3 Magic Bytes (11 bits long) */
	public static final byte[] MP3_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xE0};
	public static final int MP3_LENGTH_BITS_FRAME_START_MAGICBYTES = 11;

	public static final int MP3_HEADER_SIZE = 4;

	/**
	 * Constructor.
	 */
	public AudioMp3Parser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the MP3 payload length from the given MP3 frame and calculates the remaining payload length to read.
	 * @param mp3Frame MP3 frame
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If MP3 data size is invalid
	 */
	public static int getRemainingMp3PayloadLengthToRead(@NonNull BufferExt mp3Frame) throws AvInvalidCodecDataException {
		AudioMp3Parser mp3Parser = new AudioMp3Parser();
		AudioMp3Info mp3Info = mp3Parser.parseMp3Data(new BufferView(mp3Frame));

		return mp3Info.samplesLength;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the MP3 data and returns an Mp3Info object with the parsed information.
	 * @param inputBv MP3 data
	 * @return Parsed MP3 information
	 */
	public @NonNull AudioMp3Info parseMp3Data(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMp3Data()";

		if (inputBv.getLength() < MP3_HEADER_SIZE) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MP3 data size");
		}

		/*
		 * See
		 *   http://www.mp3-tech.org/programmer/frame_header.html
		 */

		//
		AudioMp3Info resObj = new AudioMp3Info();

		resObj.samplesOffset = MP3_HEADER_SIZE;

		// -------------------------------------------------

		BitReaderHelper brh = new BitReaderHelper(inputBv);

		boolean hdHaveCrc;
		boolean hdHavePadd;
		try {
			brh.readBits(11);  // syncword
			byte tmpMpegVersion = (byte)brh.readBits(2);
			resObj.mpegAudioVersion = AudioMp3Info.MpegAudioVersion.of(tmpMpegVersion);
			byte tmpMpegLayer = (byte)brh.readBits(2);
			if (tmpMpegLayer == 0x00) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MPEG layer in MP3 header");
			}
			resObj.mpegLayer = AudioMp3Info.MpegLayer.of(tmpMpegLayer);
			hdHaveCrc = (brh.readBits(1) == 0);  // protection bit (16bit CRC follows header)
			resObj.bitRateIx = (byte)brh.readBits(4);  // bitrate index
			if (resObj.bitRateIx == 0x00) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Cannot handle 'free' bitrate in MP3 header");
			}
			if (resObj.bitRateIx == 0x0F) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid bitrate index in MP3 header");
			}
			resObj.sampleRateIx = (byte)brh.readBits(2);
			if (resObj.sampleRateIx == 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid sample rate index in MP3 header");
			}
			hdHavePadd = (brh.readBits(1) == 1);
			brh.readBit();  // skip private bit
			byte tmpChannMode = (byte)brh.readBits(2);
			resObj.channelMode = AudioMp3Info.ChannelMode.of(tmpChannMode);
			brh.readBits(2);  // skip mode extension
			brh.readBit();  // skip copyright bit
			brh.readBit();  // skip 'original' bit
			brh.readBits(2);  // skip emphasis
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Could not read all bits from MP3 header");
		}

		// -------------------------------------------------

		resObj.samplesOffset += (hdHaveCrc ? 2 : 0);
		resObj.frameLength = (int)(144.0 * (double)(resObj.getBitRateKbps() * 1000) / (double)resObj.getSampleRateHz()) +
				(hdHavePadd ? 1 : 0);
		resObj.samplesLength = resObj.frameLength - resObj.samplesOffset;
		if (inputBv.getLength() < resObj.frameLength) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid MP3 data size");
		}

		return resObj;
	}

}
