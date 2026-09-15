package org.tsitle.lib_xrtxp.avdata.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;
import org.tsitle.lib_xrtxp.common.helpers.BitWriterHelper;

import java.util.HexFormat;

public final class AudioAacParser {

	/** AAC Magic Bytes (12 bits long) */
	public static final byte[] AAC_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xF0};
	public static final int AAC_LENGTH_BITS_FRAME_START_MAGICBYTES = 12;

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
		AudioAacInfo aacInfo = aacParser.parseAacData(new BufferView(adtsHeader));

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
		return aacParser.parseAacData(new BufferView(adtsHeader));
	}

	@SuppressWarnings("unused")
	public static AudioAacInfo.@NonNull AacAudioSpecificConfigInfo parseAacAudioSpecificConfig(
				@NonNull ExtradataContainerHex decoderExtradata
			) throws AvInvalidCodecDataException {
		final String FNC_NAME = AudioAacParser.class.getSimpleName() + ".parseAacAudioSpecificConfig()";

		if (! decoderExtradata.isCodecAac()) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": decoderExtradata is not for AAC");
		}
		String tmpEdStr = decoderExtradata.getEd();
		if (tmpEdStr.length() < 2) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": decoderExtradata is missing");
		}
		byte[] tmpEdBa;
		try {
			tmpEdBa = HexFormat.of().parseHex(tmpEdStr);
		} catch (IllegalArgumentException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": decoderExtradata is not valid hex-string");
		}
		BufferExt tmpEdBe = new BufferExt(tmpEdBa);
		BitReaderHelper brh = new BitReaderHelper(tmpEdBe, 0);

		try {
			int tmpAudioObjectTypeIndex = brh.readBits(5);
			int tmpSamplingFrequencyIndex = brh.readBits(4);
			int tmpSamplingFrequencyCustom = -1;
			if (tmpSamplingFrequencyIndex == 0x0F) {
				tmpSamplingFrequencyCustom = brh.readBits(24);
			}
			int tmpChannelConfig = brh.readBits(4);

			//
			if (tmpSamplingFrequencyCustom >= 0) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": custom AAC samplerate is not supported");
			}

			AudioAacInfo.AacAudioSpecificConfigInfo resObj = new AudioAacInfo.AacAudioSpecificConfigInfo();
			resObj.audioObjectType = AudioAacInfo.AudioObjectType.of(tmpAudioObjectTypeIndex);
			resObj.samplerate = AudioAacInfo.Samplerate.of(tmpSamplingFrequencyIndex);
			resObj.channelConfiguration = tmpChannelConfig;
			return resObj;
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": failed to parse AAC ASC");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the AAC data and returns an AacInfo object with the parsed information.
	 * @param inputBv AAC data
	 * @return Parsed AAC information
	 */
	public @NonNull AudioAacInfo parseAacData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseAacData()";

		if (inputBv.getLength() < AAC_HEADER_SIZE_MIN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid AAC data size");
		}

		//
		AudioAacInfo resObj = new AudioAacInfo();

		resObj.samplesOffset = AAC_HEADER_SIZE_MIN;
		resObj.samplesLength = 0;

		/*
		 * For the AAC header format, see:
		 *   ISO/IEC 14496-3:2001(E), Section 1.A.2.2 Audio_Data_Transport_Stream frame, ADTS
		 */

		BitReaderHelper bitReader = new BitReaderHelper(inputBv);

		try {
			// --------------------------------------------------------------------
			// Fixed Header - identical for every frame: 28 bits (bytes 0..3.5)

			// Verify syncword 0xFFF: bits 0-11 (12 bits)
			if (bitReader.readBits(8) != 0xFF || bitReader.readBits(4) != 0x0F) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid ADTS syncword");
			}

			// ID: bit 12 (1 bit)
			resObj.internalInfo.idBit = (bitReader.readBits(1) == 1);

			// Layer: bits 13-14 (2 bits): Always 00
			resObj.internalInfo.layer2Bits = bitReader.readBits(2);

			// Protection Absent: bit 15 (1 bit): 1 if no CRC, 0 if CRC exists
			resObj.internalInfo.crcBit = (bitReader.readBits(1) == 0);

			// MPEG-4 Audio Object Type: bits 16-17 (2 bits)
			byte tmpAot = (byte)bitReader.readBits(2);
			resObj.audioObjectType = AudioAacInfo.AudioObjectType.of(tmpAot + 1);
			if (resObj.audioObjectType == AudioAacInfo.AudioObjectType.UNKNOWN) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid audio object type");
			}

			// sampling_frequency_index: bits 18-21 (4 bits)
			int tmpSamplingFrequIndex = bitReader.readBits(4);
			resObj.samplerate = AudioAacInfo.Samplerate.of(tmpSamplingFrequIndex);
			if (resObj.samplerate == AudioAacInfo.Samplerate.UNKNOWN) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid samplerate (index=" + tmpSamplingFrequIndex + ")");
			}
			if (resObj.samplerate.getHz() > AAC_SAMPLERATE_MAX) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Samplerate too high (is=" + resObj.samplerate.getHz() +
						", max=" + AAC_SAMPLERATE_MAX + " Hz)");
			}

			// Private Bit: bit 22 (1 bit): Set by user
			resObj.internalInfo.privateBit = (bitReader.readBits(1) == 1);

			// channel_configuration: bits 23-25 (3 bits), 1 bit from byte 2 + 2 bits from byte 3
			resObj.channelConfiguration = bitReader.readBits(3);
			if (resObj.channelConfiguration == 0) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid channel configuration");
			}
			if (resObj.channelConfiguration > AAC_CHANNELS_MAX) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Too many channels (is=" + resObj.channelConfiguration +
						", max=" + AAC_CHANNELS_MAX + ")");
			}

			// Original/Copy: bit 26 (1 bit)
			resObj.internalInfo.originalCopyBit = (bitReader.readBits(1) == 1);

			// Home: bit 27 (1 bit)
			resObj.internalInfo.homeBit = (bitReader.readBits(1) == 1);

			// --------------------------------------------------------------------
			// Variable Header - changes per frame: 28 bits (bytes 3.5..7)

			// Copyright ID Bit: bit 28 (1 bit)
			resObj.internalInfo.copyrightIdBit = (bitReader.readBits(1) == 1);

			// Copyright ID Start: bit 29 (1 bit)
			resObj.internalInfo.copyrightIdStartBit = (bitReader.readBits(1) == 1);

			// Frame Length: bits 30-42 (13 bits): Length of the frame including header, in bytes
			resObj.frameLength = bitReader.readBits(13);

			// Buffer Fullness: bits 43-53 (11 bits): 0x7FF for VBR (variable bit rate)
			resObj.internalInfo.bufferFullness11Bits = bitReader.readBits(11);

			// Number of RAW Data Blocks: 54-55 (2 bits): Number of AAC frames minus 1
			resObj.internalInfo.numRawDataBlocks2Bits = (byte)(bitReader.readBits(2) + 1);
			if (resObj.internalInfo.numRawDataBlocks2Bits > 1) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": More than one AAC frame");
			}

			// --------------------------------------------------------------------
			// CRC - changes per frame: 16 bits (bytes 8..9)
			if (resObj.internalInfo.crcBit) {
				resObj.internalInfo.crc2Bytes[0] = (byte)bitReader.readBits(8);
				resObj.internalInfo.crc2Bytes[1] = (byte)bitReader.readBits(8);
			}
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Could not read all bits from AAC header");
		}

		// --------------------------------------------------------------------
		// CRC if 'Protection Absent' is set to 0: 2 bytes (bytes 7..8)
		if (resObj.internalInfo.crcBit) {
			if (inputBv.getLength() < AAC_HEADER_SIZE_MAX) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid AAC data size (missing CRC)");
			}
			resObj.samplesOffset += 2;
		}

		// --------------------------------------------------------------------

		resObj.samplesLength = resObj.frameLength - resObj.samplesOffset;

		// --------------------------------------------------------------------

		buildAacAudioSpecificConfig(
				resObj.audioObjectType,
				resObj.samplerate,
				resObj.channelConfiguration,
				resObj.sdpFmtpConfigHex
			);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Builds AudioSpecificConfig as a hex string for SDP.
	 * @param audioObjectType AudioObjectType
	 * @param samplingFrequency Samplerate
	 * @param channelConfig ChannelConfiguration
	 * @param outAacAudioSpecificConfig Output for the AudioSpecificConfig
	 */
	private static void buildAacAudioSpecificConfig(
				AudioAacInfo.@NonNull AudioObjectType audioObjectType,
				AudioAacInfo.@NonNull Samplerate samplingFrequency,
				int channelConfig,
				@NonNull ExtradataContainerHex outAacAudioSpecificConfig
			) {
		BitWriterHelper bitWriter = new BitWriterHelper();

		bitWriter.writeBits(audioObjectType.index, 5);
		bitWriter.writeBits(samplingFrequency.index, 4);
		// if (samplingFrequencyIndex==0xf) then the next 24 bits would be the actual sampling frequency
		bitWriter.writeBits(channelConfig, 4);

		ExtradataContainerHex tmpEd = ExtradataContainerHex.ofAac(
				bytesToHexString(bitWriter.toByteArray())
			);
		outAacAudioSpecificConfig.copyFrom(tmpEd);
	}

	private static @NonNull String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

}
