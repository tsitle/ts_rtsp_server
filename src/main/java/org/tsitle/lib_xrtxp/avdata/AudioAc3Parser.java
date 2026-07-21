package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;

public final class AudioAc3Parser {

	public static final int AC3_HEADER_SIZE_MIN = 7;

	/**
	 * Constructor.
	 */
	public AudioAc3Parser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the AC-3 payload length from the given AC-3 header and calculates the remaining payload length to read.
	 * @param ac3Header AC-3 header
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If AC-3 data size is invalid
	 */
	public static int getRemainingAc3PayloadLengthToRead(@NonNull BufferExt ac3Header) throws AvInvalidCodecDataException {
		if (ac3Header.getUsed() < AC3_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("Invalid AC-3 data size");
		}

		AudioAc3Parser ac3Parser = new AudioAc3Parser();
		AudioAc3Info ac3Info = ac3Parser.parseAc3Data(new BufferView(ac3Header));

		return ac3Info.frameLength - ac3Header.getUsed();
	}

	/**
	 * Parse only the AC-3 header
	 * @param ac3Header AC-3 header
	 * @return AC-3 info
	 */
	public static @NonNull AudioAc3Info parseAc3Header(@NonNull BufferExt ac3Header)
			throws AvInvalidCodecDataException {
		if (ac3Header.getUsed() < AC3_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("AC-3 header to short");
		}

		AudioAc3Parser ac3Parser = new AudioAc3Parser();
		return ac3Parser.parseAc3Data(new BufferView(ac3Header));
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the AC-3 data and returns an Ac3Info object with the parsed information.
	 * @param inputBv AC-3 data
	 * @return Parsed AC-3 information
	 */
	public @NonNull AudioAc3Info parseAc3Data(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseAc3Data()";

		if (inputBv.getLength() < AC3_HEADER_SIZE_MIN) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid AC-3 data size");
		}

		//
		AudioAc3Info resObj = new AudioAc3Info();

		resObj.samplesOffset = 0;
		resObj.samplesLength = inputBv.getLength();

		BitReaderHelper bitReader = new BitReaderHelper(inputBv);

		try {
			// Syncinfo: Verify syncword 0x0B77: bits 0-15 (16 bits)
			if (bitReader.readBits(8) != 0x0B || bitReader.readBits(8) != 0x77) {
				throw new AvInvalidCodecDataException("Invalid AC-3 syncword");
			}

			// Syncinfo: CRC1: bits 16-31 (16 bits)
			bitReader.readBits(16);

			// Syncinfo: FSCOD: bits 32-33 (2 bits)
			int fscod = bitReader.readBits(2);
			resObj.samplerate = AudioAc3Info.Samplerate.of(fscod);
			if (resObj.samplerate == AudioAc3Info.Samplerate.UNKNOWN) {
				throw new AvInvalidCodecDataException("Invalid AC-3 FSCOD");
			}

			// Syncinfo: FRMSIZECOD: bits 34-39 (6 bits)
			int frmsizecod = bitReader.readBits(6);
			resObj.bitrate = lookupBitrate(frmsizecod);
			if (resObj.bitrate == AudioAc3Info.Bitrate.UNKNOWN) {
				throw new AvInvalidCodecDataException("Invalid AC-3 FRMSIZECOD (bitrate)");
			}
			resObj.frameLength = lookupBytesPerSyncframe(frmsizecod, resObj.samplerate);
			if (resObj.frameLength < 1) {
				throw new AvInvalidCodecDataException("Invalid AC-3 FRMSIZECOD (frameLength)");
			}

			// BSI: bsid: bits 40-44 (5 bits)
			bitReader.readBits(5);

			// BSI: bsmod: bits 45-47 (3 bits)
			bitReader.readBits(3);

			// BSI: acmod: bits 48-50 (3 bits)
			int acmod = bitReader.readBits(3);
			resObj.audioCodingMode = AudioAc3Info.AudioCodingMode.of(acmod);
			if (resObj.audioCodingMode == AudioAc3Info.AudioCodingMode.UNKNOWN) {
				throw new AvInvalidCodecDataException("Invalid AC-3 ACMOD");
			}
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Could not read all bits from AC-3 header");
		}

		// --------------------------------------------------------------------

		resObj.samplesLength = resObj.frameLength - resObj.samplesOffset;

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static AudioAc3Info.@NonNull Bitrate lookupBitrate(int frmsizecod) {
		if (! AudioAc3Fscl.MAP_FSCL.containsKey(frmsizecod)) {
			return AudioAc3Info.Bitrate.UNKNOWN;
		}
		return AudioAc3Fscl.MAP_FSCL.get(frmsizecod).bitrate();
	}

	private static int lookupBytesPerSyncframe(int frmsizecod, AudioAc3Info.@NonNull Samplerate samplerate) {
		if (! AudioAc3Fscl.MAP_FSCL.containsKey(frmsizecod)) {
			return -1;
		}
		if (! AudioAc3Fscl.MAP_FSCL.get(frmsizecod).subEntriesPerSr().containsKey(samplerate)) {
			return -1;
		}
		return AudioAc3Fscl.MAP_FSCL.get(frmsizecod).subEntriesPerSr().get(samplerate);
	}

}
