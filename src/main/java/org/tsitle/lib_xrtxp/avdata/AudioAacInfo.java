package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AudioAacInfo implements CodecInfoInterface<AudioAacInfo>, Cloneable {

	public static class InternalInfo implements Cloneable {
		public boolean idBit;
		public int layer2Bits;
		public boolean crcBit;
		public boolean privateBit;
		public boolean originalCopyBit;
		public boolean homeBit;
		public boolean copyrightIdBit;
		public boolean copyrightIdStartBit;
		public int bufferFullness11Bits;
		public int numRawDataBlocks2Bits;
		public byte[] crc2Bytes = new byte[2];

		public InternalInfo() {
			reset();
		}
		public void reset() {
			idBit = false;
			layer2Bits = 0;
			crcBit = false;
			privateBit = false;
			originalCopyBit = false;
			homeBit = false;
			copyrightIdBit = false;
			copyrightIdStartBit = false;
			bufferFullness11Bits = 0;
			numRawDataBlocks2Bits = 0;
			crc2Bytes[0] = 0;
			crc2Bytes[1] = 0;
		}
		public void copyOf(@NonNull InternalInfo src) {
			reset();

			idBit = src.idBit;
			layer2Bits = src.layer2Bits;
			crcBit = src.crcBit;
			privateBit = src.privateBit;
			originalCopyBit = src.originalCopyBit;
			homeBit = src.homeBit;
			copyrightIdBit = src.copyrightIdBit;
			copyrightIdStartBit = src.copyrightIdStartBit;
			bufferFullness11Bits = src.bufferFullness11Bits;
			numRawDataBlocks2Bits = src.numRawDataBlocks2Bits;
			System.arraycopy(src.crc2Bytes, 0, crc2Bytes, 0, 2);
		}
		@Override
		public @NonNull InternalInfo clone() {
			try {
				InternalInfo clone = (InternalInfo)super.clone();
				clone.crc2Bytes = new byte[2];
				System.arraycopy(crc2Bytes, 0, clone.crc2Bytes, 0, 2);
				return clone;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(idBit ? 1 : 0);
			baos.write(layer2Bits);
			baos.write(crcBit ? 1 : 0);
			baos.write(privateBit ? 1 : 0);
			baos.write(originalCopyBit ? 1 : 0);
			baos.write(homeBit ? 1 : 0);
			baos.write(copyrightIdBit ? 1 : 0);
			baos.write(copyrightIdStartBit ? 1 : 0);
			baos.write(bufferFullness11Bits);
			baos.write(numRawDataBlocks2Bits);
			baos.write(crc2Bytes[0]);
			baos.write(crc2Bytes[1]);
			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	/** Sampling rates according to ISO/IEC 14496-3:2001(E) Table 1.10 */
	public enum Samplerate {
		SR96000(0),
		SR88200(1),
		SR64000(2),
		SR48000(3),
		SR44100(4),
		SR32000(5),
		SR24000(6),
		SR22050(7),
		SR16000(8),
		SR12000(9),
		SR11025(10),
		SR08000(11),
		SR07350(12),
		ESCAPE(15),
		UNKNOWN(255);

		public final int index;
		Samplerate(int index) {
			this.index = index;
		}
		public static @NonNull Samplerate of(int index) {
			for (Samplerate rate : Samplerate.values()) {
				if (rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
		public int getHz() {
			return switch (this) {
				case SR96000 -> 96000;
				case SR88200 -> 88200;
				case SR64000 -> 64000;
				case SR48000 -> 48000;
				case SR44100 -> 44100;
				case SR32000 -> 32000;
				case SR24000 -> 24000;
				case SR22050 -> 22050;
				case SR16000 -> 16000;
				case SR12000 -> 12000;
				case SR11025 -> 11025;
				case SR08000 -> 8000;
				case SR07350 -> 7350;
				default -> -1;
			};
		}
	}

	/** Audio object types according to ISO/IEC 14496-3:2001(E) Table 1.1 */
	public enum AudioObjectType {
		AAC_MAIN(1),
		AAC_LC(2),
		AAC_SSR(3),
		AAC_LTP(4),
		RESERVED5(5),
		AAC_SCALABLE(6),
		UNKNOWN(255);

		public final int index;
		AudioObjectType(int index) {
			this.index = index;
		}
		public static @NonNull AudioObjectType of(int index) {
			for (AudioObjectType rate : AudioObjectType.values()) {
				if (rate.index == index) {
					return rate;
				}
			}
			return UNKNOWN;
		}
	}

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Length of the complete AAC frame (including header) */
	public int frameLength;
	/** Samplerate of the audio data (4 bits) */
	public @NonNull Samplerate samplerate;
	/** MPEG-4 Audio Object Type (2 bits) */
	public @NonNull AudioObjectType audioObjectType;
	/** Channel configuration (3 bits) */
	public int channelConfiguration;
	/** AudioSpecificConfig for SDP 'fmtp config' as hex string */
	public @NonNull String sdpFmtpConfigHex;

	public @NonNull InternalInfo internalInfo = new InternalInfo();

	public AudioAacInfo() {
		reset();
	}

	/*
	 * MPEG-4 low delay (from ISO/IEC 14496-3:2001(E)):
	 *   The MPEG-4 low delay coding functionality provides the ability to extend the usage of generic low bitrate audio
	 *   coding to applications requiring a very low delay in the encoding / decoding chain (e.g. full-duplex real-time
	 *   communications). [...].
	 *   Specifically, it is derived from the proven architecture of MPEG-2/4 Advanced Audio Coding (AAC) and all
	 *   capabilities for coding of two or more sound channels are available within the low-delay coder.
	 *   It operates at up to 48 kHz sampling rate and uses a frame length of 512 or 480 samples, compared to the
	 *   1024 or 960 samples used in standard MPEG-2/4 AAC to enable coding of general audio signals with an
	 *   algorithmic delay not exceeding 20 ms.
	 */

	@Override
	public int getPayloadOffset() {
		// we skip the ADTS header for the RTP payload
		return samplesOffset;
	}

	@Override
	public int getPayloadLength() {
		// since we skipped the ADTS header for the RTP payload, the payload length is now the length of the audio samples
		return samplesLength;
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		frameLength = 0;
		samplerate = Samplerate.UNKNOWN;
		audioObjectType = AudioObjectType.UNKNOWN;
		channelConfiguration = 0;
		sdpFmtpConfigHex = "";

		internalInfo.reset();
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<AudioAacInfo> src) {
		reset();

		AudioAacInfo tmpSrc = (AudioAacInfo)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		frameLength = tmpSrc.frameLength;
		samplerate = tmpSrc.samplerate;
		audioObjectType = tmpSrc.audioObjectType;
		channelConfiguration = tmpSrc.channelConfiguration;
		//noinspection StringOperationCanBeSimplified
		sdpFmtpConfigHex = new String(tmpSrc.sdpFmtpConfigHex);

		internalInfo.copyOf(tmpSrc.internalInfo);
	}

	@Override
	public @NonNull AudioAacInfo clone() {
		try {
			AudioAacInfo clone = (AudioAacInfo)super.clone();
			//noinspection StringOperationCanBeSimplified
			clone.sdpFmtpConfigHex = new String(sdpFmtpConfigHex);
			clone.internalInfo = internalInfo.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", frameLength=" + Integer.toUnsignedString(frameLength) +
				", samplerate=" + samplerate +
				", aot=" + audioObjectType +
				", channelConfiguration=" + channelConfiguration +
				"]";
	}

	@Override
	public @NonNull String toString(boolean shortOutput) {
		return toString();
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(samplesOffset);
		baos.write(samplesLength);
		baos.write(frameLength);
		baos.write(samplerate.ordinal());
		baos.write(audioObjectType.ordinal());
		baos.write(channelConfiguration);
		try {
			baos.write(sdpFmtpConfigHex.getBytes(StandardCharsets.UTF_8));
			baos.write(internalInfo.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}
