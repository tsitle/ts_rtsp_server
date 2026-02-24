package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.helpers.BitWriterHelper;

public final class AudioAacParser {

	public static final int AAC_HEADER_SIZE_MIN = 7;
	public static final int AAC_HEADER_SIZE_MAX = 9;
	/** Maximum sampling frequency in Hz for AAC Level 4 (48 kHz) */
	public static final int AAC_SAMPLERATE_MAX = 48000;
	/** Maximum channel configuration for AAC Level 4 (5.1 surround) */
	public static final int AAC_CHANNELS_MAX = 6;

	/**
	 * Constructor.
	 */
	public AudioAacParser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the AAC payload length from the given ADTS header and calculates the remaining payload length to read.
	 * @param adtsHeader ADTS header
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If AAC data size is invalid
	 */
	public static int getRemainingAacPayloadLengthToRead(@NonNull BufferExt adtsHeader) throws AvInvalidCodecDataException {
		if (adtsHeader.getUsed() < AAC_HEADER_SIZE_MAX) {
			throw new IllegalArgumentException("Invalid AAC data size");
		}

		AudioAacParser aacParser = new AudioAacParser();
		AudioAacInfo aacInfo = aacParser.parseAacData(adtsHeader);

		return aacInfo.frameLength - adtsHeader.getUsed();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse only the ADTS header
	 * @param adtsHeader ADTS header
	 * @return AAC info
	 */
	public static @NonNull AudioAacInfo parseAdtsHeader(@NonNull BufferExt adtsHeader)
			throws AvInvalidCodecDataException {
		if (adtsHeader.getUsed() < AAC_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("ADTS header to short");
		}

		AudioAacParser aacParser = new AudioAacParser();
		return aacParser.parseAacData(adtsHeader);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the AAC data and returns an AacInfo object with the parsed information.
	 * @param aacBuf AAC data
	 * @return Parsed AAC information
	 */
	public AudioAacInfo parseAacData(BufferExt aacBuf) throws AvInvalidCodecDataException {
		final String FNC_NAME = AudioAacParser.class.getSimpleName() + ".parseAacData()";

		if (aacBuf.getUsed() < AAC_HEADER_SIZE_MIN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid AAC data size");
		}

		//
		AudioAacInfo resObj = new AudioAacInfo();

		resObj.samplesOffset = AAC_HEADER_SIZE_MIN;
		resObj.samplesLength = aacBuf.getUsed() - AAC_HEADER_SIZE_MIN;

		// --------------------------------------------------------------------
		// Fixed Header - identical for every frame: 28 bits (bytes 0..3.5)
		/// Verify syncword 0xFFF: bits 0-11 (12 bits)
		if ((aacBuf.get(0) & 0xFF) != 0xFF || (aacBuf.get(1) & 0xF0) != 0xF0) {
			throw new IllegalArgumentException("Invalid ADTS syncword");
		}

		/// ID: bit 12 (1 bit)

		/// Layer: bits 13-14 (2 bits): Always 00

		/// Protection Absent: bit 15 (1 bit): 1 if no CRC, 0 if CRC exists
		boolean haveCrc = ((aacBuf.get(2) & 0x10) == 0);

		/// MPEG-4 Audio Object Type: bits 16-17 (2 bits)
		byte tmpAot = (byte)((aacBuf.get(2) & 0xC0) >> 6);
		resObj.audioObjectType = AudioAacInfo.AudioObjectType.of(tmpAot + 1);
		if (resObj.audioObjectType == AudioAacInfo.AudioObjectType.UNKNOWN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid audio object type");
		}

		/// sampling_frequency_index: bits 18-21 (4 bits)
		int tmpSamplingFrequIndex = ((aacBuf.get(2) & 0x3C) >> 2);
		resObj.samplerate = AudioAacInfo.SampleRate.of(tmpSamplingFrequIndex);
		if (resObj.samplerate == AudioAacInfo.SampleRate.UNKNOWN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid sample rate (index=" + tmpSamplingFrequIndex + ")");
		}
		if (resObj.samplerate.getHz() > AAC_SAMPLERATE_MAX) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Sample rate too high (max. " + AAC_SAMPLERATE_MAX + " Hz");
		}

		/// Private Bit: bit 22 (1 bit): Set by user

		/// channel_configuration: bits 23-25 (3 bits), 1 bit from byte 2 + 2 bits from byte 3
		resObj.channelConfiguration = (((aacBuf.get(2) & 0x01) << 2) | ((aacBuf.get(3) & 0xC0) >> 6));
		if (resObj.channelConfiguration == 0) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid channel configuration");
		}
		if (resObj.channelConfiguration > AAC_CHANNELS_MAX) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Too many channels (is=" + resObj.channelConfiguration +
					", max=" + AAC_CHANNELS_MAX + ")");
		}

		/// Original/Copy: bit 26 (1 bit)

		/// Home: bit 27 (1 bit)

		// --------------------------------------------------------------------
		// Variable Header - changes per frame: 28 bits (bytes 3.5..7)
		/// Copyright ID Bit: bit 28 (1 bit)

		/// Copyright ID Start: bit 29 (1 bit)

		/// Frame Length: bits 30-42 (13 bits): Length of the frame including header, in bytes
		resObj.frameLength = (((aacBuf.get(3) & 0x03) << 11) | (aacBuf.get(4) << 3) | ((aacBuf.get(5) & 0xE0) >> 5));

		/// Buffer Fullness: bits 43-53 (11 bits): 0x7FF for VBR (variable bit rate)

		/// Number of RAW Data Blocks: 54-55 (2 bits): Number of AAC frames minus 1

		// --------------------------------------------------------------------
		// CRC if 'Protection Absent' is set to 0: 2 bytes (bytes 7..8)
		if (haveCrc) {
			if (aacBuf.getUsed() < AAC_HEADER_SIZE_MAX) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid AAC data size (missing CRC)");
			}
			resObj.samplesOffset += 2;
			resObj.samplesLength -= 2;
		}

		// --------------------------------------------------------------------

		resObj.sdpFmtpConfigHex = buildAacAudioSpecificConfig(
				resObj.audioObjectType,
				resObj.samplerate,
				resObj.channelConfiguration
			);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Builds AudioSpecificConfig as a hex string for SDP.
	 * @param audioObjectType AudioObjectType
	 * @param samplingFrequency SampleRate
	 * @param channelConfig ChannelConfiguration
	 * @return AudioSpecificConfig as hex string
	 */
	private static @NonNull String buildAacAudioSpecificConfig(
				AudioAacInfo.AudioObjectType audioObjectType,
				AudioAacInfo.SampleRate samplingFrequency,
				int channelConfig
			) {
		BitWriterHelper bitWriter = new BitWriterHelper();

		bitWriter.writeBits(audioObjectType.index, 5);
		bitWriter.writeBits(samplingFrequency.index, 4);
		// if (samplingFrequencyIndex==0xf) then the next 24 bits would be the actual sampling frequency
		bitWriter.writeBits(channelConfig, 4);

		bitWriter.flush();

		return bytesToHexString(bitWriter.toByteArray());
	}

	private static @NonNull String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

}
