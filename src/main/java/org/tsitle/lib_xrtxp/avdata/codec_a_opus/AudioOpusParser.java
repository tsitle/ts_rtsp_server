package org.tsitle.lib_xrtxp.avdata.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class AudioOpusParser {

	/** Opus Magic Bytes (12 bits long) */
	public static final byte[] OPUS_CUSTOM_FRAME_START_MAGICBYTES = {
			(byte)'O', (byte)'P', (byte)'U', (byte)'S', (byte)'C', (byte)'U', (byte)'S', (byte)'T', (byte)'O', (byte)'M'
		};
	public static final int OPUS_CUSTOM_HEADER_SIZE = OPUS_CUSTOM_FRAME_START_MAGICBYTES.length + 4;

	public static final int OPUS_HEADER_SIZE = 1;
	/** Maximum sampling frequency in Hz for Opus (48 kHz) */
	@SuppressWarnings("unused")
	public static final int OPUS_SAMPLERATE_MAX = 48000;

	private static final int OPUS_MAX_SAMPLES_FOR_MAX_DURATION_120MS = 5760;

	private boolean isFirstFrame = true;
	private boolean isCustomFileFmt = false;

	/**
	 * Constructor.
	 */
	public AudioOpusParser() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the Opus payload length from the given Opus frame and calculates the remaining payload length to read.
	 * @param opusFrameHeader Opus frame
	 * @return Remaining number of bytes to read
	 * @throws IllegalArgumentException If Opus data size is invalid
	 */
	public static int getRemainingOpusPayloadLengthToRead(@NonNull BufferExt opusFrameHeader) throws AvInvalidCodecDataException {
		AudioOpusParser opusParser = new AudioOpusParser();
		AudioOpusInfo opusInfo = opusParser.parseOpusData(new BufferView(opusFrameHeader));
		if (! opusParser.isCustomFileFmt) {
			throw new IllegalArgumentException("Invalid Opus data - must be custom file format");
		}

		return opusInfo.frameLength - OPUS_HEADER_SIZE;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the Opus data and returns an OpusInfo object with the parsed information.
	 * @param inputBv Opus data
	 * @return Parsed Opus information
	 */
	public @NonNull AudioOpusInfo parseOpusData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseOpusData()";

		if (inputBv.getLength() < OPUS_HEADER_SIZE) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid Opus data size");
		}

		//
		AudioOpusInfo resObj = new AudioOpusInfo();

		resObj.samplesOffset = 0;  // we need to include the TOC byte in the RTP packet
		resObj.frameLength = inputBv.getLength();

		// -------------------------------------------------

		if (inputBv.getLength() >= OPUS_CUSTOM_HEADER_SIZE + OPUS_HEADER_SIZE) {
			if (isFirstFrame) {
				isCustomFileFmt = true;
				for (int x = 0; x < OPUS_CUSTOM_FRAME_START_MAGICBYTES.length; x++) {
					if (inputBv.getByte(x) != OPUS_CUSTOM_FRAME_START_MAGICBYTES[x]) {
						isCustomFileFmt = false;
						break;
					}
				}
			}
			if (isCustomFileFmt) {
				int tmpOffs = OPUS_CUSTOM_FRAME_START_MAGICBYTES.length;
				resObj.frameLength = (inputBv.getByte(tmpOffs) & 0xFF) |
						((inputBv.getByte(tmpOffs + 1) << 8) & 0xFF00) |
						((inputBv.getByte(tmpOffs + 2) << 16) & 0xFF0000) |
						((inputBv.getByte(tmpOffs + 3) << 24) & 0xFF000000);
				resObj.samplesOffset = OPUS_CUSTOM_HEADER_SIZE;
			}
		}
		isFirstFrame = false;

		// -------------------------------------------------

		int tmpOffs = resObj.samplesOffset;

		int samplesPerFrame = samplesPerFrame(inputBv.getByte(tmpOffs));
		int frameCount = frameCount(inputBv, tmpOffs);

		resObj.samplesPerChannelInAudioData = samplesPerFrame * frameCount;
		if (resObj.samplesPerChannelInAudioData > OPUS_MAX_SAMPLES_FOR_MAX_DURATION_120MS) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Packet exceeds maximum duration of 120 ms " +
					"(is=" + resObj.samplesPerChannelInAudioData + ", " +
					"max=" + OPUS_MAX_SAMPLES_FOR_MAX_DURATION_120MS + " samples)");
		}

		// --------------------------------------------------------------------

		resObj.samplesLength = resObj.frameLength - resObj.samplesOffset + (isCustomFileFmt ? OPUS_CUSTOM_HEADER_SIZE : 0);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Returns the number of Opus frames in the packet.
	 */
	private static int frameCount(@NonNull BufferView inputBv, int offs) throws AvInvalidCodecDataException {
		final String FNC_NAME = AudioOpusParser.class.getSimpleName() + ".frameCount()";

		int code = (inputBv.getByte(offs) & 0x03);
		return switch (code) {
				case 0 -> 1;
				case 1, 2 -> 2;
				case 3 -> {
					if (offs + 1 >= inputBv.getLength()) {
						throw new AvInvalidCodecDataException(FNC_NAME + ": Malformed Opus packet - invalid length");
					}
					yield (inputBv.getByte(offs + 1) & 0x3F);
				}
				default -> throw new AvInvalidCodecDataException(FNC_NAME + ": Malformed Opus packet");  // only for the linter
			};
	}

	/**
	 * Returns the number of 48 kHz samples in ONE frame.
	 */
	private static int samplesPerFrame(int toc) throws AvInvalidCodecDataException {
		final String FNC_NAME = AudioOpusParser.class.getSimpleName() + ".samplesPerFrame()";

		int config = ((toc >> 3) & 0x1F);
		if (config < 12) {
			// SILK
			return switch (config & 0x03) {
					case 0 -> 480;   // 10 ms
					case 1 -> 960;   // 20 ms
					case 2 -> 1920;  // 40 ms
					case 3 -> 2880;  // 60 ms
					default -> throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid Opus config byte");  // only for the linter
				};
		}
		if (config < 16) {
			// Hybrid
			return (config & 0x01) == 0 ? 480 : 960;
		}
		// CELT
		return switch (config & 0x03) {
				case 0 -> 120;   // 2.5 ms
				case 1 -> 240;   // 5 ms
				case 2 -> 480;   // 10 ms
				case 3 -> 960;   // 20 ms
				default -> throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid Opus config byte");  // only for the linter
			};
	}

}
